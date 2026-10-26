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
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verification test suite for Synthetic Orders and Chatbot Integration in Commodity_Admin_AI.
 * Validates SyntheticOrderDataProvider, MarketplaceDataService delegation toggle,
 * chatbot question routing, table generation, total value calculation, multilingual support,
 * and strict privacy protection (no phone numbers in output).
 */
@ExtendWith(MockitoExtension.class)
class SyntheticOrdersAndChatbotVerificationTest {

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
    private MarketplaceApiClient marketplaceApiClient;

    private SyntheticOrderDataProvider syntheticOrderDataProvider;
    private MarketplaceDataService marketplaceDataService;
    private AdminChatbotService adminChatbotService;
    private ChatSession testSession;
    private LocalDate today;
    private LocalDate yesterday;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        yesterday = today.minusDays(1);

        testSession = new ChatSession("admin");
        testSession.setId(501L);

        syntheticOrderDataProvider = new SyntheticOrderDataProvider();

        // MarketplaceDataService with mocked client (which mimics Tradex throwing UnsupportedOperationException on orders)
        marketplaceDataService = new MarketplaceDataService(
                RestClient.builder(),
                "http://localhost:8080",
                "/api/ai/market-rates",
                "",
                marketplaceApiClient
        );
        marketplaceDataService.setSyntheticOrderDataProvider(syntheticOrderDataProvider);
        marketplaceDataService.setSyntheticOrdersEnabled(true);

        lenient().when(marketplaceApiClient.getOrders(any())).thenThrow(
                new UnsupportedOperationException("Tradex models commercial transactions as Deals/Contracts.")
        );
        lenient().when(marketplaceApiClient.getOrders()).thenThrow(
                new UnsupportedOperationException("Tradex models commercial transactions as Deals/Contracts.")
        );
        lenient().when(marketplaceApiClient.getOrderById(any())).thenThrow(
                new UnsupportedOperationException("Tradex models commercial transactions as Deals/Contracts.")
        );

        adminChatbotService = new AdminChatbotService(
                chatSessionRepository,
                chatMessageRepository,
                intentService,
                ragService,
                geminiService,
                marketplaceDataService,
                10
        );

        lenient().when(chatSessionRepository.findById(501L)).thenReturn(Optional.of(testSession));
        lenient().when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // -------------------------------------------------------------
    // SECTION 1: SyntheticOrderDataProvider Unit Tests
    // -------------------------------------------------------------

    @Test
    @DisplayName("Provider: generates 10 realistic orders with all required non-null fields")
    void testSyntheticOrderDataProvider_Has10OrdersWithValidFields() {
        List<OrderDto> orders = syntheticOrderDataProvider.getAllSyntheticOrders();
        assertEquals(10, orders.size(), "Should contain exactly 10 synthetic orders");

        for (OrderDto o : orders) {
            assertNotNull(o.getId(), "Order ID must not be null");
            assertNotNull(o.getBuyerName(), "Buyer name must not be null");
            assertNotNull(o.getBuyerPhone(), "Buyer phone must not be null");
            assertNotNull(o.getCommodityName(), "Commodity name must not be null");
            assertNotNull(o.getQuantity(), "Quantity must not be null");
            assertNotNull(o.getUnit(), "Unit must not be null");
            assertNotNull(o.getPricePerUnit(), "Price per unit must not be null");
            assertNotNull(o.getTotalAmount(), "Total amount must not be null");
            assertNotNull(o.getMarketLocation(), "Market location must not be null");
            assertNotNull(o.getDeliveryLocation(), "Delivery location must not be null");
            assertNotNull(o.getStatus(), "Status must not be null");
            assertNotNull(o.getCreatedAt(), "CreatedAt must not be null");

            // Verify totalAmount = quantity * pricePerUnit
            BigDecimal expectedTotal = o.getQuantity().multiply(o.getPricePerUnit()).setScale(2, BigDecimal.ROUND_HALF_UP);
            assertEquals(expectedTotal, o.getTotalAmount().setScale(2, BigDecimal.ROUND_HALF_UP),
                    "Total amount should match quantity * pricePerUnit for order " + o.getId());
        }
    }

    @Test
    @DisplayName("Provider: filters orders placed today")
    void testSyntheticOrderDataProvider_FilterByDate_Today() {
        OrderFilterDto filter = new OrderFilterDto();
        filter.setStartDate(today.toString());
        filter.setEndDate(today.toString());

        List<OrderDto> result = syntheticOrderDataProvider.filterOrders(filter);
        assertEquals(2, result.size(), "Should have 2 orders placed today");
        assertTrue(result.stream().allMatch(o -> "Vishal Patil".equals(o.getBuyerName())));
        assertTrue(result.stream().allMatch(o -> "Soybean".equals(o.getCommodityName())));
    }

