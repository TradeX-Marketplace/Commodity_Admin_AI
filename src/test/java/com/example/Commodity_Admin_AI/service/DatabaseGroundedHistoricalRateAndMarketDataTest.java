package com.example.Commodity_Admin_AI.service;

import com.example.Commodity_Admin_AI.ai.GeminiService;
import com.example.Commodity_Admin_AI.ai.RagService;
import com.example.Commodity_Admin_AI.dto.ChartData;
import com.example.Commodity_Admin_AI.dto.ChatRequest;
import com.example.Commodity_Admin_AI.dto.ChatResponse;
import com.example.Commodity_Admin_AI.dto.MarketplaceRateDto;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verification test suite for database-backed data retrieval and date-window filtering:
 * 1. "last N days" is defined consistently as N calendar dates including today.
 *    For 7 days on 2026-10-14, the window is October 8–14 inclusive. For 1 day, it is October 14 only.
 * 2. October 7 is NEVER included in chartData for October 8–14.
 * 3. When 0 records match the requested window, chartData is an empty array ([]) and a clear message is returned.
 * 4. October 7 may be mentioned separately as the latest available rate outside the requested period.
 * 5. Matching date ranges (e.g. October 1 to 7) correctly include October 7 with CHART displayType.
 * 6. Multilingual support (English, Marathi, Hindi) preserves clear grounded facts.
 */
@ExtendWith(MockitoExtension.class)
class DatabaseGroundedHistoricalRateAndMarketDataTest {

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
    private ChatSession session;

