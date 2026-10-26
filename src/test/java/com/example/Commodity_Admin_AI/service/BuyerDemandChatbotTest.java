package com.example.Commodity_Admin_AI.service;

import com.example.Commodity_Admin_AI.ai.GeminiService;
import com.example.Commodity_Admin_AI.ai.RagService;
import com.example.Commodity_Admin_AI.dto.BuyerDemandDto;
import com.example.Commodity_Admin_AI.dto.ChatRequest;
import com.example.Commodity_Admin_AI.dto.ChatResponse;
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
class BuyerDemandChatbotTest {

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
    private List<BuyerDemandDto> sampleDemands;

    @BeforeEach
    void setUp() {
        testSession = new ChatSession("admin");
        testSession.setId(200L);

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

        sampleDemands = createSampleDemands();
    }

    private List<BuyerDemandDto> createSampleDemands() {
        List<BuyerDemandDto> list = new ArrayList<>();
        BuyerDemandDto d = new BuyerDemandDto();
        d.setDemandNumber("DEM-1792400874398");
        d.setCommodityName("Soybean");
        d.setQuantity(200.0);
        d.setFulfilledQuantity(0.0);
        d.setUnit("Quintal");
        d.setTargetRate(4400.0);
        d.setDestination("Pune");
        d.setDestinationZone("West");
        d.setLocation("Pune");
        d.setValidTill("2026-11-30");
        d.setStatus("ACTIVE");
        d.setMaskedLabel("K.K.CANVANSSING AGENT Verified Buyer [West]");
        d.setBuyerName("K.K.CANVANSSING AGENT");
        d.setCreatedAt("2026-10-15T10:00:00");
        list.add(d);
        return list;
    }

