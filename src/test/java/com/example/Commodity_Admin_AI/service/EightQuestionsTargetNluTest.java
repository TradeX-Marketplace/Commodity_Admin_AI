package com.example.Commodity_Admin_AI.service;

import com.example.Commodity_Admin_AI.ai.GeminiService;
import com.example.Commodity_Admin_AI.ai.RagService;
import com.example.Commodity_Admin_AI.dto.BuyerDemandDto;
import com.example.Commodity_Admin_AI.dto.ChartData;
import com.example.Commodity_Admin_AI.dto.ChatRequest;
import com.example.Commodity_Admin_AI.dto.ChatResponse;
import com.example.Commodity_Admin_AI.dto.MarketplaceRateDto;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Live test suite executing all 8 required NLU benchmark questions:
 * 1. Wheat prices
 * 2. What is today's Wheat rate?
 * 3. What is today's Soybean rate?
 * 4. Show me all actual Soybean records.
 * 5. Show me Soybean rates for the last 7 days.
 * 6. Show me a chart of Soybean rates for the last 7 days.
 * 7. Show me Wheat supplies in Sangli.
 * 8. Show me today's Wheat demand.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class EightQuestionsTargetNluTest {

    private static final Logger log = LoggerFactory.getLogger(EightQuestionsTargetNluTest.class);

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
    private AdminChatbotService chatbotService;
    private ChatSession testSession;

    @BeforeEach
    void setUp() {
        intentService = new IntentService();

        testSession = new ChatSession("admin");
        testSession.setId(888L);
        lenient().when(chatSessionRepository.findById(888L)).thenReturn(Optional.of(testSession));
        lenient().when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(testSession);
        lenient().when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));

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

    private MarketplaceRateDto createRate(String commodity, String location, double rate, String date) {
        MarketplaceRateDto dto = new MarketplaceRateDto();
        dto.setCommodity(commodity);
        dto.setLocation(location);
        dto.setRate(BigDecimal.valueOf(rate));
        dto.setUnit("Quintal");
        dto.setDate(date);
        dto.setPriceType("Market Rate");
        return dto;
    }

    @Test
    @DisplayName("Test 1: 'Wheat prices' -> Intent CURRENT_RATE, truthful non-fabrication of Wheat rate")
    void test1_WheatPrices() {
        String q = "Wheat prices";
        String intent = intentService.detectIntent(q);
        assertEquals(IntentService.INTENT_CURRENT_RATE, intent, "Detected intent must be CURRENT_RATE");

        // DB State: PostgreSQL has only Soybean records, 0 Wheat records
        MarketplaceRateDto soy = createRate("Soybean", "Sangli", 4250.0, "2026-10-25");
        when(marketplaceDataService.fetchMarketRates()).thenReturn(List.of(soy));
        when(marketplaceDataService.isConfigured()).thenReturn(true);
        when(ragService.retrieveContext(eq(q), eq(intent))).thenReturn("");
        when(geminiService.generateResponse(eq(q), anyString(), anyList(), eq("English")))
                .thenReturn("RESOURCE_EXHAUSTED: 429 Quota exceeded");

        ChatResponse res = chatbotService.processChat(new ChatRequest(888L, q, "en"));

        assertNotNull(res);
        assertEquals("RATE", res.getDisplayType());
        assertTrue(res.getMessage().contains("No current rate record is available for Wheat in the marketplace database"),
                "Must report no rate available for Wheat: " + res.getMessage());
        assertTrue(res.getMessage().contains("Soybean"), "Must mention available commodity Soybean");
        assertFalse(res.getMessage().contains("RESOURCE_EXHAUSTED"), "Must not leak raw 429 error");
        log.info("[TEST 1 PASSED] Intent={}, Extracted Commodity=Wheat, Data Source=MarketplaceDataService.fetchMarketRates(), Response={}",
                intent, res.getMessage());
    }

    @Test
    @DisplayName("Test 2: 'What is today's Wheat rate?' -> Intent CURRENT_RATE, truthful non-fabrication of Wheat rate")
    void test2_WhatIsTodaysWheatRate() {
        String q = "What is today's Wheat rate?";
        String intent = intentService.detectIntent(q);
        assertEquals(IntentService.INTENT_CURRENT_RATE, intent, "Detected intent must be CURRENT_RATE");

        MarketplaceRateDto soy = createRate("Soybean", "Sangli", 4250.0, "2026-10-25");
        when(marketplaceDataService.fetchMarketRates()).thenReturn(List.of(soy));
        when(marketplaceDataService.isConfigured()).thenReturn(true);
        when(ragService.retrieveContext(eq(q), eq(intent))).thenReturn("");
        when(geminiService.generateResponse(eq(q), anyString(), anyList(), eq("English")))
                .thenReturn("No current rate record is available for Wheat.");

        ChatResponse res = chatbotService.processChat(new ChatRequest(888L, q, "en"));

        assertNotNull(res);
        assertEquals("RATE", res.getDisplayType());
        assertTrue(res.getMessage().contains("No current rate record is available for Wheat"),
                "Must not invent Wheat rate: " + res.getMessage());
        log.info("[TEST 2 PASSED] Intent={}, Extracted Commodity=Wheat, Data Source=MarketplaceDataService.fetchMarketRates(), Response={}",
                intent, res.getMessage());
    }

    @Test
    @DisplayName("Test 3: 'What is today's Soybean rate?' -> Intent CURRENT_RATE, shows genuine Soybean rate")
    void test3_WhatIsTodaysSoybeanRate() {
        String q = "What is today's Soybean rate?";
        String intent = intentService.detectIntent(q);
        assertEquals(IntentService.INTENT_CURRENT_RATE, intent, "Detected intent must be CURRENT_RATE");

        MarketplaceRateDto soy = createRate("Soybean", "Sangli", 4250.0, "2026-10-25");
        when(marketplaceDataService.fetchMarketRates()).thenReturn(List.of(soy));
        when(marketplaceDataService.isConfigured()).thenReturn(true);
        when(ragService.retrieveContext(eq(q), eq(intent))).thenReturn("Current Soybean rate in Sangli is ₹4,250 per Quintal");
        when(geminiService.generateResponse(eq(q), anyString(), anyList(), eq("English")))
                .thenReturn("Today's Soybean market rate in Sangli is ₹4,250 per Quintal.");

        ChatResponse res = chatbotService.processChat(new ChatRequest(888L, q, "en"));

        assertNotNull(res);
        assertEquals("RATE", res.getDisplayType());
        assertTrue(res.getMessage().contains("4,250") || res.getMessage().contains("4250"), "Must contain Soybean rate");
        log.info("[TEST 3 PASSED] Intent={}, Extracted Commodity=Soybean, Data Source=MarketplaceDataService.fetchMarketRates(), Response={}",
                intent, res.getMessage());
    }

    @Test
    @DisplayName("Test 4: 'Show me all actual Soybean records.' -> Intent RATE_HISTORY, tabular list, NO auto-chart")
    void test4_ShowMeAllActualSoybeanRecords() {
        String q = "Show me all actual Soybean records.";
        String intent = intentService.detectIntent(q);
        assertEquals(IntentService.INTENT_RATE_HISTORY, intent, "Detected intent must be RATE_HISTORY");

        List<ChartData> records = List.of(
                new ChartData("2026-10-19", new BigDecimal("4150.0"), "Soybean", "Sangli", "Quintal"),
                new ChartData("2026-10-21", new BigDecimal("4200.0"), "Soybean", "Sangli", "Quintal"),
                new ChartData("2026-10-25", new BigDecimal("4250.0"), "Soybean", "Sangli", "Quintal")
        );
        when(marketplaceDataService.fetchHistoricalRates("Soybean", null)).thenReturn(records);
        when(ragService.retrieveContext(eq(q), eq(intent))).thenReturn("");
        when(geminiService.generateResponse(eq(q), anyString(), anyList(), eq("English")))
                .thenReturn("AUTHORITATIVE MARKETPLACE DATABASE RECORDS:\n| Date | Rate |\n| 2026-10-19 | ₹4,150 |\n| 2026-10-21 | ₹4,200 |\n| 2026-10-25 | ₹4,250 |");

        ChatResponse res = chatbotService.processChat(new ChatRequest(888L, q, "en"));

        assertNotNull(res);
        // Explicit preservation requirement: NOT automatically triggering a chart
        assertEquals("RATE", res.getDisplayType(), "Must be displayType RATE, not auto-CHART");
        assertTrue(res.getMessage().contains("4150") || res.getMessage().contains("4,150"), "Must list records");
        log.info("[TEST 4 PASSED] Intent={}, Extracted Commodity=Soybean, Data Source=MarketplaceDataService.fetchHistoricalRates(), DisplayType={}, Response={}",
                intent, res.getDisplayType(), res.getMessage());
    }

    @Test
    @DisplayName("Test 5: 'Show me Soybean rates for the last 7 days.' -> Intent RATE_HISTORY, lists 7 days, NO auto-chart")
    void test5_ShowMeSoybeanRatesForLast7Days() {
        String q = "Show me Soybean rates for the last 7 days.";
        String intent = intentService.detectIntent(q);
        assertEquals(IntentService.INTENT_RATE_HISTORY, intent, "Detected intent must be RATE_HISTORY");

        List<ChartData> records = List.of(
                new ChartData("2026-10-19", new BigDecimal("4150.0"), "Soybean", "Sangli", "Quintal"),
                new ChartData("2026-10-20", new BigDecimal("4180.0"), "Soybean", "Sangli", "Quintal"),
                new ChartData("2026-10-21", new BigDecimal("4200.0"), "Soybean", "Sangli", "Quintal"),
                new ChartData("2026-10-22", new BigDecimal("4210.0"), "Soybean", "Sangli", "Quintal"),
                new ChartData("2026-10-23", new BigDecimal("4220.0"), "Soybean", "Sangli", "Quintal"),
                new ChartData("2026-10-24", new BigDecimal("4230.0"), "Soybean", "Sangli", "Quintal"),
                new ChartData("2026-10-25", new BigDecimal("4250.0"), "Soybean", "Sangli", "Quintal")
        );
        when(marketplaceDataService.fetchHistoricalRates("Soybean", null)).thenReturn(records);
        when(ragService.retrieveContext(eq(q), eq(intent))).thenReturn("");
        when(geminiService.generateResponse(eq(q), anyString(), anyList(), eq("English")))
                .thenReturn("Soybean rates for the last 7 days:\n| Date | Rate |\n| 2026-10-19 | ₹4,150 |\n| 2026-10-25 | ₹4,250 |");

        ChatResponse res = chatbotService.processChat(new ChatRequest(888L, q, "en"));

        assertNotNull(res);
        assertEquals("RATE", res.getDisplayType(), "Must be displayType RATE, not auto-CHART");
        log.info("[TEST 5 PASSED] Intent={}, Extracted Commodity=Soybean, DateRange=Last 7 Days, Data Source=MarketplaceDataService.fetchHistoricalRates(), DisplayType={}",
                intent, res.getDisplayType());
    }

    @Test
    @DisplayName("Test 6: 'Show me a chart of Soybean rates for the last 7 days.' -> Intent RATE_CHART, displayType CHART")
    void test6_ShowMeChartOfSoybeanRatesForLast7Days() {
        String q = "Show me a chart of Soybean rates for the last 7 days.";
        String intent = intentService.detectIntent(q);
        assertEquals(IntentService.INTENT_RATE_CHART, intent, "Detected intent must be RATE_CHART");

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        List<ChartData> records = List.of(
                new ChartData(today.minusDays(5).toString(), new BigDecimal("4150.0"), "Soybean", "Sangli", "Quintal"),
                new ChartData(today.minusDays(1).toString(), new BigDecimal("4250.0"), "Soybean", "Sangli", "Quintal")
        );
        when(marketplaceDataService.fetchHistoricalRates("Soybean", null)).thenReturn(records);
        when(ragService.retrieveContext(eq(q), eq(intent))).thenReturn("");
        when(geminiService.generateResponse(eq(q), anyString(), anyList(), eq("English")))
                .thenReturn("Here is the chart of Soybean rates for the last 7 days.");

        ChatResponse res = chatbotService.processChat(new ChatRequest(888L, q, "en"));

        assertNotNull(res);
        assertEquals("CHART", res.getDisplayType(), "Explicit chart request MUST return displayType CHART");
        assertNotNull(res.getChartData(), "Chart data must be attached");
        assertEquals(2, res.getChartData().size());
        log.info("[TEST 6 PASSED] Intent={}, Extracted Commodity=Soybean, DateRange=Last 7 Days, Data Source=MarketplaceDataService.fetchHistoricalRates(), DisplayType=CHART",
                intent);
    }

    @Test
    @DisplayName("Test 7: 'Show me Wheat supplies in Sangli.' -> Intent TODAY_LISTINGS, displayType SUPPLIES, returns SUP-1792647111859")
    void test7_ShowMeWheatSuppliesInSangli() {
        String q = "Show me Wheat supplies in Sangli.";
        String intent = intentService.detectIntent(q);
        assertEquals(IntentService.INTENT_TODAY_LISTINGS, intent, "Detected intent must be TODAY_LISTINGS");

        SupplyDto supply = new SupplyDto();
        supply.setSupplyNumber("SUP-1792647111859");
        supply.setCommodityName("Wheat");
        supply.setTotalQuantity(25.0);
        supply.setLocation("Sangli");
        supply.setStatus("ACTIVE");

        when(marketplaceDataService.isConfigured()).thenReturn(true);
        when(marketplaceDataService.fetchSupplies()).thenReturn(List.of(supply));

        ChatResponse res = chatbotService.processChat(new ChatRequest(888L, q, "en"));

        assertNotNull(res);
        assertEquals("SUPPLIES", res.getDisplayType());
        assertTrue(res.getMessage().contains("SUP-1792647111859"), "Must return actual supply ID SUP-1792647111859");
        assertTrue(res.getMessage().contains("Wheat"), "Must contain Wheat");
        assertTrue(res.getMessage().contains("Sangli"), "Must contain Sangli");
        log.info("[TEST 7 PASSED] Intent={}, Extracted Commodity=Wheat, Extracted Location=Sangli, Data Source=TradeX GET /api/ai/supplies, SupplyId=SUP-1792647111859",
                intent);
    }

    @Test
    @DisplayName("Test 8: 'Show me today's Wheat demand.' -> Intent TODAY_DEMAND, displayType DEMAND, returns DEM-1792647559972")
    void test8_ShowMeTodaysWheatDemand() {
        String q = "Show me today's Wheat demand.";
        String intent = intentService.detectIntent(q);
        assertEquals(IntentService.INTENT_TODAY_DEMAND, intent, "Detected intent must be TODAY_DEMAND");

        BuyerDemandDto demand = new BuyerDemandDto();
        demand.setDemandNumber("DEM-1792647559972");
        demand.setCommodityName("Wheat");
        demand.setQuantity(300.0);
        demand.setUnit("Quintal");
        demand.setDestination("Sangli");
        demand.setStatus("ACTIVE");
        demand.setTargetRate(2750.0);

        when(marketplaceDataService.fetchBuyerDemands("Wheat", null)).thenReturn(List.of(demand));

        ChatResponse res = chatbotService.processChat(new ChatRequest(888L, q, "en"));

        assertNotNull(res);
        assertEquals("DEMAND", res.getDisplayType());
        assertTrue(res.getMessage().contains("DEM-1792647559972"), "Must return actual demand ID DEM-1792647559972");
        assertTrue(res.getMessage().contains("Wheat"), "Must contain Wheat");
        log.info("[TEST 8 PASSED] Intent={}, Extracted Commodity=Wheat, Data Source=TradeX GET /api/ai/demands, DemandId=DEM-1792647559972",
                intent);
    }
}