    @BeforeEach
    void setUp() {
        session = new ChatSession("admin");
        session.setId(777L);
        lenient().when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(session);
        lenient().when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
    @DisplayName("Historical rates: 'last 7 days' on 2026-10-14 evaluates to Oct 8-14; returns empty chartData and mentions Oct 7 outside period")
    void testHistoricalWheatQuery_Last7Days_Oct8To14_NoRecordsInWindow_ReturnsEmptyChartData_MentionsOct7Separately() {
        String question = "Show me the Wheat rate for the last 7 days";
        when(intentService.detectIntent(question)).thenReturn(IntentService.INTENT_RATE_TREND);

        // Database verified state: Only 1 Wheat record exists on 2026-10-07 in Pune at Rs 2800
        ChartData wheatPoint = new ChartData("2026-10-07", new BigDecimal("2800.0"), "Wheat", "Pune", "Quintal");
        when(marketplaceDataService.fetchHistoricalRates("Wheat", null)).thenReturn(List.of(wheatPoint));
        when(ragService.retrieveContext(eq(question), eq(IntentService.INTENT_RATE_TREND))).thenReturn("");
        when(ragService.isQuotaExhausted()).thenReturn(false);

        // Gemini explanation grounded in DB context
        when(geminiService.generateResponse(eq(question), anyString(), anyList(), eq("English")))
                .thenReturn("No Wheat historical rates are recorded for October 8–14, 2026. The latest available Wheat rate on record is ₹2,800 per Quintal (Pune, 2026-10-07), which is outside the requested period.");

        ChatRequest request = new ChatRequest(777L, question, "en");
        ChatResponse response = chatbotService.processChat(request);

        assertNotNull(response);
        assertNull(response.getDisplayType(), "Empty data must NOT have CHART displayType");
        assertNotNull(response.getChartData());
        assertTrue(response.getChartData().isEmpty(), "chartData must be empty; October 7 must NEVER be included for October 8-14");

        // Verify message states no records for Oct 8-14 and mentions Oct 7 as outside requested period
        assertTrue(response.getMessage().contains("No Wheat historical rates are recorded for October 8–14, 2026")
                || response.getMessage().contains("2026-10-08"), "Must report no records for Oct 8-14");
        assertTrue(response.getMessage().contains("2026-10-07"), "May mention October 7 as latest available rate");
        assertTrue(response.getMessage().contains("outside the requested period"), "Must explicitly label October 7 as outside requested period");
    }

    @Test
    @DisplayName("Historical rates: 'last 1 day' on 2026-10-14 evaluates to Oct 14 only; returns empty chartData")
    void testHistoricalWheatQuery_Last1Day_Oct14_NoRecordsInWindow_ReturnsEmptyChartData() {
        String question = "Show me the Wheat rate for the last 1 day";
        when(intentService.detectIntent(question)).thenReturn(IntentService.INTENT_RATE_TREND);

        ChartData wheatPoint = new ChartData("2026-10-07", new BigDecimal("2800.0"), "Wheat", "Pune", "Quintal");
        when(marketplaceDataService.fetchHistoricalRates("Wheat", null)).thenReturn(List.of(wheatPoint));
        when(ragService.retrieveContext(eq(question), eq(IntentService.INTENT_RATE_TREND))).thenReturn("");
        when(ragService.isQuotaExhausted()).thenReturn(false);

        when(geminiService.generateResponse(eq(question), anyString(), anyList(), eq("English")))
                .thenReturn("No Wheat historical rates are recorded for October 14, 2026. The latest available Wheat rate on record is ₹2,800 per Quintal in Pune on October 7, 2026, which is outside the requested period.");

        ChatRequest request = new ChatRequest(777L, question, "en");
        ChatResponse response = chatbotService.processChat(request);

        assertNotNull(response);
        assertNull(response.getDisplayType());
        assertNotNull(response.getChartData());
        assertTrue(response.getChartData().isEmpty(), "chartData must be empty for Oct 14");
        assertTrue(response.getMessage().contains("October 14, 2026") || response.getMessage().contains("2026-10-14"));
    }

    @Test
    @DisplayName("Historical rates: matching date range (October 1 to 7, 2026) includes October 7 record in chartData")
    void testHistoricalWheatQuery_MatchingDateRange_Oct1To7_ReturnsRecordAndChartDisplay() {
        String question = "Show rates for Wheat between 1 October 2026 and 7 October 2026";
        when(intentService.detectIntent(question)).thenReturn(IntentService.INTENT_RATE_TREND);

        ChartData wheatPoint = new ChartData("2026-10-07", new BigDecimal("2800.0"), "Wheat", "Pune", "Quintal");
        when(marketplaceDataService.fetchHistoricalRates("Wheat", null)).thenReturn(List.of(wheatPoint));
        when(ragService.retrieveContext(eq(question), eq(IntentService.INTENT_RATE_TREND))).thenReturn("");
        when(ragService.isQuotaExhausted()).thenReturn(false);

        when(geminiService.generateResponse(eq(question), anyString(), anyList(), eq("English")))
                .thenReturn("According to marketplace records, the Wheat rate in Pune on 2026-10-07 was ₹2,800 per Quintal.");

        ChatRequest request = new ChatRequest(777L, question, "en");
        ChatResponse response = chatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("RATE", response.getDisplayType(), "Matching records should have RATE displayType");
        assertNotNull(response.getChartData());
        assertEquals(1, response.getChartData().size(), "chartData should contain the October 7 matching record");
        assertEquals("2026-10-07", response.getChartData().get(0).getDate());
        assertEquals(new BigDecimal("2800.0"), response.getChartData().get(0).getRate());
    }

    @Test
    @DisplayName("Historical rates: explicit range with no matching records (October 8 to 14, 2026) returns empty chartData")
    void testHistoricalWheatQuery_DateRangeWithNoMatchingRecords_Oct8To14_ReturnsEmptyChartData() {
        String question = "Show rates for Wheat between 8 October 2026 and 14 October 2026";
        when(intentService.detectIntent(question)).thenReturn(IntentService.INTENT_RATE_TREND);

        ChartData wheatPoint = new ChartData("2026-10-07", new BigDecimal("2800.0"), "Wheat", "Pune", "Quintal");
        when(marketplaceDataService.fetchHistoricalRates("Wheat", null)).thenReturn(List.of(wheatPoint));
        when(ragService.retrieveContext(eq(question), eq(IntentService.INTENT_RATE_TREND))).thenReturn("");
        when(ragService.isQuotaExhausted()).thenReturn(false);

        when(geminiService.generateResponse(eq(question), anyString(), anyList(), eq("English")))
                .thenReturn("An error occurred while communicating with the AI service: Timeout");

        ChatRequest request = new ChatRequest(777L, question, "en");
        ChatResponse response = chatbotService.processChat(request);

        assertNotNull(response);
        assertNull(response.getDisplayType());
        assertNotNull(response.getChartData());
        assertTrue(response.getChartData().isEmpty(), "No records match Oct 8-14; chartData must be empty");
        assertTrue(response.getMessage().contains("No Wheat historical rates are recorded for October 8–14, 2026"));
        assertTrue(response.getMessage().contains("outside the requested period"));
    }

    @Test
    @DisplayName("Historical rates: Marathi query for last 7 days returns empty chartData and explains missing dates in Marathi")
    void testHistoricalWheatQuery_Marathi_IdentifiesMissingDatesInMarathi() {
        String question = "गव्हाचे मागील ७ दिवसांचे दर दाखवा";
        when(intentService.detectIntent(question)).thenReturn(IntentService.INTENT_RATE_TREND);

        ChartData wheatPoint = new ChartData("2026-10-07", new BigDecimal("2800.0"), "Wheat", "Pune", "Quintal");
        when(marketplaceDataService.fetchHistoricalRates("Wheat", null)).thenReturn(List.of(wheatPoint));
        when(ragService.retrieveContext(eq(question), eq(IntentService.INTENT_RATE_TREND))).thenReturn("");

        when(geminiService.generateResponse(eq(question), anyString(), anyList(), eq("Marathi")))
                .thenReturn("An error occurred while communicating with the AI service: Failed");

        ChatRequest request = new ChatRequest(777L, question, "mr");
        ChatResponse response = chatbotService.processChat(request);

        assertNotNull(response);
        assertNull(response.getDisplayType());
        assertNotNull(response.getChartData());
        LocalDate expectedStart = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).minusDays(6);
        assertTrue(response.getMessage().contains(expectedStart.toString()) || response.getMessage().contains("2026-10-08") || response.getMessage().contains("८ ते १४ ऑक्टोबर २०२६") || response.getMessage().contains("ऑक्टोबर २०२६"));
        assertTrue(response.getMessage().contains("मागितलेल्या कालावधीबाहेर"));
    }