    @Test
    @DisplayName("Question A: How many active buyer demands are there?")
    void testActiveBuyerDemandsCount() {
        when(chatSessionRepository.findById(200L)).thenReturn(java.util.Optional.of(testSession));
        when(marketplaceDataService.fetchBuyerDemands()).thenReturn(sampleDemands);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));

        ChatRequest req = new ChatRequest(200L, "How many active buyer demands are there?", "en");
        ChatResponse res = adminChatbotService.processChat(req);

        assertNotNull(res);
        assertEquals("DEMAND", res.getDisplayType());
        assertNotNull(res.getMessage());
        assertTrue(res.getMessage().contains("1 active buyer demand record"), "Must report 1 active demand record");
        assertTrue(res.getMessage().contains("K.K.CANVANSSING AGENT Verified Buyer [West]"), "Must identify the distinct buyer");
        assertTrue(res.getMessage().contains("not the total number of registered marketplace buyers"), "Must differentiate active demand listings from total registered buyers");
    }

    @Test
    @DisplayName("Question B: Show all Soybean demands.")
    void testShowAllSoybeanDemands() {
        when(chatSessionRepository.findById(200L)).thenReturn(java.util.Optional.of(testSession));
        when(marketplaceDataService.fetchBuyerDemands()).thenReturn(sampleDemands);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));

        ChatRequest req = new ChatRequest(200L, "Show all Soybean demands.", "en");
        ChatResponse res = adminChatbotService.processChat(req);

        assertNotNull(res);
        assertEquals("DEMAND", res.getDisplayType());
        assertNotNull(res.getMessage());
        assertTrue(res.getMessage().contains("DEM-1792400874398"), "Must contain demand number");
        assertTrue(res.getMessage().contains("Soybean"), "Must contain commodity");
        assertTrue(res.getMessage().contains("200 Quintals"), "Must contain quantity");
        assertTrue(res.getMessage().contains("0 Quintals"), "Must contain fulfilled quantity");
        assertTrue(res.getMessage().contains("₹4,400 / Quintal"), "Must contain target rate");
        assertTrue(res.getMessage().contains("Pune"), "Must contain destination");
        assertTrue(res.getMessage().contains("2026-11-30"), "Must contain valid till date");
        assertTrue(res.getMessage().contains("ACTIVE"), "Must contain status");
        assertTrue(res.getMessage().contains("K.K.CANVANSSING AGENT Verified Buyer [West]"), "Must contain buyer label");
    }

    @Test
    @DisplayName("Question C: Which commodity has the highest demand?")
    void testHighestDemandCommodity() {
        when(chatSessionRepository.findById(200L)).thenReturn(java.util.Optional.of(testSession));
        when(marketplaceDataService.fetchBuyerDemands()).thenReturn(sampleDemands);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));

        ChatRequest req = new ChatRequest(200L, "Which commodity has the highest demand?", "en");
        ChatResponse res = adminChatbotService.processChat(req);

        assertNotNull(res);
        assertEquals("DEMAND", res.getDisplayType());
        assertNotNull(res.getMessage());
        assertTrue(res.getMessage().contains("Soybean"), "Must identify Soybean as highest demand");
        assertTrue(res.getMessage().contains("200 Quintals"), "Must report 200 Quintals");
        assertTrue(res.getMessage().contains("GET /api/ai/demands"), "Must cite GET /api/ai/demands");
    }

    @Test
    @DisplayName("Question D: What is the total requested quantity for Soybean?")
    void testTotalRequestedQuantityForSoybean() {
        when(chatSessionRepository.findById(200L)).thenReturn(java.util.Optional.of(testSession));
        when(marketplaceDataService.fetchBuyerDemands()).thenReturn(sampleDemands);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));

        ChatRequest req = new ChatRequest(200L, "What is the total requested quantity for Soybean?", "en");
        ChatResponse res = adminChatbotService.processChat(req);

        assertNotNull(res);
        assertEquals("DEMAND", res.getDisplayType());
        assertNotNull(res.getMessage());
        assertTrue(res.getMessage().contains("200 Quintals"), "Must report 200 Quintals");
        assertTrue(res.getMessage().contains("Soybean"), "Must mention Soybean");
        assertTrue(res.getMessage().contains("0 Quintals fulfilled"), "Must report 0 Quintals fulfilled");
    }

    @Test
    @DisplayName("Question E: Find buyer demands with a target rate above ₹4,000.")
    void testTargetRateFilterAbove4000() {
        when(chatSessionRepository.findById(200L)).thenReturn(java.util.Optional.of(testSession));
        when(marketplaceDataService.fetchBuyerDemands()).thenReturn(sampleDemands);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));

        // Threshold 4000: 4400 > 4000 matches strictly
        ChatRequest req = new ChatRequest(200L, "Find buyer demands with a target rate above ₹4,000.", "en");
        ChatResponse res = adminChatbotService.processChat(req);

        assertNotNull(res);
        assertEquals("DEMAND", res.getDisplayType());
        assertNotNull(res.getMessage());
        assertTrue(res.getMessage().contains("DEM-1792400874398"), "Target rate 4400 is strictly greater than 4000");
        assertTrue(res.getMessage().contains("₹4,400 / Quintal"), "Must show target rate");
    }

    @Test
    @DisplayName("Question E (Boundary): Target rate above ₹4,400 or ₹5,000 returns no records")
    void testTargetRateFilterStrictlyGreaterThan() {
        when(chatSessionRepository.findById(200L)).thenReturn(java.util.Optional.of(testSession));
        when(marketplaceDataService.fetchBuyerDemands()).thenReturn(sampleDemands);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));

        // Threshold 4400: 4400 > 4400 is FALSE (strictly greater than)
        ChatRequest req4400 = new ChatRequest(200L, "Find buyer demands with a target rate above ₹4,400.", "en");
        ChatResponse res4400 = adminChatbotService.processChat(req4400);

        assertNotNull(res4400);
        assertTrue(res4400.getMessage().contains("No buyer demands found with a target rate above ₹4,400"),
                "Rate 4400 is NOT strictly greater than 4400, so 0 records must be found");

        // Threshold 5000: 4400 > 5000 is FALSE
        ChatRequest req5000 = new ChatRequest(200L, "Find buyer demands with a target rate above ₹5,000.", "en");
        ChatResponse res5000 = adminChatbotService.processChat(req5000);

        assertNotNull(res5000);
        assertTrue(res5000.getMessage().contains("No buyer demands found with a target rate above ₹5,000"),
                "No demands above 5000");
    }

    @Test
    @DisplayName("Question F: Show demands expiring this week.")
    void testExpiringThisWeek() {
        when(chatSessionRepository.findById(200L)).thenReturn(java.util.Optional.of(testSession));
        when(marketplaceDataService.fetchBuyerDemands()).thenReturn(sampleDemands);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));

        ChatRequest req = new ChatRequest(200L, "Show demands expiring this week.", "en");
        ChatResponse res = adminChatbotService.processChat(req);

        assertNotNull(res);
        assertEquals("DEMAND", res.getDisplayType());
        assertNotNull(res.getMessage());
        assertTrue(res.getMessage().contains("No buyer demands expire this week"), "Must state no demands expire this week");
        assertTrue(res.getMessage().contains("October 19, 2026 to Sunday, October 25, 2026"), "Must cite week Oct 19 to Oct 25");
        assertTrue(res.getMessage().contains("November 30, 2026"), "Must note active Soybean demand is valid until November 30, 2026");
    }

    @Test
    @DisplayName("Multilingual: Marathi and Hindi Buyer Demand Questions")
    void testMultilingualBuyerDemands() {
        when(chatSessionRepository.findById(200L)).thenReturn(java.util.Optional.of(testSession));
        when(marketplaceDataService.fetchBuyerDemands()).thenReturn(sampleDemands);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));

        // Marathi count question
        ChatRequest mrReq = new ChatRequest(200L, "सक्रिय खरेदीदार मागण्या किती आहेत?", "mr");
        ChatResponse mrRes = adminChatbotService.processChat(mrReq);
        assertNotNull(mrRes);
        assertTrue(mrRes.getMessage().contains("1 सक्रिय खरेदीदार मागणी नोंद"), "Marathi active count response");

        // Hindi highest demand question
        ChatRequest hiReq = new ChatRequest(200L, "सर्वाधिक मांग वाली कमोडिटी कौन सी है?", "hi");
        ChatResponse hiRes = adminChatbotService.processChat(hiReq);
        assertNotNull(hiRes);
        assertTrue(hiRes.getMessage().contains("सोयाबीन"), "Hindi highest demand response");
        assertTrue(hiRes.getMessage().contains("200 क्विंटल"), "Hindi quantity");
    }
}
