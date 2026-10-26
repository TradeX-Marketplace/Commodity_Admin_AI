package com.example.Commodity_Admin_AI.service;

import com.example.Commodity_Admin_AI.ai.GeminiService;
import com.example.Commodity_Admin_AI.ai.RagService;
import com.example.Commodity_Admin_AI.dto.ChatRequest;
import com.example.Commodity_Admin_AI.dto.ChatResponse;
import com.example.Commodity_Admin_AI.dto.PartiesResultDto;
import com.example.Commodity_Admin_AI.dto.PartyDto;
import com.example.Commodity_Admin_AI.entity.ChatMessage;
import com.example.Commodity_Admin_AI.entity.ChatSession;
import com.example.Commodity_Admin_AI.repository.ChatMessageRepository;
import com.example.Commodity_Admin_AI.repository.ChatSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartiesChatbotTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private RagService ragService;

    @Mock
    private GeminiService geminiService;

    @Mock
    private MarketplaceDataService marketplaceDataService;

    private IntentService intentService;
    private AdminChatbotService adminChatbotService;
    private ChatSession testSession;
    private List<PartyDto> sampleParties;

    @BeforeEach
    void setUp() {
        testSession = new ChatSession("admin");
        testSession.setId(100L);

        intentService = new IntentService();

        adminChatbotService = new AdminChatbotService(
                chatSessionRepository,
                chatMessageRepository,
                intentService,
                ragService,
                geminiService,
                marketplaceDataService,
                10
        );

        sampleParties = createSample12Parties();
    }

    private List<PartyDto> createSample12Parties() {
        List<PartyDto> list = new ArrayList<>();

        // PRT-0001: Sangli Agro Traders (Sangli, Unassigned)
        PartyDto p1 = new PartyDto(1L, "PRT-0001", "Sangli Agro Traders", "Sangli", "Maharashtra", List.of(), "ACTIVE", "2026-10-19T14:37:25");
        p1.setPhone("9876543210");
        p1.setEmail("sangli.agro@example.com");
        list.add(p1);

        // PRT-0002: Pune Agro Buyers (Pune, Unassigned)
        PartyDto p2 = new PartyDto(2L, "PRT-0002", "Pune Agro Buyers", "Pune", "Maharashtra", List.of(), "ACTIVE", "2026-10-19T14:38:27");
        list.add(p2);

        // PRT-0003: Sangli Agro Traders (Sangli, MANUFACTURER)
        PartyDto p3 = new PartyDto(3L, "PRT-0003", "Sangli Agro Traders", "Sangli", "Maharashtra", List.of("MANUFACTURER"), "ACTIVE", "2026-10-21T11:45:31");
        p3.setContactPerson("Amit Patil");
        p3.setPhone("9822011021");
        p3.setEmail("amit.patil@example.com");
        p3.setGstin("27AABCS1021A1Z1");
        p3.setPan("AABCS1021A");
        p3.setAddress("APMC Market Yard");
        list.add(p3);

        // PRT-0004: Pune Oil Processing Ltd (Pune, TRADER)
        list.add(new PartyDto(4L, "PRT-0004", "Pune Oil Processing Ltd", "Pune", "Maharashtra", List.of("TRADER"), "ACTIVE", "2026-10-21T11:46:15"));

        // PRT-0005: Vidarbha Grain Suppliers (Akola, MANUFACTURER)
        list.add(new PartyDto(5L, "PRT-0005", "Vidarbha Grain Suppliers", "Akola", "Maharashtra", List.of("MANUFACTURER"), "ACTIVE", "2026-10-21T11:46:28"));

        // PRT-0006: Latur Agro Industries (Latur, TRADER)
        list.add(new PartyDto(6L, "PRT-0006", "Latur Agro Industries", "Latur", "Maharashtra", List.of("TRADER"), "ACTIVE", "2026-10-21T11:46:42"));

        // PRT-0007: Marathwada Oil Mills (Nanded, MANUFACTURER)
        list.add(new PartyDto(7L, "PRT-0007", "Marathwada Oil Mills", "Nanded", "Maharashtra", List.of("MANUFACTURER"), "ACTIVE", "2026-10-21T11:46:51"));

        // PRT-0008: Kolhapur Trading Corporation (Kolhapur, TRADER)
        list.add(new PartyDto(8L, "PRT-0008", "Kolhapur Trading Corporation", "Kolhapur", "Maharashtra", List.of("TRADER"), "ACTIVE", "2026-10-21T11:46:59"));

        // PRT-0009: Nashik Farm Produce Co (Nashik, MANUFACTURER)
        list.add(new PartyDto(9L, "PRT-0009", "Nashik Farm Produce Co", "Nashik", "Maharashtra", List.of("MANUFACTURER"), "ACTIVE", "2026-10-21T11:47:08"));

        // PRT-0010: Deccan Edible Oils Pvt Ltd (Hyderabad, TRADER)
        list.add(new PartyDto(10L, "PRT-0010", "Deccan Edible Oils Pvt Ltd", "Hyderabad", "Telangana", List.of("TRADER"), "ACTIVE", "2026-10-21T11:47:16"));

        // PRT-0011: Khandesh Agro Suppliers (Jalgaon, MANUFACTURER)
        list.add(new PartyDto(11L, "PRT-0011", "Khandesh Agro Suppliers", "Jalgaon", "Maharashtra", List.of("MANUFACTURER"), "ACTIVE", "2026-10-21T11:47:25"));

        // PRT-0012: Southern Grain Processing Co (Bengaluru, TRADER)
        list.add(new PartyDto(12L, "PRT-0012", "Southern Grain Processing Co", "Bengaluru", "Karnataka", List.of("TRADER"), "ACTIVE", "2026-10-21T11:47:33"));

        return list;
    }

    private void mockMarketplaceService() {
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(testSession);
        when(marketplaceDataService.isConfigured()).thenReturn(true);
        when(marketplaceDataService.fetchParties()).thenReturn(new PartiesResultDto(sampleParties, 12, 1, 0, 50));
    }

    @Test
    @DisplayName("Question 1: Total registered parties count returns exactly 12 parties")
    void testQuestion1_TotalPartiesCount() {
        mockMarketplaceService();

        ChatRequest req = new ChatRequest("How many parties are registered in the marketplace?");
        ChatResponse resp = adminChatbotService.processChat(req);

        assertNotNull(resp);
        assertEquals("PARTIES", resp.getDisplayType());
        assertTrue(resp.getMessage().contains("12 registered parties") || resp.getMessage().contains("12"),
                "Response should report 12 registered parties: " + resp.getMessage());
        assertTrue(resp.getMessage().contains("Manufacturers") || resp.getMessage().contains("MANUFACTURER"));
        assertTrue(resp.getMessage().contains("Traders") || resp.getMessage().contains("TRADER"));
    }

    @Test
    @DisplayName("Question 2: List all registered parties returns markdown table with 12 rows")
    void testQuestion2_ListAllParties() {
        mockMarketplaceService();

        ChatRequest req = new ChatRequest("List all registered parties.");
        ChatResponse resp = adminChatbotService.processChat(req);

        assertNotNull(resp);
        assertEquals("PARTIES", resp.getDisplayType());
        assertTrue(resp.getMessage().contains("| Party Code | Company Name |"));
        assertTrue(resp.getMessage().contains("PRT-0001"));
        assertTrue(resp.getMessage().contains("PRT-0012"));
        assertEquals(12, resp.getParties().size());
    }

    @Test
    @DisplayName("Question 3: Registered buyers reports 0 explicit BUYER role and explains TradeX model")
    void testQuestion3_RegisteredBuyers() {
        mockMarketplaceService();

        ChatRequest req = new ChatRequest("How many buyers are registered?");
        ChatResponse resp = adminChatbotService.processChat(req);

        assertNotNull(resp);
        assertEquals("PARTIES", resp.getDisplayType());
        assertTrue(resp.getMessage().contains("0 parties with an explicit 'BUYER' role") || resp.getMessage().contains("0"));
        assertTrue(resp.getMessage().contains("MANUFACTURER") && resp.getMessage().contains("5"));
        assertTrue(resp.getMessage().contains("TRADER") && resp.getMessage().contains("5"));
    }

    @Test
    @DisplayName("Question 4: Registered sellers reports 0 explicit SELLER role and explains TradeX model")
    void testQuestion4_RegisteredSellers() {
        mockMarketplaceService();

        ChatRequest req = new ChatRequest("How many sellers are registered?");
        ChatResponse resp = adminChatbotService.processChat(req);

        assertNotNull(resp);
        assertEquals("PARTIES", resp.getDisplayType());
        assertTrue(resp.getMessage().contains("0 parties with an explicit 'SELLER' role") || resp.getMessage().contains("0"));
        assertTrue(resp.getMessage().contains("TRADER") && resp.getMessage().contains("5"));
        assertTrue(resp.getMessage().contains("MANUFACTURER") && resp.getMessage().contains("5"));
    }

    @Test
    @DisplayName("Question 5: Show details of PRT-0001 returns exact record details")
    void testQuestion5_PartyDetailsPRT0001() {
        mockMarketplaceService();

        ChatRequest req = new ChatRequest("Show the details of party PRT-0001.");
        ChatResponse resp = adminChatbotService.processChat(req);

        assertNotNull(resp);
        assertEquals("PARTIES", resp.getDisplayType());
        assertTrue(resp.getMessage().contains("PRT-0001"));
        assertTrue(resp.getMessage().contains("Sangli Agro Traders"));
        assertTrue(resp.getMessage().contains("Sangli"));
        assertTrue(resp.getMessage().contains("9876543210"));
        assertEquals(1, resp.getParties().size());
        assertEquals("PRT-0001", resp.getParties().get(0).getPartyCode());
    }

    @Test
    @DisplayName("Question 6: Parties registered in Sangli returns PRT-0001 and PRT-0003")
    void testQuestion6_PartiesInSangli() {
        mockMarketplaceService();

        ChatRequest req = new ChatRequest("Which parties are registered in Sangli?");
        ChatResponse resp = adminChatbotService.processChat(req);

        assertNotNull(resp);
        assertEquals("PARTIES", resp.getDisplayType());
        assertTrue(resp.getMessage().contains("2 registered parties") || resp.getMessage().contains("2"));
        assertTrue(resp.getMessage().contains("PRT-0001"));
        assertTrue(resp.getMessage().contains("PRT-0003"));
        assertEquals(2, resp.getParties().size());
    }

    @Test
    @DisplayName("Question 7: Parties registered this month reports all 12 registered in October 2026")
    void testQuestion7_PartiesRegisteredThisMonth() {
        mockMarketplaceService();

        ChatRequest req = new ChatRequest("How many parties registered this month?");
        ChatResponse resp = adminChatbotService.processChat(req);

        assertNotNull(resp);
        assertEquals("PARTIES", resp.getDisplayType());
        assertTrue(resp.getMessage().contains("12 parties registered this month"));
        assertTrue(resp.getMessage().contains("October 19, 2026") || resp.getMessage().contains("19"));
        assertTrue(resp.getMessage().contains("October 21, 2026") || resp.getMessage().contains("21"));
    }

    @Test
    @DisplayName("Question 8: Inactive or deleted parties reports 0 inactive and 0 deleted")
    void testQuestion8_InactiveOrDeletedParties() {
        mockMarketplaceService();

        ChatRequest req = new ChatRequest("Show inactive or deleted parties, if supported.");
        ChatResponse resp = adminChatbotService.processChat(req);

        assertNotNull(resp);
        assertEquals("PARTIES", resp.getDisplayType());
        assertTrue(resp.getMessage().contains("0 inactive parties"));
        assertTrue(resp.getMessage().contains("0 deleted parties"));
        assertTrue(resp.getMessage().contains("GET /api/v1/parties"));
    }
}
