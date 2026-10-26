package com.example.Commodity_Admin_AI.service;

import com.example.Commodity_Admin_AI.ai.GeminiService;
import com.example.Commodity_Admin_AI.ai.RagService;
import com.example.Commodity_Admin_AI.dto.ChatRequest;
import com.example.Commodity_Admin_AI.dto.ChatResponse;
import com.example.Commodity_Admin_AI.dto.SupplyDto;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class SuppliesChatbotTest {

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
    private List<SupplyDto> sampleSupplies;

    @BeforeEach
    void setUp() {
        testSession = new ChatSession("admin");
        testSession.setId(300L);

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

        sampleSupplies = createSampleSupplies();
        when(marketplaceDataService.isConfigured()).thenReturn(true);
    }

    private List<SupplyDto> createSampleSupplies() {
        List<SupplyDto> list = new ArrayList<>();

        // 1. SUP-1792572721202 - Soybean, Sangli, 500 Q, ₹4,300, validTill: 2026-10-19
        SupplyDto s1 = new SupplyDto();
        s1.setId(6L);
        s1.setSupplyNumber("SUP-1792572721202");
        s1.setPartyId(1L);
        s1.setPartyName("Sangli Agro Traders");
        s1.setCommodityName("Soybean");
        s1.setParentCommodityName("SOYBEAN");
        s1.setTotalQuantity(500.0);
        s1.setAvailableQuantity(500.0);
        s1.setLockedQuantity(0.0);
        s1.setAskingRate(4300.0);
        s1.setLocation("Sangli");
        s1.setOriginZone("Maharashtra");
        s1.setMaskedLabel("Sangli Agro Traders");
        s1.setLabSpecs("Moisture: 12%, Foreign Matter: 1%");
        s1.setValidTill("2026-10-19");
        s1.setStatus("ACTIVE");
        s1.setCreatedBy("PRT-0001");
        s1.setCreatedAt("2026-10-21T14:22:01.202443");
        list.add(s1);

        // 2. SUP-1792400860244 - Soybean, Sangli, 500 Q, ₹4,500, validTill: 2026-11-30
        SupplyDto s2 = new SupplyDto();
        s2.setId(1L);
        s2.setSupplyNumber("SUP-1792400860244");
        s2.setPartyId(1L);
        s2.setPartyName("Sangli Agro Traders");
        s2.setCommodityName("Soybean");
        s2.setParentCommodityName("SOYBEAN");
        s2.setTotalQuantity(500.0);
        s2.setAvailableQuantity(500.0);
        s2.setLockedQuantity(0.0);
        s2.setAskingRate(4500.0);
        s2.setLocation("Sangli");
        s2.setOriginZone("West");
        s2.setMaskedLabel("Sangli Agro Traders");
        s2.setValidTill("2026-11-30");
        s2.setStatus("ACTIVE");
        s2.setCreatedBy("PRT-0001");
        s2.setCreatedAt("2026-10-19T14:37:40.246168");
        list.add(s2);

        // 3. SUP-1792572585508 - Soybean, Sangli, 500 Q, ₹4,500, validTill: 2026-10-31 (expiring soonest)
        SupplyDto s3 = new SupplyDto();
        s3.setId(2L);
        s3.setSupplyNumber("SUP-1792572585508");
        s3.setPartyId(1L);
        s3.setPartyName("Sangli Agro Traders");
        s3.setCommodityName("Soybean");
        s3.setParentCommodityName("SOYBEAN");
        s3.setTotalQuantity(500.0);
        s3.setAvailableQuantity(500.0);
        s3.setLockedQuantity(0.0);
        s3.setAskingRate(4500.0);
        s3.setLocation("Sangli");
        s3.setOriginZone("Maharashtra");
        s3.setMaskedLabel("Sangli Agro Traders");
        s3.setLabSpecs("Moisture: 12%, Foreign Matter: 1%");
        s3.setValidTill("2026-10-31");
        s3.setStatus("ACTIVE");
        s3.setCreatedBy("PRT-0001");
        s3.setCreatedAt("2026-10-21T14:19:45.515015");
        list.add(s3);

        // 4. SUP-1792572661871 - Soybean, Sangli, 500 Q, ₹4,500, validTill: 2026-10-16
        SupplyDto s4 = new SupplyDto();
        s4.setId(3L);
        s4.setSupplyNumber("SUP-1792572661871");
        s4.setPartyId(1L);
        s4.setPartyName("Sangli Agro Traders");
        s4.setCommodityName("Soybean");
        s4.setParentCommodityName("SOYBEAN");
        s4.setTotalQuantity(500.0);
        s4.setAvailableQuantity(500.0);
        s4.setLockedQuantity(0.0);
        s4.setAskingRate(4500.0);
        s4.setLocation("Sangli");
        s4.setOriginZone("Maharashtra");
        s4.setMaskedLabel("Sangli Agro Traders");
        s4.setLabSpecs("Moisture: 12%, Foreign Matter: 1%");
        s4.setValidTill("2026-10-16");
        s4.setStatus("ACTIVE");
        s4.setCreatedBy("PRT-0001");
        s4.setCreatedAt("2026-10-21T14:21:01.874074");
        list.add(s4);

        // 5. SUP-1792572709817 - Soybean, Sangli, 100 Q, ₹4,600, validTill: 2026-10-18
        SupplyDto s5 = new SupplyDto();
        s5.setId(5L);
        s5.setSupplyNumber("SUP-1792572709817");
        s5.setPartyId(1L);
        s5.setPartyName("Sangli Agro Traders");
        s5.setCommodityName("Soybean");
        s5.setParentCommodityName("SOYBEAN");
        s5.setTotalQuantity(100.0);
        s5.setAvailableQuantity(100.0);
        s5.setLockedQuantity(0.0);
        s5.setAskingRate(4600.0);
        s5.setLocation("Sangli");
        s5.setOriginZone("Maharashtra");
        s5.setMaskedLabel("Sangli Agro Traders");
        s5.setLabSpecs("Moisture: 12%, Foreign Matter: 1%");
        s5.setValidTill("2026-10-18");
        s5.setStatus("ACTIVE");
        s5.setCreatedBy("PRT-0001");
        s5.setCreatedAt("2026-10-21T14:22:28.561423");
        list.add(s5);

        // 6. SUP-1792572691806 - Soybean, Sangli, 500 Q, ₹4,800, validTill: 2026-10-17
        SupplyDto s6 = new SupplyDto();
        s6.setId(4L);
        s6.setSupplyNumber("SUP-1792572691806");
        s6.setPartyId(1L);
        s6.setPartyName("Sangli Agro Traders");
        s6.setCommodityName("Soybean");
        s6.setParentCommodityName("SOYBEAN");
        s6.setTotalQuantity(500.0);
        s6.setAvailableQuantity(500.0);
        s6.setLockedQuantity(0.0);
        s6.setAskingRate(4800.0);
        s6.setLocation("Sangli");
        s6.setOriginZone("Maharashtra");
        s6.setMaskedLabel("Sangli Agro Traders");
        s6.setLabSpecs("Moisture: 12%, Foreign Matter: 1%");
        s6.setValidTill("2026-10-17");
        s6.setStatus("ACTIVE");
        s6.setCreatedBy("PRT-0001");
        s6.setCreatedAt("2026-10-21T14:21:31.80659");
        list.add(s6);

        // 7. SUP-1792647130344 - MAIZE, Sangli, 600 Q, ₹22,000, validTill: 2026-11-30
        SupplyDto s7 = new SupplyDto();
        s7.setId(10L);
        s7.setSupplyNumber("SUP-1792647130344");
        s7.setPartyId(1L);
        s7.setPartyName("Sangli Agro Traders");
        s7.setCommodityName("MAIZE");
        s7.setParentCommodityName("MAIZE");
        s7.setTotalQuantity(600.0);
        s7.setAvailableQuantity(600.0);
        s7.setLockedQuantity(0.0);
        s7.setAskingRate(22000.0);
        s7.setLocation("Sangli");
        s7.setOriginZone("Maharashtra");
        s7.setMaskedLabel("Sangli Agro Traders");
        s7.setLabSpecs("Moisture: 13%, Foreign Matter: 2%");
        s7.setValidTill("2026-11-30");
        s7.setStatus("ACTIVE");
        s7.setCreatedBy("PRT-0001");
        s7.setCreatedAt("2026-10-22T11:02:10.344589");
        list.add(s7);

        // 8. SUP-1792647148044 - MAIZEW, Kolhapur, 700 Q, ₹22,500, validTill: 2026-11-30
        SupplyDto s8 = new SupplyDto();
        s8.setId(11L);
        s8.setSupplyNumber("SUP-1792647148044");
        s8.setPartyId(1L);
        s8.setPartyName("Sangli Agro Traders");
        s8.setCommodityName("MAIZEW");
        s8.setParentCommodityName("MAIZEW");
        s8.setTotalQuantity(700.0);
        s8.setAvailableQuantity(700.0);
        s8.setLockedQuantity(0.0);
        s8.setAskingRate(22500.0);
        s8.setLocation("Kolhapur");
        s8.setOriginZone("Maharashtra");
        s8.setMaskedLabel("Sangli Agro Traders");
        s8.setLabSpecs("Moisture: 12%, Foreign Matter: 1.5%");
        s8.setValidTill("2026-11-30");
        s8.setStatus("ACTIVE");
        s8.setCreatedBy("PRT-0001");
        s8.setCreatedAt("2026-10-22T11:02:28.047968");
        list.add(s8);

        // 9. SUP-1792647111859 - Wheat, Sangli, 800 Q, ₹28,000, validTill: 2026-11-30
        SupplyDto s9 = new SupplyDto();
        s9.setId(9L);
        s9.setSupplyNumber("SUP-1792647111859");
        s9.setPartyId(1L);
        s9.setPartyName("Sangli Agro Traders");
        s9.setCommodityName("Wheat");
        s9.setParentCommodityName("WHEAT");
        s9.setTotalQuantity(800.0);
        s9.setAvailableQuantity(800.0);
        s9.setLockedQuantity(0.0);
        s9.setAskingRate(28000.0);
        s9.setLocation("Sangli");
        s9.setOriginZone("Maharashtra");
        s9.setMaskedLabel("Sangli Agro Traders");
        s9.setLabSpecs("Moisture: 11%, Foreign Matter: 1%");
        s9.setValidTill("2026-11-30");
        s9.setStatus("ACTIVE");
        s9.setCreatedBy("PRT-0001");
        s9.setCreatedAt("2026-10-22T11:01:51.861071");
        list.add(s9);

        return list;
    }

    @Test
    @DisplayName("Question 1: How many active supplies are available?")
    void test01_ActiveSuppliesCount() {
        when(chatSessionRepository.findById(300L)).thenReturn(java.util.Optional.of(testSession));
        when(marketplaceDataService.fetchSupplies()).thenReturn(sampleSupplies);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));

        ChatRequest req = new ChatRequest(300L, "How many active supplies are available?", "en");
        ChatResponse res = adminChatbotService.processChat(req);

        assertNotNull(res);
        assertEquals("SUPPLIES", res.getDisplayType());
        assertNotNull(res.getMessage());
        assertTrue(res.getMessage().contains("9 active supply listings"), "Must report 9 active supply listings");
        assertTrue(res.getMessage().contains("4,700 Quintals"), "Must report total available quantity 4,700 Quintals");
        assertTrue(res.getMessage().contains("Soybean"), "Must list Soybean");
        assertTrue(res.getMessage().contains("MAIZE"), "Must list MAIZE");
        assertTrue(res.getMessage().contains("MAIZEW"), "Must list MAIZEW");
        assertTrue(res.getMessage().contains("Wheat"), "Must list Wheat");
    }

    @Test
    @DisplayName("Question 2: Show all active supplies for a specified commodity (MAIZE vs MAIZEW exact match)")
    void test02_SuppliesForCommodity_ExactMatch() {
        when(chatSessionRepository.findById(300L)).thenReturn(java.util.Optional.of(testSession));
        when(marketplaceDataService.fetchSupplies()).thenReturn(sampleSupplies);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));

        // Query for MAIZE
        ChatRequest reqMaize = new ChatRequest(300L, "Show all active supplies for Maize", "en");
        ChatResponse resMaize = adminChatbotService.processChat(reqMaize);

        assertNotNull(resMaize);
        assertEquals("SUPPLIES", resMaize.getDisplayType());
        assertTrue(resMaize.getMessage().contains("SUP-1792647130344"), "Must contain MAIZE supply number");
        assertTrue(resMaize.getMessage().contains("600 Quintal"), "Must contain 600 Quintals");
        assertFalse(resMaize.getMessage().contains("SUP-1792647148044"), "MAIZE must NOT match MAIZEW supply number");
        assertFalse(resMaize.getMessage().contains("700 Quintal"), "MAIZE must NOT match 700 Quintal from MAIZEW");

        // Query for MAIZEW
        ChatRequest reqMaizew = new ChatRequest(300L, "Show all active supplies for MAIZEW", "en");
        ChatResponse resMaizew = adminChatbotService.processChat(reqMaizew);

        assertNotNull(resMaizew);
        assertEquals("SUPPLIES", resMaizew.getDisplayType());
        assertTrue(resMaizew.getMessage().contains("SUP-1792647148044"), "Must contain MAIZEW supply number");
        assertTrue(resMaizew.getMessage().contains("700 Quintal"), "Must contain 700 Quintals");
        assertFalse(resMaizew.getMessage().contains("SUP-1792647130344"), "MAIZEW must NOT match MAIZE supply number");

        // Query for Soybean
        ChatRequest reqSoy = new ChatRequest(300L, "Show all active supplies for Soybean", "en");
        ChatResponse resSoy = adminChatbotService.processChat(reqSoy);
        assertNotNull(resSoy);
        assertTrue(resSoy.getMessage().contains("6 active supply listing(s)"), "Must find 6 listings for Soybean");
    }

    @Test
    @DisplayName("Question 3: Which seller has the highest available quantity?")
    void test03_HighestQuantitySeller() {
        when(chatSessionRepository.findById(300L)).thenReturn(java.util.Optional.of(testSession));
        when(marketplaceDataService.fetchSupplies()).thenReturn(sampleSupplies);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));

        ChatRequest req = new ChatRequest(300L, "Which seller has the highest available quantity?", "en");
        ChatResponse res = adminChatbotService.processChat(req);

        assertNotNull(res);
        assertEquals("SUPPLIES", res.getDisplayType());
        assertTrue(res.getMessage().contains("Sangli Agro Traders"), "Must identify Sangli Agro Traders");
        assertTrue(res.getMessage().contains("4,700 Quintals"), "Must state total 4,700 Quintals");
    }

    @Test
    @DisplayName("Question 4: What is the total available quantity for a specified commodity?")
    void test04_TotalAvailableQuantity() {
        when(chatSessionRepository.findById(300L)).thenReturn(java.util.Optional.of(testSession));
        when(marketplaceDataService.fetchSupplies()).thenReturn(sampleSupplies);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));

        // Total for MAIZE
        ChatRequest req1 = new ChatRequest(300L, "What is the total available quantity for Maize?", "en");
        ChatResponse res1 = adminChatbotService.processChat(req1);
        assertNotNull(res1);
        assertTrue(res1.getMessage().contains("600 Quintals"), "MAIZE total must be 600 Quintals");
        assertFalse(res1.getMessage().contains("1,300 Quintals"), "Must NOT combine MAIZE and MAIZEW");

        // Total for MAIZEW
        ChatRequest req2 = new ChatRequest(300L, "What is the total available quantity for MAIZEW?", "en");
        ChatResponse res2 = adminChatbotService.processChat(req2);
        assertNotNull(res2);
        assertTrue(res2.getMessage().contains("700 Quintals"), "MAIZEW total must be 700 Quintals");

        // Total for Soybean
        ChatRequest req3 = new ChatRequest(300L, "What is the total available quantity for Soybean?", "en");
        ChatResponse res3 = adminChatbotService.processChat(req3);
        assertNotNull(res3);
        assertTrue(res3.getMessage().contains("2,600 Quintals"), "Soybean total must be 2,600 Quintals");
    }

    @Test
    @DisplayName("Question 5: Show supplies from a specified location.")
    void test05_SuppliesByLocation() {
        when(chatSessionRepository.findById(300L)).thenReturn(java.util.Optional.of(testSession));
        when(marketplaceDataService.fetchSupplies()).thenReturn(sampleSupplies);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));

        // Location: Kolhapur
        ChatRequest reqKol = new ChatRequest(300L, "Show supplies from Kolhapur", "en");
        ChatResponse resKol = adminChatbotService.processChat(reqKol);
        assertNotNull(resKol);
        assertTrue(resKol.getMessage().contains("SUP-1792647148044"), "Must contain Kolhapur supply number");
        assertTrue(resKol.getMessage().contains("MAIZEW"), "Must contain MAIZEW");

        // Location: Sangli
        ChatRequest reqSan = new ChatRequest(300L, "Show supplies from Sangli", "en");
        ChatResponse resSan = adminChatbotService.processChat(reqSan);
        assertNotNull(resSan);
        assertTrue(resSan.getMessage().contains("8 active supply listing(s)"), "Must find 8 supplies from Sangli");
    }

    @Test
    @DisplayName("Question 6: Which supplies are expiring soon?")
    void test06_ExpiringSoon() {
        when(chatSessionRepository.findById(300L)).thenReturn(java.util.Optional.of(testSession));
        when(marketplaceDataService.fetchSupplies()).thenReturn(sampleSupplies);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));

        ChatRequest req = new ChatRequest(300L, "Which supplies are expiring soon?", "en");
        ChatResponse res = adminChatbotService.processChat(req);

        assertNotNull(res);
        assertTrue(res.getMessage().contains("SUP-1792572585508"), "Must highlight SUP-1792572585508 as expiring soonest");
        assertTrue(res.getMessage().contains("2026-10-31"), "Must mention validity 2026-10-31");
    }

    @Test
    @DisplayName("Question 7: Find supplies priced below a specified amount per unit.")
    void test07_PricedBelow() {
        when(chatSessionRepository.findById(300L)).thenReturn(java.util.Optional.of(testSession));
        when(marketplaceDataService.fetchSupplies()).thenReturn(sampleSupplies);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));

        ChatRequest req = new ChatRequest(300L, "Find supplies priced below 4700 per unit", "en");
        ChatResponse res = adminChatbotService.processChat(req);

        assertNotNull(res);
        assertTrue(res.getMessage().contains("5 active supply listing(s)"), "Must find 5 listings priced below 4700");
        assertTrue(res.getMessage().contains("SUP-1792572721202"), "Must include 4300 listing");
        assertFalse(res.getMessage().contains("SUP-1792572691806"), "Must NOT include 4800 listing");
        assertFalse(res.getMessage().contains("SUP-1792647130344"), "Must NOT include 22000 listing");
    }

    @Test
    @DisplayName("Question 8: Show the details of a supply by exact supply number.")
    void test08_SupplyDetailsByNumber() {
        when(chatSessionRepository.findById(300L)).thenReturn(java.util.Optional.of(testSession));
        when(marketplaceDataService.fetchSupplies()).thenReturn(sampleSupplies);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));

        ChatRequest req = new ChatRequest(300L, "Show the details of supply SUP-1792572721202", "en");
        ChatResponse res = adminChatbotService.processChat(req);

        assertNotNull(res);
        assertTrue(res.getMessage().contains("SUP-1792572721202"), "Must display requested supply number");
        assertTrue(res.getMessage().contains("Soybean"), "Must display commodity Soybean");
        assertTrue(res.getMessage().contains("Sangli Agro Traders"), "Must display seller");
        assertTrue(res.getMessage().contains("4,300"), "Must display asking rate 4,300");
        assertTrue(res.getMessage().contains("Moisture: 12%"), "Must display lab specs");
    }

    @Test
    @DisplayName("Intent Routing: Verify supply queries do not misroute to market-rate handler")
    void test09_IntentRoutingIntegrity() {
        assertEquals(IntentService.INTENT_TODAY_LISTINGS, intentService.detectIntent("How many active supplies are available?"));
        assertEquals(IntentService.INTENT_TODAY_LISTINGS, intentService.detectIntent("Show all active supplies for Soybean"));
        assertEquals(IntentService.INTENT_TODAY_LISTINGS, intentService.detectIntent("Which seller has the highest available quantity?"));
        assertEquals(IntentService.INTENT_TODAY_LISTINGS, intentService.detectIntent("What is the total available quantity for Maize?"));
        assertEquals(IntentService.INTENT_TODAY_LISTINGS, intentService.detectIntent("Show supplies from Sangli"));
        assertEquals(IntentService.INTENT_TODAY_LISTINGS, intentService.detectIntent("Which supplies are expiring soon?"));
        assertEquals(IntentService.INTENT_TODAY_LISTINGS, intentService.detectIntent("Find supplies priced below 4700 per unit"));
        assertEquals(IntentService.INTENT_TODAY_LISTINGS, intentService.detectIntent("Show the details of supply SUP-1792572721202"));

        // Preserve market rate queries
        assertEquals(IntentService.INTENT_CURRENT_RATE, intentService.detectIntent("What is today's Soybean rate?"));
        assertEquals(IntentService.INTENT_RATE_HISTORY, intentService.detectIntent("Show me the Soybean rate for the last 7 days."));
    }

    @Test
    @DisplayName("Multilingual: Active supplies in Marathi and Hindi")
    void test10_MultilingualSupplies() {
        when(chatSessionRepository.findById(300L)).thenReturn(java.util.Optional.of(testSession));
        when(marketplaceDataService.fetchSupplies()).thenReturn(sampleSupplies);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));

        // Marathi
        ChatRequest reqMr = new ChatRequest(300L, "सध्या किती पुरवठा उपलब्ध आहे?", "mr");
        ChatResponse resMr = adminChatbotService.processChat(reqMr);
        assertNotNull(resMr);
        assertTrue(resMr.getMessage().contains("सक्रिय पुरवठा नोंदी"), "Must provide Marathi response");

        // Hindi
        ChatRequest reqHi = new ChatRequest(300L, "कितनी सक्रिय आपूर्ति उपलब्ध है?", "hi");
        ChatResponse resHi = adminChatbotService.processChat(reqHi);
        assertNotNull(resHi);
        assertTrue(resHi.getMessage().contains("सक्रिय आपूर्ति लिस्टिंग"), "Must provide Hindi response");
    }
}
