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

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuyerDemandAndPerformanceTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private IntentService intentService;

    @Mock
    private RagService ragService;

    @Mock
    private GeminiService geminiService;

    @Mock
    private MarketplaceDataService marketplaceDataService;

    private AdminChatbotService chatbotService;
    private ChatSession testSession;

    @BeforeEach
    void setUp() {
        testSession = new ChatSession("admin");
        testSession.setId(10L);
        lenient().when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(testSession);

        chatbotService = new AdminChatbotService(
                chatSessionRepository,
                chatMessageRepository,
                intentService,
                ragService,
                geminiService,
                marketplaceDataService,
                10
        );
    }

    @Test
    @DisplayName("1. Greeting 'hi' responds immediately without calling Marketplace, RAG, or Gemini")
    void testGreeting_FastResponse() {
        when(intentService.detectIntent("hi")).thenReturn(IntentService.INTENT_GREETING);
        when(intentService.getControlledResponse()).thenReturn(IntentService.CONTROLLED_HELP_RESPONSE);

        ChatResponse response = chatbotService.processChat(new ChatRequest("hi"));

        assertNotNull(response);
        assertEquals(IntentService.CONTROLLED_HELP_RESPONSE, response.getMessage());
        verify(marketplaceDataService, never()).fetchMarketRates();
        verify(marketplaceDataService, never()).fetchBuyerDemands(any(), any());
        verify(ragService, never()).retrieveContext(anyString(), anyString());
        verify(geminiService, never()).generateResponse(anyString(), anyString());
    }

    @Test
    @DisplayName("2. Soybean buyer demand query in English returns structured marketplace data")
    void testSoybeanBuyerDemand_English_ReturnsTable() {
        when(intentService.detectIntent("Today what is the demand for Soybean?")).thenReturn(IntentService.INTENT_TODAY_DEMAND);

        BuyerDemandDto demand = new BuyerDemandDto(
                1L, "ABC Foods Pvt Ltd", 1L, "Soybean", 500.0, "Quintal", "Sangli", "2026-10-12", "OPEN", "2026-10-06"
        );
        when(marketplaceDataService.fetchBuyerDemands("Soybean", null)).thenReturn(List.of(demand));

        ChatResponse response = chatbotService.processChat(new ChatRequest("Today what is the demand for Soybean?"));

        assertNotNull(response);
        assertTrue(response.getMessage().contains("ABC Foods Pvt Ltd"));
        assertTrue(response.getMessage().contains("500"));
        assertTrue(response.getMessage().contains("Sangli"));
        assertTrue(response.getMessage().contains("OPEN"));
        verify(ragService, never()).retrieveContext(anyString(), anyString());
        verify(geminiService, never()).generateResponse(anyString(), anyString());
    }

    @Test
    @DisplayName("3. Buyer demand query with no matching records returns clear empty state")
    void testBuyerDemand_NoMatchingRecords_ReturnsEmptyState() {
        when(intentService.detectIntent("Today what is the demand for Cotton?")).thenReturn(IntentService.INTENT_TODAY_DEMAND);
        when(marketplaceDataService.fetchBuyerDemands("Cotton", null)).thenReturn(Collections.emptyList());

        ChatResponse response = chatbotService.processChat(new ChatRequest("Today what is the demand for Cotton?"));

        assertNotNull(response);
        assertTrue(response.getMessage().contains("No active buyer demands found for Cotton"));
    }

    @Test
    @DisplayName("4. Buyer demand query in Hindi returns localized Hindi table and summary")
    void testBuyerDemand_Hindi_ReturnsLocalizedSummary() {
        when(intentService.detectIntent("आज सोयाबीन की मांग क्या है?")).thenReturn(IntentService.INTENT_TODAY_DEMAND);

        BuyerDemandDto demand = new BuyerDemandDto(
                1L, "ABC Foods Pvt Ltd", 1L, "Soybean", 500.0, "Quintal", "Sangli", "2026-10-12", "OPEN", "2026-10-06"
        );
        when(marketplaceDataService.fetchBuyerDemands("Soybean", null)).thenReturn(List.of(demand));

        ChatRequest request = new ChatRequest("आज सोयाबीन की मांग क्या है?");
        request.setLanguage("Hindi");
        ChatResponse response = chatbotService.processChat(request);

        assertNotNull(response);
        assertTrue(response.getMessage().contains("मार्केटप्लेस रिकॉर्ड्स"));
        assertTrue(response.getMessage().contains("खरीदार"));
        assertTrue(response.getMessage().contains("ABC Foods Pvt Ltd"));
        assertTrue(response.getMessage().contains("सोयाबीन"));
        assertTrue(response.getMessage().contains("सांगली"));
    }

    @Test
    @DisplayName("5. Buyer demand query in Marathi returns localized Marathi table and summary")
    void testBuyerDemand_Marathi_ReturnsLocalizedSummary() {
        when(intentService.detectIntent("आज सोयाबीनची मागणी काय आहे?")).thenReturn(IntentService.INTENT_TODAY_DEMAND);

        BuyerDemandDto demand = new BuyerDemandDto(
                1L, "ABC Foods Pvt Ltd", 1L, "Soybean", 500.0, "Quintal", "Sangli", "2026-10-12", "OPEN", "2026-10-06"
        );
        when(marketplaceDataService.fetchBuyerDemands("Soybean", null)).thenReturn(List.of(demand));

        ChatRequest request = new ChatRequest("आज सोयाबीनची मागणी काय आहे?");
        request.setLanguage("Marathi");
        ChatResponse response = chatbotService.processChat(request);

        assertNotNull(response);
        assertTrue(response.getMessage().contains("मार्केटप्लेसच्या नोंदीनुसार"));
        assertTrue(response.getMessage().contains("खरेदीदार"));
        assertTrue(response.getMessage().contains("ABC Foods Pvt Ltd"));
        assertTrue(response.getMessage().contains("सोयाबीन"));
        assertTrue(response.getMessage().contains("सांगली"));
    }

    @Test
    @DisplayName("6. Marketplace API failure returns user-friendly error without crashing")
    void testBuyerDemand_MarketplaceApiFailure_ReturnsHelpfulError() {
        when(intentService.detectIntent("Today what is the demand for Soybean?")).thenReturn(IntentService.INTENT_TODAY_DEMAND);
        when(marketplaceDataService.fetchBuyerDemands("Soybean", null))
                .thenThrow(new RuntimeException("Marketplace connection refused"));

        ChatResponse response = chatbotService.processChat(new ChatRequest("Today what is the demand for Soybean?"));

        assertNotNull(response);
        assertTrue(response.getMessage().contains("marketplace service is currently unavailable"));
        assertFalse(response.getMessage().contains("Marketplace connection refused"), "Must not leak raw technical exception");
    }

    @Test
    @DisplayName("7. Gemini failure when marketplace data is retrieved produces fallback")
    void testGeminiFailure_WithRetrievedHistoricalData_ProducesFallback() {
        when(intentService.detectIntent("Soybean price trend")).thenReturn(IntentService.INTENT_RATE_TREND);
        java.time.LocalDate mockDate = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).minusDays(2);
        when(marketplaceDataService.fetchHistoricalRates("Soybean", null)).thenReturn(List.of(
                new com.example.Commodity_Admin_AI.dto.ChartData(mockDate.toString(), new java.math.BigDecimal("4250"), "Soybean", "Sangli", "Quintal")
        ));
        when(ragService.retrieveContext(anyString(), anyString())).thenReturn("Marketplace context");
        when(geminiService.generateResponse(anyString(), anyString(), any(), anyString()))
                .thenReturn("An error occurred while communicating with the AI service: Failed to generate content");

        ChatRequest request = new ChatRequest("Soybean price trend");
        request.setLanguage("English");
        ChatResponse response = chatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("RATE", response.getDisplayType());
        assertTrue(response.getMessage().contains("According to marketplace records"));
        assertTrue(response.getMessage().contains("4250"));
    }

    @Test
    @DisplayName("8. Query 'How many buyers are looking for Soybean?' counts distinct buyers, not rows")
    void testHowManyBuyersLookingForSoybean_CountsDistinctBuyers() {
        when(intentService.detectIntent("How many buyers are looking for Soybean?")).thenReturn(IntentService.INTENT_TODAY_DEMAND);

        // 2 demand rows with the SAME buyer name
        BuyerDemandDto demand1 = new BuyerDemandDto(
                1L, "ABC Foods Pvt Ltd", 1L, "Soybean", 500.0, "Quintal", "Sangli", "2026-10-12", "OPEN", "2026-10-06"
        );
        BuyerDemandDto demand2 = new BuyerDemandDto(
                2L, "ABC Foods Pvt Ltd", 1L, "Soybean", 200.0, "Quintal", "Sangli", "2026-10-15", "OPEN", "2026-10-07"
        );
        when(marketplaceDataService.fetchBuyerDemands("Soybean", null)).thenReturn(List.of(demand1, demand2));

        ChatResponse response = chatbotService.processChat(new ChatRequest("How many buyers are looking for Soybean?"));

        assertNotNull(response);
        // Distinct buyers = 1, total demand records = 2
        assertTrue(response.getMessage().contains("1 distinct buyer"), "Must report 1 distinct buyer, not 2 buyers");
        assertTrue(response.getMessage().contains("ABC Foods Pvt Ltd"));
        assertTrue(response.getMessage().contains("2 active demand records"));
        verify(ragService, never()).retrieveContext(anyString(), anyString());
        verify(geminiService, never()).generateResponse(anyString(), anyString());
    }

    @Test
    @DisplayName("9. When buyer identity is unavailable, reports matching demand records accurately")
    void testBuyerDemand_WhenBuyerIdentityUnavailable_ReportsDemandRecordsAccurately() {
        when(intentService.detectIntent("How many buyers are looking for Soybean?")).thenReturn(IntentService.INTENT_TODAY_DEMAND);

        // 2 demand rows with null/empty buyer names
        BuyerDemandDto demand1 = new BuyerDemandDto(
                1L, null, 1L, "Soybean", 500.0, "Quintal", "Sangli", "2026-10-12", "OPEN", "2026-10-06"
        );
        BuyerDemandDto demand2 = new BuyerDemandDto(
                2L, "   ", 1L, "Soybean", 200.0, "Quintal", "Sangli", "2026-10-15", "OPEN", "2026-10-07"
        );
        when(marketplaceDataService.fetchBuyerDemands("Soybean", null)).thenReturn(List.of(demand1, demand2));

        ChatResponse response = chatbotService.processChat(new ChatRequest("How many buyers are looking for Soybean?"));

        assertNotNull(response);
        assertTrue(response.getMessage().contains("buyer identity is unavailable"), "Must indicate buyer identity is unavailable");
        assertTrue(response.getMessage().contains("2 matching demand records"), "Must label as matching demand records");
        assertFalse(response.getMessage().contains("2 buyers"), "Must not count rows as buyers when identity is unavailable");
    }

    @Test
    @DisplayName("10. Multiple distinct buyers looking for Soybean are counted correctly")
    void testHowManyBuyers_MultipleDistinctBuyers() {
        when(intentService.detectIntent("How many buyers are looking for Soybean?")).thenReturn(IntentService.INTENT_TODAY_DEMAND);

        // 2 distinct buyers across 3 demand rows
        BuyerDemandDto demand1 = new BuyerDemandDto(
                1L, "ABC Foods Pvt Ltd", 1L, "Soybean", 500.0, "Quintal", "Sangli", "2026-10-12", "OPEN", "2026-10-06"
        );
        BuyerDemandDto demand2 = new BuyerDemandDto(
                2L, "XYZ Agro", 1L, "Soybean", 300.0, "Quintal", "Sangli", "2026-10-13", "OPEN", "2026-10-06"
        );
        BuyerDemandDto demand3 = new BuyerDemandDto(
                3L, "ABC Foods Pvt Ltd", 1L, "Soybean", 200.0, "Quintal", "Sangli", "2026-10-15", "OPEN", "2026-10-07"
        );
        when(marketplaceDataService.fetchBuyerDemands("Soybean", null)).thenReturn(List.of(demand1, demand2, demand3));

        ChatResponse response = chatbotService.processChat(new ChatRequest("How many buyers are looking for Soybean?"));

        assertNotNull(response);
        assertTrue(response.getMessage().contains("2 distinct buyers"));
        assertTrue(response.getMessage().contains("3 active demand records"));
    }

    @Test
    @DisplayName("11. extractCommodities detects commodities in query order without duplicates (English, Hindi, Marathi)")
    void testExtractCommodities_AllScenarios() {
        // 2 commodities
        assertEquals(List.of("Soybean", "Wheat"),
                chatbotService.extractCommodities("Compare Soybean and Wheat demand."));

        // 3 commodities
        assertEquals(List.of("Soybean", "Wheat", "Rice"),
                chatbotService.extractCommodities("Compare Soybean, Wheat, and Rice demand."));

        // 5 commodities
        assertEquals(List.of("Soybean", "Wheat", "Rice", "Maize", "Cotton"),
                chatbotService.extractCommodities("Compare Soybean, Wheat, Rice, Maize, and Cotton demand."));

        // Duplicates removed while preserving first occurrence
        assertEquals(List.of("Soybean", "Wheat", "Rice"),
                chatbotService.extractCommodities("Compare Soybean, Wheat, Soybean, and Rice demand."));

        // Different order preserved
        assertEquals(List.of("Wheat", "Soybean"),
                chatbotService.extractCommodities("Compare Wheat and Soybean demand."));

        // No commodity
        assertTrue(chatbotService.extractCommodities("What is the demand today?").isEmpty());

        // Hindi
        assertEquals(List.of("Soybean", "Wheat"),
                chatbotService.extractCommodities("सोयाबीन और गेहूं की मांग की तुलना करें।"));

        // Marathi
        assertEquals(List.of("Soybean", "Wheat"),
                chatbotService.extractCommodities("सोयाबीन आणि गव्हाची मागणी तुलना करा."));
    }

    @Test
    @DisplayName("12. Multi-commodity buyer demand query returns structured commodities and demands on ChatResponse")
    void testMultiCommodityBuyerDemand_ReturnsStructuredResponse() {
        when(intentService.detectIntent("Compare Soybean and Wheat demand.")).thenReturn(IntentService.INTENT_TODAY_DEMAND);

        BuyerDemandDto demand1 = new BuyerDemandDto(
                1L, "ABC Foods Pvt Ltd", 1L, "Soybean", 500.0, "Quintal", "Sangli", "2026-10-12", "OPEN", "2026-10-06"
        );
        BuyerDemandDto demand2 = new BuyerDemandDto(
                2L, "XYZ Agro", 2L, "Wheat", 300.0, "Quintal", "Pune", "2026-10-14", "OPEN", "2026-10-06"
        );
        when(marketplaceDataService.fetchBuyerDemands(null, null)).thenReturn(List.of(demand1, demand2));

        ChatResponse response = chatbotService.processChat(new ChatRequest("Compare Soybean and Wheat demand."));

        assertNotNull(response);
        assertEquals("DEMAND", response.getDisplayType());
        assertEquals(List.of("Soybean", "Wheat"), response.getCommodities());
        assertNotNull(response.getBuyerDemands());
        assertEquals(2, response.getBuyerDemands().size());
    }
}