    @Test
    @DisplayName("Provider: filters orders placed yesterday")
    void testSyntheticOrderDataProvider_FilterByDate_Yesterday() {
        OrderFilterDto filter = new OrderFilterDto();
        filter.setStartDate(yesterday.toString());
        filter.setEndDate(yesterday.toString());

        List<OrderDto> result = syntheticOrderDataProvider.filterOrders(filter);
        assertEquals(2, result.size(), "Should have 2 orders placed yesterday");
        List<String> buyers = result.stream().map(OrderDto::getBuyerName).toList();
        assertTrue(buyers.contains("K.K. Canvassing Agent"));
        assertTrue(buyers.contains("Ramesh Sharma"));
    }

    @Test
    @DisplayName("Provider: filters orders on historical date 14 October 2026")
    void testSyntheticOrderDataProvider_FilterByDate_SpecificDate() {
        OrderFilterDto filter = new OrderFilterDto();
        filter.setStartDate("2026-10-14");
        filter.setEndDate("2026-10-14");

        List<OrderDto> result = syntheticOrderDataProvider.filterOrders(filter);
        assertEquals(3, result.size(), "Should have 3 orders on 2026-10-14");
        List<String> buyers = result.stream().map(OrderDto::getBuyerName).toList();
        assertTrue(buyers.contains("Pooja Kulkarni"));
        assertTrue(buyers.contains("Vishal Patil"));
        assertTrue(buyers.contains("Suresh Gupta"));
    }