    @Test
    @DisplayName("Historical rates: Hindi query for last 7 days returns empty chartData and explains missing dates in Hindi")
    void testHistoricalWheatQuery_Hindi_IdentifiesMissingDatesInHindi() {
        String question = "पिछले 7 दिनों के गेहूं के भाव दिखाएं";
        when(intentService.detectIntent(question)).thenReturn(IntentService.INTENT_RATE_TREND);

        ChartData wheatPoint = new ChartData("2026-10-07", new BigDecimal("2800.0"), "Wheat", "Pune", "Quintal");
        when(marketplaceDataService.fetchHistoricalRates("Wheat", null)).thenReturn(List.of(wheatPoint));
        when(ragService.retrieveContext(eq(question), eq(IntentService.INTENT_RATE_TREND))).thenReturn("");

        when(geminiService.generateResponse(eq(question), anyString(), anyList(), eq("Hindi")))
                .thenReturn("An error occurred while communicating with the AI service: Failed");

        ChatRequest request = new ChatRequest(777L, question, "hi");
        ChatResponse response = chatbotService.processChat(request);

        assertNotNull(response);
        assertNull(response.getDisplayType());
        assertNotNull(response.getChartData());
        assertTrue(response.getChartData().isEmpty(), "chartData must be empty for Oct 8-14");
        LocalDate expectedStartHi = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).minusDays(6);
        assertTrue(response.getMessage().contains(expectedStartHi.toString()) || response.getMessage().contains("2026-10-08") || response.getMessage().contains("8 से 14 अक्टूबर 2026") || response.getMessage().contains("अक्टूबर 2026"));
        assertTrue(response.getMessage().contains("अनुरोधित अवधि से बाहर"));
    }

    @Test
    @DisplayName("Historical rates: Cotton with 0 records returns clear empty state without hallucinating or plotting fake charts")
    void testHistoricalCottonQuery_ZeroRecords_ReturnsClearEmptyState() {
        String question = "Show historical rates for Cotton in Surat";
        when(intentService.detectIntent(question)).thenReturn(IntentService.INTENT_RATE_TREND);

        when(marketplaceDataService.fetchHistoricalRates("Cotton", "Surat")).thenReturn(Collections.emptyList());
        when(ragService.retrieveContext(eq(question), eq(IntentService.INTENT_RATE_TREND))).thenReturn("");

        when(geminiService.generateResponse(eq(question), anyString(), anyList(), eq("English")))
                .thenReturn("An error occurred while communicating with the AI service: Failed");

        ChatRequest request = new ChatRequest(777L, question, "en");
        ChatResponse response = chatbotService.processChat(request);

        assertNotNull(response);
        assertNull(response.getDisplayType(), "Empty data must NOT have CHART displayType");
        assertNotNull(response.getChartData());
        assertTrue(response.getChartData().isEmpty(), "Empty data must have empty chart data points");
        assertTrue(response.getMessage().toLowerCase(java.util.Locale.ROOT).contains("no cotton historical rates are recorded")
                || response.getMessage().toLowerCase(java.util.Locale.ROOT).contains("no historical rate records are currently available for cotton"),
                "Response must clearly state that no records exist in the marketplace database");
        assertTrue(response.getMessage().contains("Marketplace Data Status:"));
    }

    @Test
    @DisplayName("Current rates: Wheat rate query returns exact PostgreSQL rate via Marketplace API")
    void testCurrentRate_Wheat_ReturnsExactDatabaseRate() {
        String question = "What is today's rate for Wheat?";
        when(intentService.detectIntent(question)).thenReturn(IntentService.INTENT_CURRENT_RATE);

        MarketplaceRateDto wheatRate = new MarketplaceRateDto("Wheat", "Pune", new BigDecimal("2800.00"), "Quintal", "2026-10-14");
        MarketplaceRateDto soybeanRate = new MarketplaceRateDto("Soybean", "Sangli", new BigDecimal("4250.00"), "Quintal", "2026-10-14");
        when(marketplaceDataService.fetchMarketRates()).thenReturn(List.of(soybeanRate, wheatRate));

        ChatRequest request = new ChatRequest(777L, question, "en");
        ChatResponse response = chatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("RATE", response.getDisplayType());
        assertTrue(response.getMessage().contains("Wheat"));
        assertTrue(response.getMessage().contains("Pune"));
        assertTrue(response.getMessage().contains("2800.00"));
        assertTrue(response.getMessage().contains("Quintal"));
    }

    @Test
    @DisplayName("Current rates: Cotton missing from database clearly reports no record rather than fabricating rates")
    void testCurrentRate_MissingCommodity_Cotton_ReportsNoRecordAvailable() {
        String question = "What is today's rate for Cotton?";
        when(intentService.detectIntent(question)).thenReturn(IntentService.INTENT_CURRENT_RATE);

        MarketplaceRateDto wheatRate = new MarketplaceRateDto("Wheat", "Pune", new BigDecimal("2800.00"), "Quintal", "2026-10-14");
        MarketplaceRateDto soybeanRate = new MarketplaceRateDto("Soybean", "Sangli", new BigDecimal("4250.00"), "Quintal", "2026-10-14");
        when(marketplaceDataService.fetchMarketRates()).thenReturn(List.of(soybeanRate, wheatRate));

        ChatRequest request = new ChatRequest(777L, question, "en");
        ChatResponse response = chatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("RATE", response.getDisplayType());
        assertTrue(response.getMessage().contains("No current rate record is available for Cotton in the marketplace database"),
                "Must clearly report Cotton is not in the database");
        assertTrue(response.getMessage().contains("Soybean"));
        assertTrue(response.getMessage().contains("Wheat"));
        assertFalse(response.getMessage().contains("₹4250 per Quintal for Cotton"), "Must not assign another commodity's rate to Cotton");
    }

    @Test
    @DisplayName("Date window extraction: dynamically calculates correct start and end dates from local date")
    void testDateWindowExtraction_DynamicCalculation() {
        LocalDate today = LocalDate.of(2026, 10, 14);

        AdminChatbotService.HistoricalDateWindow w7 = chatbotService.extractHistoricalDateWindow("Show me the Wheat rate for the last 7 days", today);
        assertEquals(LocalDate.of(2026, 10, 8), w7.getStartDate());
        assertEquals(LocalDate.of(2026, 10, 14), w7.getEndDate());
        assertEquals(7, w7.getRequestedDays());
        assertTrue(w7.isDaysExplicit());

        AdminChatbotService.HistoricalDateWindow w1 = chatbotService.extractHistoricalDateWindow("Show me the Wheat rate for the last 1 day", today);
        assertEquals(LocalDate.of(2026, 10, 14), w1.getStartDate());
        assertEquals(LocalDate.of(2026, 10, 14), w1.getEndDate());
        assertEquals(1, w1.getRequestedDays());
        assertTrue(w1.isDaysExplicit());

        AdminChatbotService.HistoricalDateWindow wYesterday = chatbotService.extractHistoricalDateWindow("What was yesterday's rate?", today);
        assertEquals(LocalDate.of(2026, 10, 13), wYesterday.getStartDate());
        assertEquals(LocalDate.of(2026, 10, 13), wYesterday.getEndDate());
        assertEquals(1, wYesterday.getRequestedDays());

        AdminChatbotService.HistoricalDateWindow wMarathi = chatbotService.extractHistoricalDateWindow("मागील ७ दिवसांचे दर", today);
        assertEquals(LocalDate.of(2026, 10, 8), wMarathi.getStartDate());
        assertEquals(LocalDate.of(2026, 10, 14), wMarathi.getEndDate());
        assertEquals(7, wMarathi.getRequestedDays());

        AdminChatbotService.HistoricalDateWindow wBetween = chatbotService.extractHistoricalDateWindow("Show rates between 1 October 2026 and 14 October 2026", today);
        assertEquals(LocalDate.of(2026, 10, 1), wBetween.getStartDate());
        assertEquals(LocalDate.of(2026, 10, 14), wBetween.getEndDate());
        assertEquals(14, wBetween.getRequestedDays());
    }

    @Test
    @DisplayName("Dynamic historical rates: Soybean 7-day query with valid records renders CHART, table and chartData match identically")
    void testHistoricalRates_Soybean_7Days_SufficientData_RendersChartAndTableFromSameData() {
        String question = "Show me a chart of Soybean rates for the last 7 days";
        when(intentService.detectIntent(question)).thenReturn(IntentService.INTENT_RATE_CHART);

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        List<ChartData> soybeanPoints = List.of(
                new ChartData(today.minusDays(4).toString(), new BigDecimal("4600.0"), "Soybean", "Sangli", "Quintal"),
                new ChartData(today.minusDays(3).toString(), new BigDecimal("4620.0"), "Soybean", "Sangli", "Quintal"),
                new ChartData(today.minusDays(2).toString(), new BigDecimal("4650.0"), "Soybean", "Sangli", "Quintal"),
                new ChartData(today.minusDays(1).toString(), new BigDecimal("4680.0"), "Soybean", "Sangli", "Quintal"),
                new ChartData(today.toString(), new BigDecimal("4700.0"), "Soybean", "Sangli", "Quintal")
        );
        when(marketplaceDataService.fetchHistoricalRates("Soybean", null)).thenReturn(soybeanPoints);
        when(marketplaceDataService.fetchSellerAskingPrices("Soybean", null)).thenReturn(Collections.emptyList());
        when(ragService.retrieveContext(eq(question), eq(IntentService.INTENT_RATE_CHART))).thenReturn("");
        when(ragService.isQuotaExhausted()).thenReturn(false);
        when(geminiService.generateResponse(eq(question), anyString(), anyList(), eq("English")))
                .thenReturn("An error occurred while communicating with the AI service: Timeout");

        ChatRequest request = new ChatRequest(777L, question, "en");
        ChatResponse response = chatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("CHART", response.getDisplayType(), "With >= 2 records and explicit chart request, displayType must be CHART");
        assertNotNull(response.getChartData());
        assertEquals(5, response.getChartData().size());
        assertEquals(today.minusDays(4).toString(), response.getChartData().get(0).getDate());
        assertEquals(today.toString(), response.getChartData().get(4).getDate());

        // Verify table and chart records match
        for (ChartData point : response.getChartData()) {
            assertTrue(response.getMessage().contains(point.getDate()), "Response table must contain record date " + point.getDate());
            assertTrue(response.getMessage().contains(point.getRate().stripTrailingZeros().toPlainString()), "Response table must contain rate " + point.getRate());
        }
        assertTrue(response.getMessage().contains("Average Rate (5 recorded days)"), "Must label average with actual recorded day count, not misleading 7-day average");
        assertTrue(response.getMessage().contains("not recorded in the marketplace database") || response.getMessage().contains("Missing Dates:"), "Must report missing calendar dates in requested 7-day window");
    }

    @Test
    @DisplayName("Dynamic historical rates: Wheat and Maize query dynamically without hardcoded commodity branches")
    void testHistoricalRates_WheatAndMaize_DynamicSupport() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

        // Test Wheat
        String wheatQuestion = "Show me the Wheat rate for the last 7 days";
        when(intentService.detectIntent(wheatQuestion)).thenReturn(IntentService.INTENT_RATE_TREND);
        List<ChartData> wheatPoints = List.of(
                new ChartData(today.minusDays(3).toString(), new BigDecimal("2820.0"), "Wheat", "Pune", "Quintal"),
                new ChartData(today.minusDays(1).toString(), new BigDecimal("2840.0"), "Wheat", "Pune", "Quintal")
        );
        when(marketplaceDataService.fetchHistoricalRates("Wheat", null)).thenReturn(wheatPoints);
        when(marketplaceDataService.fetchSellerAskingPrices("Wheat", null)).thenReturn(Collections.emptyList());
        when(ragService.retrieveContext(eq(wheatQuestion), eq(IntentService.INTENT_RATE_TREND))).thenReturn("");
        when(geminiService.generateResponse(eq(wheatQuestion), anyString(), anyList(), eq("English")))
                .thenReturn("An error occurred while communicating with the AI service: Timeout");

        ChatRequest wheatRequest = new ChatRequest(777L, wheatQuestion, "en");
        ChatResponse wheatResponse = chatbotService.processChat(wheatRequest);

        assertNotNull(wheatResponse);
        assertEquals("RATE", wheatResponse.getDisplayType(), "RATE_TREND intent defaults to RATE displayType");
        assertEquals(2, wheatResponse.getChartData().size());
        assertTrue(wheatResponse.getMessage().contains("Wheat"));
        assertTrue(wheatResponse.getMessage().contains("Pune"));
        assertTrue(wheatResponse.getMessage().contains("2820"));
        assertTrue(wheatResponse.getMessage().contains("2840"));

        // Test Maize
        String maizeQuestion = "Show me the MAIZE rate for the last 7 days";
        when(intentService.detectIntent(maizeQuestion)).thenReturn(IntentService.INTENT_RATE_TREND);
        List<ChartData> maizePoints = List.of(
                new ChartData(today.minusDays(2).toString(), new BigDecimal("2230.0"), "Maize", "Sangli", "Quintal"),
                new ChartData(today.minusDays(1).toString(), new BigDecimal("2250.0"), "Maize", "Sangli", "Quintal")
        );
        when(marketplaceDataService.fetchHistoricalRates("Maize", null)).thenReturn(maizePoints);
        when(marketplaceDataService.fetchSellerAskingPrices("Maize", null)).thenReturn(Collections.emptyList());
        when(ragService.retrieveContext(eq(maizeQuestion), eq(IntentService.INTENT_RATE_TREND))).thenReturn("");
        when(geminiService.generateResponse(eq(maizeQuestion), anyString(), anyList(), eq("English")))
                .thenReturn("An error occurred while communicating with the AI service: Timeout");

        ChatRequest maizeRequest = new ChatRequest(777L, maizeQuestion, "en");
        ChatResponse maizeResponse = chatbotService.processChat(maizeRequest);

        assertNotNull(maizeResponse);
        assertEquals(2, maizeResponse.getChartData().size());
        assertTrue(maizeResponse.getMessage().contains("Maize"));
        assertTrue(maizeResponse.getMessage().contains("Sangli"));
        assertTrue(maizeResponse.getMessage().contains("2230"));
        assertTrue(maizeResponse.getMessage().contains("2250"));
    }

    @Test
    @DisplayName("Historical rates: MAIZEW with 0 historical records explains no history and shows active seller asking prices separately")
    void testHistoricalRates_MaizeW_NoHistoricalRecords_ShowsSellerAskingPricesSeparately() {
        String question = "Show me the MAIZEW rate for the last 7 days";
        when(intentService.detectIntent(question)).thenReturn(IntentService.INTENT_RATE_TREND);

        when(marketplaceDataService.fetchHistoricalRates("MAIZEW", null)).thenReturn(Collections.emptyList());
        List<ChartData> askingPrices = List.of(
                new ChartData("2026-11-30", new BigDecimal("22500.0"), "MAIZEW", "Kolhapur", "MT")
        );
        when(marketplaceDataService.fetchSellerAskingPrices("MAIZEW", null)).thenReturn(askingPrices);
        when(ragService.retrieveContext(eq(question), eq(IntentService.INTENT_RATE_TREND))).thenReturn("");
        when(geminiService.generateResponse(eq(question), anyString(), anyList(), eq("English")))
                .thenReturn("An error occurred while communicating with the AI service: Timeout");

        ChatRequest request = new ChatRequest(777L, question, "en");
        ChatResponse response = chatbotService.processChat(request);

        assertNotNull(response);
        assertNull(response.getDisplayType(), "Zero historical records must NOT have a chart display");
        assertNotNull(response.getChartData());
        assertTrue(response.getChartData().isEmpty(), "Historical chartData must be empty; asking prices must NOT be mixed into historical chartData");

        // Verify response message explains lack of historical data and presents seller asking price separately
        assertTrue(response.getMessage().contains("No MAIZEW historical rates are recorded")
                || response.getMessage().contains("No historical rate records are currently available for MAIZEW"));
        assertTrue(response.getMessage().contains("Active Seller Asking Prices"),
                "Active asking prices must be presented separately");
        assertTrue(response.getMessage().contains("Seller Asking Price"),
                "Must be explicitly labelled as Seller Asking Price");
        assertTrue(response.getMessage().contains("22500") || response.getMessage().contains("22,500"));
        assertTrue(response.getMessage().contains("Kolhapur"));
        assertTrue(response.getMessage().contains("MT"));
    }

    @Test
    @DisplayName("Dynamic commodity support: newly added commodity (Barley) recognized dynamically without commodity-specific code")
    void testHistoricalRates_NewlyAddedCommodity_RecognizedWithoutCommoditySpecificCode() {
        String question = "Show me the Barley rate for the last 7 days";
        when(intentService.detectIntent(question)).thenReturn(IntentService.INTENT_RATE_TREND);

        lenient().when(marketplaceDataService.fetchKnownCommodityNames()).thenReturn(java.util.Set.of("Barley"));
        lenient().when(marketplaceDataService.resolveCanonicalCommodityName("Barley")).thenReturn("Barley");

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        List<ChartData> barleyPoints = List.of(
                new ChartData(today.minusDays(2).toString(), new BigDecimal("1950.0"), "Barley", "Jaipur", "Quintal"),
                new ChartData(today.minusDays(1).toString(), new BigDecimal("1980.0"), "Barley", "Jaipur", "Quintal")
        );
        when(marketplaceDataService.fetchHistoricalRates("Barley", null)).thenReturn(barleyPoints);
        when(marketplaceDataService.fetchSellerAskingPrices("Barley", null)).thenReturn(Collections.emptyList());
        when(ragService.retrieveContext(eq(question), eq(IntentService.INTENT_RATE_TREND))).thenReturn("");
        when(geminiService.generateResponse(eq(question), anyString(), anyList(), eq("English")))
                .thenReturn("An error occurred while communicating with the AI service: Timeout");

        ChatRequest request = new ChatRequest(777L, question, "en");
        ChatResponse response = chatbotService.processChat(request);

        assertNotNull(response);
        assertEquals(List.of("Barley"), response.getCommodities());
        assertEquals(2, response.getChartData().size());
        assertEquals("Barley", response.getChartData().get(0).getCommodity());
        assertTrue(response.getMessage().contains("Barley"));
        assertTrue(response.getMessage().contains("Jaipur"));
        assertTrue(response.getMessage().contains("1950"));
        assertTrue(response.getMessage().contains("1980"));
    }

    @Test
    @DisplayName("Historical rates: future-dated records excluded from historical window and from latest past rate")
    void testHistoricalRates_FutureDatedRecords_ExcludedFromHistoricalWindowAndLatestPastRate() {
        String question = "Show me the Wheat rate for the last 7 days";
        when(intentService.detectIntent(question)).thenReturn(IntentService.INTENT_RATE_TREND);

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        // Backend returns an old record (outside window) and a future-dated record (e.g. next month)
        List<ChartData> records = List.of(
                new ChartData(today.minusDays(20).toString(), new BigDecimal("2750.0"), "Wheat", "Pune", "Quintal"),
                new ChartData(today.plusDays(10).toString(), new BigDecimal("3500.0"), "Wheat", "Sangli", "Quintal")
        );
        when(marketplaceDataService.fetchHistoricalRates("Wheat", null)).thenReturn(records);
        when(marketplaceDataService.fetchSellerAskingPrices("Wheat", null)).thenReturn(Collections.emptyList());
        when(ragService.retrieveContext(eq(question), eq(IntentService.INTENT_RATE_TREND))).thenReturn("");
        when(geminiService.generateResponse(eq(question), anyString(), anyList(), eq("English")))
                .thenReturn("An error occurred while communicating with the AI service: Timeout");

        ChatRequest request = new ChatRequest(777L, question, "en");
        ChatResponse response = chatbotService.processChat(request);

        assertNotNull(response);
        assertNull(response.getDisplayType());
        assertTrue(response.getChartData().isEmpty(), "No records inside the 7-day past window");

        // The future-dated record must NOT be presented as the latest historical rate
        assertFalse(response.getMessage().contains(today.plusDays(10).toString()), "Future date must never appear as latest past rate");
        assertFalse(response.getMessage().contains("3500"), "Future rate must never appear as latest past rate");
        // The genuine past record (today - 20 days) can be mentioned as latest available record outside window
        assertTrue(response.getMessage().contains(today.minusDays(20).toString()), "Past record should be referenced");
        assertTrue(response.getMessage().contains("2750"));
    }

    @Test
    @DisplayName("Historical rates: sparse data reports missing dates and does not call average a 7-day average")
    void testHistoricalRates_SparseData_ReportsMissingDates_DoesNotCall7DayAverage() {
        String question = "Show me the Soybean rate for the last 7 days";
        when(intentService.detectIntent(question)).thenReturn(IntentService.INTENT_RATE_TREND);

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        List<ChartData> sparsePoints = List.of(
                new ChartData(today.minusDays(4).toString(), new BigDecimal("4600.0"), "Soybean", "Sangli", "Quintal"),
                new ChartData(today.minusDays(1).toString(), new BigDecimal("4700.0"), "Soybean", "Sangli", "Quintal")
        );
        when(marketplaceDataService.fetchHistoricalRates("Soybean", null)).thenReturn(sparsePoints);
        when(marketplaceDataService.fetchSellerAskingPrices("Soybean", null)).thenReturn(Collections.emptyList());
        when(ragService.retrieveContext(eq(question), eq(IntentService.INTENT_RATE_TREND))).thenReturn("");
        when(geminiService.generateResponse(eq(question), anyString(), anyList(), eq("English")))
                .thenReturn("An error occurred while communicating with the AI service: Timeout");

        ChatRequest request = new ChatRequest(777L, question, "en");
        ChatResponse response = chatbotService.processChat(request);

        assertNotNull(response);
        assertEquals(2, response.getChartData().size());
        assertTrue(response.getMessage().contains("Average Rate (2 recorded days)"));
        assertFalse(response.getMessage().contains("7-day average"), "Must not call sparse average a 7-day average");
        assertTrue(response.getMessage().contains("not recorded in the marketplace database") || response.getMessage().contains("Missing Dates:"));
        assertTrue(response.getMessage().contains(today.minusDays(6).toString()));
        assertTrue(response.getMessage().contains("through"));
    }

    @Test
    @DisplayName("Historical rates: single record does not render misleading trend chart and explains minimum points required")
    void testHistoricalRates_SingleRecord_DoesNotRenderTrendChart() {
        String question = "Show me a chart of Soybean rates for the last 7 days";
        when(intentService.detectIntent(question)).thenReturn(IntentService.INTENT_RATE_CHART);

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        List<ChartData> singlePoint = List.of(
                new ChartData(today.minusDays(1).toString(), new BigDecimal("4650.0"), "Soybean", "Sangli", "Quintal")
        );
        when(marketplaceDataService.fetchHistoricalRates("Soybean", null)).thenReturn(singlePoint);
        when(marketplaceDataService.fetchSellerAskingPrices("Soybean", null)).thenReturn(Collections.emptyList());
        when(ragService.retrieveContext(eq(question), eq(IntentService.INTENT_RATE_CHART))).thenReturn("");
        when(geminiService.generateResponse(eq(question), anyString(), anyList(), eq("English")))
                .thenReturn("An error occurred while communicating with the AI service: Timeout");

        ChatRequest request = new ChatRequest(777L, question, "en");
        ChatResponse response = chatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("RATE", response.getDisplayType(), "Single point must NOT have CHART displayType");
        assertEquals(1, response.getChartData().size());
        assertTrue(response.getMessage().contains("At least two genuine historical records are required to plot a price trend chart")
                || response.getMessage().contains("at least 2 historical"));
    }
}
