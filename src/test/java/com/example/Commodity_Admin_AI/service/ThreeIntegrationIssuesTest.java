package com.example.Commodity_Admin_AI.service;

import com.example.Commodity_Admin_AI.dto.ChartData;
import com.example.Commodity_Admin_AI.dto.ChatRequest;
import com.example.Commodity_Admin_AI.dto.ChatResponse;
import com.example.Commodity_Admin_AI.dto.MarketplaceRateDto;
import com.example.Commodity_Admin_AI.dto.OrderFilterDto;
import com.example.Commodity_Admin_AI.entity.ChatSession;
import com.example.Commodity_Admin_AI.marketplaceAllAPi.MarketplaceApiClient;
import com.example.Commodity_Admin_AI.repository.ChatMessageRepository;
import com.example.Commodity_Admin_AI.repository.ChatSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThreeIntegrationIssuesTest {

    @Mock
    private MarketplaceApiClient mockApiClient;

    @Mock
    private JdbcTemplate mockJdbcTemplate;

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    private MarketplaceDataService dataService;
    private IntentService intentService;
    private AdminChatbotService chatbotService;
    private ChatSession testSession;

    @BeforeEach
    void setUp() {
        dataService = new MarketplaceDataService(
                RestClient.builder(),
                "http://localhost:8080",
                "/api/ai/market-rates",
                "",
                mockApiClient,
                mockJdbcTemplate
        );

        intentService = new IntentService();

        chatbotService = new AdminChatbotService(
                chatSessionRepository,
                chatMessageRepository,
                intentService,
                null, // RagService mock
                null, // GeminiService mock not needed for direct structured paths
                dataService,
                10    // maxHistoryMessages
        );

        testSession = new ChatSession("test-user");
        testSession.setId(101L);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Issue 1 Tests: Current Soybean Rate
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Issue 1: When market-rates endpoint is unsupported, derive current rate from live active supplies")
    void testIssue1_DeriveMarketRateFromLiveSupplies() {
        when(mockApiClient.getMarketRates())
                .thenThrow(new UnsupportedOperationException("Tradex has no market-rates endpoint"));

        String suppliesJson = "{\"success\":true,\"data\":[{" +
                "\"id\":1," +
                "\"supplyNumber\":\"SUP-1792400860244\"," +
                "\"commodityName\":\"Soybean\"," +
                "\"askingRate\":4500.0," +
                "\"location\":\"Sangli\"," +
                "\"status\":\"ACTIVE\"," +
                "\"createdAt\":\"2026-10-19T14:37:40\"" +
                "}]}";
        when(mockApiClient.getSupplies()).thenReturn(suppliesJson);

        List<MarketplaceRateDto> rates = dataService.fetchMarketRates();
        assertNotNull(rates);
        assertEquals(1, rates.size());
        assertEquals("Soybean", rates.get(0).getCommodityName());
        assertEquals("Sangli", rates.get(0).getLocation());
        assertEquals(new BigDecimal("4500.0"), rates.get(0).getRate());
        assertEquals("2026-10-19", rates.get(0).getRateDate());

        // Test chatbot handles current rate query using this live derived rate
        when(chatSessionRepository.save(any())).thenReturn(testSession);
        ChatRequest req = new ChatRequest("What is the current rate of Soybean?");
        ChatResponse res = chatbotService.processChat(req);

        assertNotNull(res);
        assertEquals("RATE", res.getDisplayType());
        assertTrue(res.getMessage().contains("4500"), "Response should reflect live asking rate 4500");
        assertTrue(res.getMessage().contains("Sangli"), "Response should reflect live location Sangli");
        assertFalse(res.getMessage().contains("4285"), "Response must NOT confuse live rate with stale vector store rate 4285");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Issue 2 Tests: Last Seven Days Soybean Rate Query & Historical Data Flow
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Issue 2: When rate-history endpoint is unsupported, fetch historical rates from database fallback")
    void testIssue2_FetchHistoricalRatesFromDatabaseFallback() {
        when(mockApiClient.getRateHistory(any(), any()))
                .thenThrow(new UnsupportedOperationException("Tradex has no rate-history endpoint"));

        ChartData cd1 = new ChartData("2026-10-14", new BigDecimal("4285.0"), "Soybean", "Sangli", "Quintal");
        ChartData cd2 = new ChartData("2026-10-15", new BigDecimal("4250.0"), "Soybean", "Sangli", "Quintal");

        when(mockJdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(cd1, cd2));

        List<ChartData> history = dataService.fetchHistoricalRates("Soybean", "Sangli");
        assertNotNull(history);
        assertEquals(2, history.size());
        assertEquals("2026-10-14", history.get(0).getDate());
        assertEquals(new BigDecimal("4285.0"), history.get(0).getRate());
        assertEquals("2026-10-15", history.get(1).getDate());
        assertEquals(new BigDecimal("4250.0"), history.get(1).getRate());
    }

    @Test
    @DisplayName("Issue 2: Chatbot historical rate flow accurately includes Oct 14-15 and reports Oct 16-20 missing")
    void testIssue2_ChatbotHistoricalRateWindowFlow() {
        when(mockApiClient.getRateHistory(any(), any()))
                .thenThrow(new UnsupportedOperationException("Tradex has no rate-history endpoint"));

        ChartData cd1 = new ChartData("2026-10-14", new BigDecimal("4285.0"), "Soybean", "Sangli", "Quintal");
        ChartData cd2 = new ChartData("2026-10-15", new BigDecimal("4250.0"), "Soybean", "Sangli", "Quintal");

        when(mockJdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(cd1, cd2));

        LocalDate today = LocalDate.of(2026, 10, 20);
        AdminChatbotService.HistoricalDateWindow window = new AdminChatbotService.HistoricalDateWindow(
                today.minusDays(6), today, 7, false); // 2026-10-14 to 2026-10-20

        List<ChartData> raw = dataService.fetchHistoricalRates("Soybean", null);
        List<ChartData> filtered = chatbotService.filterChartDataByWindow(raw, window);
        assertEquals(2, filtered.size());

        List<LocalDate> missing = chatbotService.computeMissingDates(filtered, window);
        assertEquals(5, missing.size()); // 16, 17, 18, 19, 20
        assertEquals(LocalDate.of(2026, 10, 16), missing.get(0));
        assertEquals(LocalDate.of(2026, 10, 20), missing.get(4));

        String response = chatbotService.generateHistoricalRateResponse(filtered, "English", "Soybean", "Sangli", window, missing, null);
        assertNotNull(response);
        assertTrue(response.contains("2026-10-14"));
        assertTrue(response.contains("4285"));
        assertTrue(response.contains("2026-10-15"));
        assertTrue(response.contains("4250"));
        assertTrue(response.contains("2026-10-16 through 2026-10-20"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Issue 3 Tests: Orders Query, Unsupported Endpoint vs Connection Failure
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Issue 3: When Orders API is unsupported by backend, chatbot explains placed orders are unavailable")
    void testIssue3_OrdersUnsupported_ReturnsClearUnavailableResponse() {
        when(chatSessionRepository.save(any())).thenReturn(testSession);
        when(mockApiClient.getOrders(any()))
                .thenThrow(new UnsupportedOperationException("Tradex models trading as Deals and Contracts, no orders endpoint"));

        ChatRequest request = new ChatRequest("Show today orders");
        ChatResponse response = chatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("ORDERS", response.getDisplayType());
        assertTrue(response.getMessage().contains("placed order data"),
                "Response should explain placed orders are unavailable");
        assertTrue(response.getMessage().contains("does not currently expose an Orders API"),
                "Response should explicitly state marketplace backend does not expose Orders API");
        assertFalse(response.getMessage().contains("request failed. Please try again"),
                "Unsupported endpoint must NOT be reported as a connection failure");
    }

    @Test
    @DisplayName("Issue 3: When Marketplace Backend connection fails, chatbot returns connection failure message")
    void testIssue3_OrdersConnectionFailure_ReturnsApiError() {
        when(chatSessionRepository.save(any())).thenReturn(testSession);
        when(mockApiClient.getOrders(any()))
                .thenThrow(new RuntimeException("Connection refused: connect"));

        ChatRequest request = new ChatRequest("Show today orders");
        ChatResponse response = chatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("ORDERS", response.getDisplayType());
        assertEquals("I couldn't retrieve the order records because the Marketplace Backend request failed. Please try again.",
                response.getMessage());
    }

    @Test
    @DisplayName("Issue 3: When Orders API successfully returns empty list, chatbot returns empty state")
    void testIssue3_OrdersEmptyList_ReturnsEmptyState() {
        when(chatSessionRepository.save(any())).thenReturn(testSession);
        when(mockApiClient.getOrders(any())).thenReturn("[]");

        ChatRequest request = new ChatRequest("Show today orders");
        ChatResponse response = chatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("ORDERS", response.getDisplayType());
        assertEquals("No orders matched those filters.", response.getMessage());
    }
}