    @Test
    @DisplayName("Provider: filters orders by buyer name 'Vishal Patil'")
    void testSyntheticOrderDataProvider_FilterByBuyer_VishalPatil() {
        OrderFilterDto filter = new OrderFilterDto();
        filter.setBuyerName("Vishal Patil");

        List<OrderDto> result = syntheticOrderDataProvider.filterOrders(filter);
        assertEquals(3, result.size(), "Vishal Patil should have 3 orders in total");

        BigDecimal totalValue = result.stream()
                .map(OrderDto::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // 450,000 + 225,000 + 321,375 = 996,375.00
        assertEquals(new BigDecimal("996375.00"), totalValue);
    }

    @Test
    @DisplayName("Provider: filters orders by commodity 'Soybean'")
    void testSyntheticOrderDataProvider_FilterByCommodity_Soybean() {
        OrderFilterDto filter = new OrderFilterDto();
        filter.setCommodityName("Soybean");

        List<OrderDto> result = syntheticOrderDataProvider.filterOrders(filter);
        assertEquals(5, result.size(), "Should have 5 Soybean orders");
        assertTrue(result.stream().allMatch(o -> "Soybean".equalsIgnoreCase(o.getCommodityName())));
    }

    @Test
    @DisplayName("Provider: getOrderById returns correct order or null")
    void testSyntheticOrderDataProvider_GetOrderById() {
        OrderDto order101 = syntheticOrderDataProvider.getOrderById(101L);
        assertNotNull(order101);
        assertEquals("Vishal Patil", order101.getBuyerName());
        assertEquals(new BigDecimal("450000.00"), order101.getTotalAmount());

        assertNull(syntheticOrderDataProvider.getOrderById(9999L));
        assertNull(syntheticOrderDataProvider.getOrderById(null));
    }

    // -------------------------------------------------------------
    // SECTION 2: MarketplaceDataService Synthetic Toggle Tests
    // -------------------------------------------------------------

    @Test
    @DisplayName("MarketplaceDataService: delegates to SyntheticOrderDataProvider when toggle is enabled")
    void testMarketplaceDataService_SyntheticEnabled_ReturnsSyntheticOrders() {
        marketplaceDataService.setSyntheticOrdersEnabled(true);

        OrderFilterDto filter = new OrderFilterDto();
        filter.setStartDate(today.toString());
        filter.setEndDate(today.toString());

        List<OrderDto> orders = marketplaceDataService.fetchOrders(filter);
        assertNotNull(orders);
        assertEquals(2, orders.size());
        assertEquals("Vishal Patil", orders.get(0).getBuyerName());

        OrderDto order105 = marketplaceDataService.fetchOrderById(105L);
        assertNotNull(order105);
        assertEquals("Amit Deshmukh", order105.getBuyerName());
        assertEquals("Cotton", order105.getCommodityName());
    }

    @Test
    @DisplayName("MarketplaceDataService: fetchOrdersDocuments generates searchable documents without buyer phone numbers")
    void testMarketplaceDataService_SyntheticEnabled_FetchOrdersDocuments_PrivacyPreserved() {
        marketplaceDataService.setSyntheticOrdersEnabled(true);

        List<MarketplaceDocument> docs = marketplaceDataService.fetchOrdersDocuments();
        assertEquals(10, docs.size(), "Should create 10 searchable documents from synthetic orders");

        for (MarketplaceDocument doc : docs) {
            assertNotNull(doc.getContent());
            assertNotNull(doc.getMetadata());
            assertEquals("MARKETPLACE_DATA", doc.getMetadata().get("type"));
            assertEquals("orders", doc.getMetadata().get("source"));
            assertEquals("ORDER", doc.getMetadata().get("category"));

            // STRICT PRIVACY: phone number must never be in document content or metadata
            assertFalse(doc.getContent().contains("+91-"), "Document content must not contain phone number");
            assertFalse(doc.getContent().contains("9876543210"), "Document content must not contain raw phone digits");
            assertNull(doc.getMetadata().get("buyerPhone"), "Metadata must not include buyerPhone");
            assertNull(doc.getMetadata().get("phone"), "Metadata must not include phone");
        }
    }

    @Test
    @DisplayName("MarketplaceDataService: throws UnsupportedOperationException when synthetic toggle is disabled")
    void testMarketplaceDataService_SyntheticDisabled_ThrowsUnsupportedOperationException() {
        marketplaceDataService.setSyntheticOrdersEnabled(false);

        OrderFilterDto filter = new OrderFilterDto();
        assertThrows(UnsupportedOperationException.class, () -> marketplaceDataService.fetchOrders(filter));

        List<MarketplaceDocument> docs = marketplaceDataService.fetchOrdersDocuments();
        assertTrue(docs.isEmpty(), "Should return empty document list when synthetic toggle is false and client throws");
    }

    // -------------------------------------------------------------
    // SECTION 3: AdminChatbotService End-to-End Chatbot Verification
    // -------------------------------------------------------------

    @Test
    @DisplayName("Chatbot: 'Show orders placed today' returns 2 orders formatted in markdown table")
    void testChatbot_TodayOrders_ReturnsOrdersTable() {
        when(intentService.detectIntent(any())).thenReturn(IntentService.INTENT_ORDERS);

        ChatRequest request = new ChatRequest(501L, "Show orders placed today", "ENGLISH");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("ORDERS", response.getDisplayType());
        assertNotNull(response.getOrders());
        assertEquals(2, response.getOrders().size());

        String content = response.getMessage();
        assertTrue(content.contains("Found 2 matching Soybean orders"), "Summary header should report 2 Soybean orders");
        assertTrue(content.contains("Vishal Patil"), "Content must include buyer name Vishal Patil");
        assertTrue(content.contains("450000.00"), "Content must include order total 450000.00");
        assertTrue(content.contains("225000.00"), "Content must include order total 225000.00");
        assertTrue(content.contains("| Order ID | Buyer Name | Commodity |"), "Content must contain markdown table header");

        // STRICT PRIVACY: buyer phone must never be in response text
        assertFalse(content.contains("+91-"), "Response text must never expose buyer phone number");
        assertFalse(content.contains("9876543210"), "Response text must never expose buyer phone number");
    }

    @Test
    @DisplayName("Chatbot: 'What orders were placed yesterday?' returns yesterday's orders")
    void testChatbot_YesterdayOrders_ReturnsOrdersTable() {
        when(intentService.detectIntent(any())).thenReturn(IntentService.INTENT_ORDERS);

        ChatRequest request = new ChatRequest(501L, "What orders were placed yesterday?", "ENGLISH");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("ORDERS", response.getDisplayType());
        assertNotNull(response.getOrders());
        assertEquals(2, response.getOrders().size());

        String content = response.getMessage();
        assertTrue(content.contains("K.K. Canvassing Agent"));
        assertTrue(content.contains("Ramesh Sharma"));
        assertTrue(content.contains("880000.00"));
        assertTrue(content.contains("427500.00"));
        assertFalse(content.contains("+91-"), "Privacy violation: phone number exposed");
    }

    @Test
    @DisplayName("Chatbot: 'Show orders placed on 14 October 2026' returns 3 matching orders")
    void testChatbot_OrdersOnOct14_ReturnsMatchingOrders() {
        when(intentService.detectIntent(any())).thenReturn(IntentService.INTENT_ORDERS);

        ChatRequest request = new ChatRequest(501L, "Show orders placed on 14 October 2026", "ENGLISH");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("ORDERS", response.getDisplayType());
        assertNotNull(response.getOrders());
        assertEquals(3, response.getOrders().size());

        String content = response.getMessage();
        assertTrue(content.contains("Pooja Kulkarni"));
        assertTrue(content.contains("Vishal Patil"));
        assertTrue(content.contains("Suresh Gupta"));
        assertTrue(content.contains("268200.00"));
        assertTrue(content.contains("321375.00"));
        assertTrue(content.contains("254250.00"));
        assertFalse(content.contains("+91-"));
    }

    @Test
    @DisplayName("Chatbot: 'Show Vishal Patil's orders' returns 3 orders across all dates")
    void testChatbot_VishalPatilOrders_ReturnsThreeOrders() {
        when(intentService.detectIntent(any())).thenReturn(IntentService.INTENT_ORDERS);

        ChatRequest request = new ChatRequest(501L, "Show Vishal Patil's orders", "ENGLISH");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("ORDERS", response.getDisplayType());
        assertEquals("Vishal Patil", response.getBuyerName());
        assertNotNull(response.getOrders());
        assertEquals(3, response.getOrders().size());

        String content = response.getMessage();
        assertTrue(content.contains("Found 3 matching order"));
        assertTrue(content.contains("Vishal Patil"));
        assertFalse(content.contains("+91-"));
    }

    @Test
    @DisplayName("Chatbot: 'What is the total value of Vishal Patil's orders?' calculates ₹996375.00 INR")
    void testChatbot_VishalPatilTotalValue_ReturnsAccurateCalculation() {
        when(intentService.detectIntent(any())).thenReturn(IntentService.INTENT_ORDERS);

        ChatRequest request = new ChatRequest(501L, "What is the total value of Vishal Patil's orders?", "ENGLISH");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("ORDERS", response.getDisplayType());
        String content = response.getMessage();

        assertTrue(content.contains("According to marketplace records, the total value of Vishal Patil's orders is ₹996375.00 INR across 3 orders."),
                "Should state exact total value of 996375.00 INR across 3 orders");
        assertTrue(content.contains("| Order ID | Buyer Name | Commodity |"), "Should still include the detail table");
        assertFalse(content.contains("+91-"));
    }

    @Test
    @DisplayName("Chatbot: Marathi query 'विशाल पाटील यांच्या ऑर्डर्स दाखवा' returns Marathi summary table")
    void testChatbot_Multilingual_MarathiBuyerOrders() {
        when(intentService.detectIntent(any())).thenReturn(IntentService.INTENT_ORDERS);

        ChatRequest request = new ChatRequest(501L, "विशाल पाटील यांच्या ऑर्डर्स दाखवा", "MARATHI");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("ORDERS", response.getDisplayType());
        String content = response.getMessage();

        assertTrue(content.contains("मार्केटप्लेसच्या नोंदीनुसार Vishal Patil यांच्या 3 जुळणाऱ्या ऑर्डर्स आढळल्या"),
                "Should contain Marathi summary header");
        assertTrue(content.contains("ऑर्डर आयडी") || content.contains("खरेदीदार") || content.contains("सोयाबीन"),
                "Should contain Marathi localized table headers");
        assertFalse(content.contains("+91-"));
    }

    @Test
    @DisplayName("Chatbot: Hindi query 'विशाल पाटिल के ऑर्डर दिखाएं' returns Hindi summary table")
    void testChatbot_Multilingual_HindiBuyerOrders() {
        when(intentService.detectIntent(any())).thenReturn(IntentService.INTENT_ORDERS);

        ChatRequest request = new ChatRequest(501L, "विशाल पाटिल के ऑर्डर दिखाएं", "HINDI");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("ORDERS", response.getDisplayType());
        String content = response.getMessage();

        assertTrue(content.contains("मार्केटप्लेस रिकॉर्ड्स के अनुसार Vishal Patil के 3 मेल खाने वाले ऑर्डर मिले"),
                "Should contain Hindi summary header");
        assertTrue(content.contains("ऑर्डर आईडी") || content.contains("खरीदार") || content.contains("सोयाबीन"),
                "Should contain Hindi localized table headers");
        assertFalse(content.contains("+91-"));
    }

    @Test
    @DisplayName("Chatbot: Gracefully handles unsupported orders API when synthetic toggle is disabled")
    void testChatbot_SyntheticDisabled_ReturnsGracefulUnavailableResponse() {
        marketplaceDataService.setSyntheticOrdersEnabled(false);
        when(intentService.detectIntent(any())).thenReturn(IntentService.INTENT_ORDERS);

        ChatRequest request = new ChatRequest(501L, "Show orders placed today", "ENGLISH");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("ORDERS", response.getDisplayType());
        assertNull(response.getOrders());

        String content = response.getMessage();
        assertTrue(content.contains("Deals") || content.contains("Contracts") || content.contains("unavailable"),
                "Response should explain Tradex backend models commercial transactions as Deals and Contracts");
    }
}
