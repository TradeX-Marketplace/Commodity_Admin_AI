package com.example.Commodity_Admin_AI.service;

import com.example.Commodity_Admin_AI.ai.GeminiService;
import com.example.Commodity_Admin_AI.ai.RagService;
import com.example.Commodity_Admin_AI.dto.ChatRequest;
import com.example.Commodity_Admin_AI.dto.ChatResponse;
import com.example.Commodity_Admin_AI.dto.MarketplaceDocument;
import com.example.Commodity_Admin_AI.dto.OrderDto;
import com.example.Commodity_Admin_AI.dto.OrderFilterDto;
import com.example.Commodity_Admin_AI.entity.ChatMessage;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrdersFilteringAndChatbotTest {

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

    @Mock
    private MarketplaceApiClient marketplaceApiClient;

    private AdminChatbotService adminChatbotService;
    private ChatSession testSession;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        testSession = new ChatSession("admin");
        testSession.setId(101L);

        adminChatbotService = new AdminChatbotService(
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
    @DisplayName("Filter Extraction: date-specific order query '14 October 2026'")
    void testExtractOrderFilter_SpecificDate() {
        String q = "Show orders placed on 14 October 2026.";
        OrderFilterDto filter = adminChatbotService.extractOrderFilter(q, "ENGLISH");

        assertEquals("2026-10-14", filter.getStartDate());
        assertEquals("2026-10-14", filter.getEndDate());
        assertFalse(filter.isClarificationNeeded());
    }

    @Test
    @DisplayName("Filter Extraction: relative date 'yesterday' uses local calendar date")
    void testExtractOrderFilter_Yesterday() {
        String q = "What orders were delivered to Kolhapur yesterday?";
        OrderFilterDto filter = adminChatbotService.extractOrderFilter(q, "ENGLISH");

        LocalDate expectedYesterday = today.minusDays(1);
        assertEquals(expectedYesterday.toString(), filter.getStartDate());
        assertEquals(expectedYesterday.toString(), filter.getEndDate());
        assertEquals("Kolhapur", filter.getDeliveryLocation());
        assertFalse(filter.isClarificationNeeded());
    }

    @Test
    @DisplayName("Filter Extraction: relative date 'this month' covers current month range")
    void testExtractOrderFilter_ThisMonth() {
        String q = "Show all Soybean orders from this month.";
        OrderFilterDto filter = adminChatbotService.extractOrderFilter(q, "ENGLISH");

        LocalDate startOfMonth = today.withDayOfMonth(1);
        LocalDate endOfMonth = today.withDayOfMonth(today.lengthOfMonth());
        assertEquals(startOfMonth.toString(), filter.getStartDate());
        assertEquals(endOfMonth.toString(), filter.getEndDate());
        assertEquals("Soybean", filter.getCommodityName());
        assertFalse(filter.isClarificationNeeded());
    }

    @Test
    @DisplayName("Filter Extraction: 'Show all orders from this month' covers month range without setting buyerName to 'this month'")
    void testExtractOrderFilter_AllOrdersFromThisMonth_DoesNotParseMonthAsBuyerName() {
        String q = "Show all orders from this month";
        OrderFilterDto filter = adminChatbotService.extractOrderFilter(q, "ENGLISH");

        LocalDate startOfMonth = today.withDayOfMonth(1);
        LocalDate endOfMonth = today.withDayOfMonth(today.lengthOfMonth());
        assertEquals(startOfMonth.toString(), filter.getStartDate());
        assertEquals(endOfMonth.toString(), filter.getEndDate());
        assertNull(filter.getBuyerName(), "Buyer name must NOT be 'this month'");
        assertNull(filter.getCommodityName());
        assertFalse(filter.isClarificationNeeded());
    }

    @Test
    @DisplayName("Filter Extraction: partial buyer name 'Vishal Patil'")
    void testExtractOrderFilter_BuyerName() {
        String q = "Do we have any orders from Vishal Patil?";
        OrderFilterDto filter = adminChatbotService.extractOrderFilter(q, "ENGLISH");

        assertEquals("Vishal Patil", filter.getBuyerName());
        assertFalse(filter.isClarificationNeeded());
    }

    @Test
    @DisplayName("Filter Extraction: cancelled orders from Sangli")
    void testExtractOrderFilter_StatusAndLocation() {
        String q = "Show cancelled orders from Sangli.";
        OrderFilterDto filter = adminChatbotService.extractOrderFilter(q, "ENGLISH");

        assertEquals("CANCELLED", filter.getStatus());
        assertEquals("Sangli", filter.getMarketLocation());
        assertFalse(filter.isClarificationNeeded());
    }

    @Test
    @DisplayName("Filter Extraction: date range between 1 October and 14 October 2026")
    void testExtractOrderFilter_DateRange() {
        String q = "Show orders between 1 October and 14 October 2026.";
        OrderFilterDto filter = adminChatbotService.extractOrderFilter(q, "ENGLISH");

        assertEquals("2026-10-01", filter.getStartDate());
        assertEquals("2026-10-14", filter.getEndDate());
        assertFalse(filter.isClarificationNeeded());
    }

    @Test
    @DisplayName("Filter Extraction: quantity above 100 Quintals")
    void testExtractOrderFilter_QuantityAbove() {
        String q = "Show orders with quantity above 100 Quintals.";
        OrderFilterDto filter = adminChatbotService.extractOrderFilter(q, "ENGLISH");

        assertNotNull(filter.getMinQuantity());
        assertEquals(new BigDecimal("100"), filter.getMinQuantity());
        assertFalse(filter.isClarificationNeeded());
    }

    @Test
    @DisplayName("Filter Extraction: unit price between 4000 and 4500 per unit")
    void testExtractOrderFilter_PriceRange() {
        String q = "Show orders priced between 4000 and 4500 per unit.";
        OrderFilterDto filter = adminChatbotService.extractOrderFilter(q, "ENGLISH");

        assertNotNull(filter.getMinPrice());
        assertNotNull(filter.getMaxPrice());
        assertEquals(new BigDecimal("4000"), filter.getMinPrice());
        assertEquals(new BigDecimal("4500"), filter.getMaxPrice());
        assertFalse(filter.isClarificationNeeded());
    }

    @Test
    @DisplayName("Filter Extraction: total value aggregation query")
    void testExtractOrderFilter_TotalValue() {
        String q = "What is the total value of Vishal Patil's orders?";
        OrderFilterDto filter = adminChatbotService.extractOrderFilter(q, "ENGLISH");

        assertTrue(filter.isTotalValueCalculation());
        assertEquals("Vishal Patil", filter.getBuyerName());
        assertFalse(filter.isClarificationNeeded());
    }

    @Test
    @DisplayName("Filter Extraction: ambiguous date triggers clarification question without guessing")
    void testExtractOrderFilter_AmbiguousDateClarification() {
        String q = "Show recent orders from last week.";
        OrderFilterDto filter = adminChatbotService.extractOrderFilter(q, "ENGLISH");

        assertTrue(filter.isClarificationNeeded());
        assertNotNull(filter.getClarificationPrompt());
        assertTrue(filter.getClarificationPrompt().contains("specify the exact date or date range"));
    }

    @Test
    @DisplayName("Chatbot Execution: total value calculation uses BigDecimal arithmetic")
    void testProcessChat_TotalValueCalculation_UsesBigDecimal() {
        when(chatSessionRepository.save(any())).thenReturn(testSession);
        when(intentService.detectIntent(anyString())).thenReturn(IntentService.INTENT_ORDERS);
        when(marketplaceDataService.isConfigured()).thenReturn(true);

        OrderDto o1 = new OrderDto(1L, "Vishal Patil", "+91-9876543210", "Soybean",
                new BigDecimal("120.00"), "Quintal", new BigDecimal("4250.00"), new BigDecimal("510000.00"),
                "Sangli", "Kolhapur", "CONFIRMED", "2026-10-14T09:30:00");
        OrderDto o2 = new OrderDto(2L, "Vishal Patil", "+91-9876543210", "Soybean",
                new BigDecimal("80.00"), "Quintal", new BigDecimal("4200.00"), new BigDecimal("336000.00"),
                "Sangli", "Kolhapur", "COMPLETED", "2026-10-13T14:15:00");

        when(marketplaceDataService.fetchOrders(any(OrderFilterDto.class))).thenReturn(List.of(o1, o2));

        ChatRequest request = new ChatRequest("What is the total value of Vishal Patil's orders?");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("ORDERS", response.getDisplayType());
        assertTrue(response.getMessage().contains("₹846000.00 INR"));
        assertTrue(response.getMessage().contains("across 2 orders"));
        // Privacy: phone number must NOT be exposed in output
        assertFalse(response.getMessage().contains("+91-9876543210"));
        assertNotNull(response.getOrders());
        assertEquals(2, response.getOrders().size());
    }

    @Test
    @DisplayName("Chatbot Execution: matching orders return table of permitted fields with NO buyer phone")
    void testProcessChat_MatchingOrders_PrivacyProtected() {
        when(chatSessionRepository.save(any())).thenReturn(testSession);
        when(intentService.detectIntent(anyString())).thenReturn(IntentService.INTENT_ORDERS);
        when(marketplaceDataService.isConfigured()).thenReturn(true);

        OrderDto o = new OrderDto(51L, "Amit Deshmukh", "+91-9822012345", "Soybean",
                new BigDecimal("150.00"), "Quintal", new BigDecimal("4300.00"), new BigDecimal("645000.00"),
                "Sangli", "Pune", "CANCELLED", "2026-10-10T11:00:00");

        when(marketplaceDataService.fetchOrders(any(OrderFilterDto.class))).thenReturn(List.of(o));

        ChatRequest request = new ChatRequest("Show cancelled orders from Sangli.");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("ORDERS", response.getDisplayType());
        assertTrue(response.getMessage().contains("Found 1 matching Soybean order"));
        assertTrue(response.getMessage().contains("Amit Deshmukh"));
        assertTrue(response.getMessage().contains("CANCELLED"));
        assertTrue(response.getMessage().contains("645000.00"));
        // Privacy check: buyer phone MUST NOT be exposed in chatbot response
        assertFalse(response.getMessage().contains("+91-9822012345"));
        assertFalse(response.getMessage().contains("9822012345"));
    }

    @Test
    @DisplayName("Chatbot Execution: empty results return standardized message")
    void testProcessChat_EmptyResults() {
        when(chatSessionRepository.save(any())).thenReturn(testSession);
        when(intentService.detectIntent(anyString())).thenReturn(IntentService.INTENT_ORDERS);
        when(marketplaceDataService.isConfigured()).thenReturn(true);
        when(marketplaceDataService.fetchOrders(any(OrderFilterDto.class))).thenReturn(Collections.emptyList());

        ChatRequest request = new ChatRequest("Show orders for Chana in Nagpur.");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("No orders matched those filters.", response.getMessage());
    }

    @Test
    @DisplayName("Chatbot Execution: marketplace backend API failure returns exact required error message")
    void testProcessChat_ApiFailure_ReturnsClearError() {
        when(chatSessionRepository.save(any())).thenReturn(testSession);
        when(intentService.detectIntent(anyString())).thenReturn(IntentService.INTENT_ORDERS);
        when(marketplaceDataService.isConfigured()).thenReturn(true);
        when(marketplaceDataService.fetchOrders(any(OrderFilterDto.class)))
                .thenThrow(new RuntimeException("Connection refused: connect"));

        ChatRequest request = new ChatRequest("Show orders placed today");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("I couldn't retrieve the order records because the Marketplace Backend request failed. Please try again.",
                response.getMessage());
    }

    @Test
    @DisplayName("Multilingual: Marathi query returns localized response")
    void testProcessChat_MultilingualMarathi() {
        when(chatSessionRepository.save(any())).thenReturn(testSession);
        when(intentService.detectIntent(anyString())).thenReturn(IntentService.INTENT_ORDERS);
        when(marketplaceDataService.isConfigured()).thenReturn(true);

        OrderDto o = new OrderDto(1L, "Vishal Patil", "+91-9876543210", "Soybean",
                new BigDecimal("120.00"), "Quintal", new BigDecimal("4250.00"), new BigDecimal("510000.00"),
                "Sangli", "Kolhapur", "CONFIRMED", "2026-10-14T09:30:00");

        when(marketplaceDataService.fetchOrders(any(OrderFilterDto.class))).thenReturn(List.of(o));

        ChatRequest request = new ChatRequest("विशाल पाटील यांच्या ऑर्डर्स दाखवा");
        request.setLanguage("MARATHI");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertTrue(response.getMessage().contains("मार्केटप्लेसच्या नोंदीनुसार"));
        assertTrue(response.getMessage().contains("Vishal Patil"));
        assertFalse(response.getMessage().contains("+91-9876543210"));
    }

    @Test
    @DisplayName("Multilingual: Hindi query returns localized empty state")
    void testProcessChat_MultilingualHindi_EmptyState() {
        when(chatSessionRepository.save(any())).thenReturn(testSession);
        when(intentService.detectIntent(anyString())).thenReturn(IntentService.INTENT_ORDERS);
        when(marketplaceDataService.isConfigured()).thenReturn(true);
        when(marketplaceDataService.fetchOrders(any(OrderFilterDto.class))).thenReturn(Collections.emptyList());

        ChatRequest request = new ChatRequest("14 अक्टूबर 2026 के आर्डर दिखाएं");
        request.setLanguage("HINDI");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("उन फ़िल्टर के अनुसार कोई ऑर्डर नहीं मिले।", response.getMessage());
    }

    @Test
    @DisplayName("PGVector Document Conversion: does NOT index customer phone number and references order ID")
    void testConvertOrderToSearchableDocument_OmitsPhone() {
        MarketplaceDataService service = new MarketplaceDataService(null, "http://localhost:7070", "/api/ai/market-rates", null, null);
        OrderDto o = new OrderDto(49L, "Vishal Patil", "+91-9876543210", "Soybean",
                new BigDecimal("120.00"), "Quintal", new BigDecimal("4250.00"), new BigDecimal("510000.00"),
                "Sangli", "Kolhapur", "CONFIRMED", "2026-10-14T09:30:00");

        MarketplaceDocument doc = service.convertOrderToSearchableDocument(o);
        assertNotNull(doc);
        assertEquals(49L, doc.getMetadata().get("orderId"));
        assertEquals("orders", doc.getMetadata().get("source"));
        assertEquals("ORDER", doc.getMetadata().get("category"));
        assertEquals("Soybean", doc.getMetadata().get("commodity"));
        // Privacy: phone number MUST NOT be in content or metadata
        assertFalse(doc.getContent().contains("+91-9876543210"));
        assertNull(doc.getMetadata().get("buyerPhone"));
        assertNull(doc.getMetadata().get("phone"));
    }

    @Test
    @DisplayName("Chatbot Execution: buyer name query 'Show Vishal Patil''s orders' passes buyerName filter and returns correct summary")
    void testProcessChat_BuyerFilter_VishalPatil() {
        when(chatSessionRepository.save(any())).thenReturn(testSession);
        when(intentService.detectIntent(anyString())).thenReturn(IntentService.INTENT_ORDERS);
        when(marketplaceDataService.isConfigured()).thenReturn(true);

        OrderDto o1 = new OrderDto(49L, "Vishal Patil", "+91-9876543210", "Soybean",
                new BigDecimal("120.00"), "Quintal", new BigDecimal("4250.00"), new BigDecimal("510000.00"),
                "Sangli", "Kolhapur", "CONFIRMED", "2026-10-14T09:30:00");
        OrderDto o2 = new OrderDto(50L, "Vishal Patil", "+91-9876543210", "Soybean",
                new BigDecimal("80.00"), "Quintal", new BigDecimal("4200.00"), new BigDecimal("336000.00"),
                "Sangli", "Kolhapur", "COMPLETED", "2026-10-13T14:15:00");

        when(marketplaceDataService.fetchOrders(any(OrderFilterDto.class))).thenReturn(List.of(o1, o2));

        ChatRequest request = new ChatRequest("Show Vishal Patil's orders");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("Vishal Patil", response.getBuyerName());
        assertEquals("ORDERS", response.getDisplayType());
        assertTrue(response.getMessage().contains("Found 2 matching orders for Vishal Patil."));
        assertEquals(2, response.getOrders().size());
        // Verify privacy: phone number is not in message text
        assertFalse(response.getMessage().contains("+91-9876543210"));
    }
}
