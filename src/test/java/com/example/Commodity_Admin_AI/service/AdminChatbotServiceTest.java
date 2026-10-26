package com.example.Commodity_Admin_AI.service;

import com.example.Commodity_Admin_AI.ai.GeminiService;
import com.example.Commodity_Admin_AI.ai.RagService;
import com.example.Commodity_Admin_AI.dto.ChartData;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminChatbotServiceTest {

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

    private AdminChatbotService adminChatbotService;

    private ChatSession testSession;

    @BeforeEach
    void setUp() {
        testSession = new ChatSession("admin");
        testSession.setId(100L);
        adminChatbotService = new AdminChatbotService(
                chatSessionRepository,
                chatMessageRepository,
                intentService,
                ragService,
                geminiService,
                10
        );
    }

    @Test
    @DisplayName("processChat creates new session, saves messages, and orchestrates retrieval and response")
    void processChat_NewSession_OrchestratesFullFlow() {
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(testSession);
        when(intentService.detectIntent("What is the current corn rate?")).thenReturn("CURRENT_RATE");
        when(ragService.retrieveContext("What is the current corn rate?", "CURRENT_RATE")).thenReturn("Corn rate context");
        when(geminiService.generateResponse("What is the current corn rate?", "Corn rate context"))
                .thenReturn("Corn rate is 2200 per quintal");

        ChatRequest request = new ChatRequest("What is the current corn rate?");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals(100L, response.getSessionId());
        assertEquals("Corn rate is 2200 per quintal", response.getMessage());

        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, times(2)).save(messageCaptor.capture());

        List<ChatMessage> savedMessages = messageCaptor.getAllValues();
        assertEquals(2, savedMessages.size());

        ChatMessage adminMsg = savedMessages.get(0);
        assertEquals(ChatMessage.ROLE_ADMIN, adminMsg.getRole());
        assertEquals("What is the current corn rate?", adminMsg.getContent());
        assertEquals(testSession, adminMsg.getSession());

        ChatMessage aiMsg = savedMessages.get(1);
        assertEquals(ChatMessage.ROLE_ASSISTANT, aiMsg.getRole());
        assertEquals("Corn rate is 2200 per quintal", aiMsg.getContent());
        assertEquals(testSession, aiMsg.getSession());
    }

    @Test
    @DisplayName("processChat with existing session ID finds session and preserves conversation context")
    void processChat_ExistingSession_PreservesSession() {
        when(chatSessionRepository.findById(100L)).thenReturn(Optional.of(testSession));
        when(intentService.detectIntent("What about yesterday?")).thenReturn("RATE_TREND");
        when(ragService.retrieveContext("What about yesterday?", "RATE_TREND")).thenReturn("Trend context");
        when(geminiService.generateResponse("What about yesterday?", "Trend context")).thenReturn("Yesterday rate was 2180");

        ChatRequest request = new ChatRequest(100L, "What about yesterday?");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals(100L, response.getSessionId());
        assertEquals("Yesterday rate was 2180", response.getMessage());

        verify(chatSessionRepository, times(0)).save(any(ChatSession.class));
        verify(chatMessageRepository, times(2)).save(any(ChatMessage.class));
    }

    @Test
    @DisplayName("processChat with existing session and prior history passes bounded history to Gemini")
    void processChat_ExistingSession_WithHistory_PassesHistoryToGemini() {
        ChatMessage priorAdminMsg = new ChatMessage(testSession, ChatMessage.ROLE_ADMIN, "What is the soybean rate in Sangli?");
        priorAdminMsg.setId(1L);
        ChatMessage priorAiMsg = new ChatMessage(testSession, ChatMessage.ROLE_ASSISTANT, "Soybean rate in Sangli is 42000 per MT.");
        priorAiMsg.setId(2L);
        List<ChatMessage> history = List.of(priorAdminMsg, priorAiMsg);

        when(chatSessionRepository.findById(100L)).thenReturn(Optional.of(testSession));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(100L)).thenReturn(history);
        when(intentService.detectIntent("What about yesterday?")).thenReturn("RATE_TREND");
        when(ragService.retrieveContext("What about yesterday?", "RATE_TREND")).thenReturn("Trend context for Sangli");
        when(geminiService.generateResponse(eq("What about yesterday?"), eq("Trend context for Sangli"), eq(history)))
                .thenReturn("Yesterday rate in Sangli was 41800 per MT.");

        ChatRequest request = new ChatRequest(100L, "What about yesterday?");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals(100L, response.getSessionId());
        assertEquals("Yesterday rate in Sangli was 41800 per MT.", response.getMessage());

        verify(geminiService).generateResponse("What about yesterday?", "Trend context for Sangli", history);
        verify(chatMessageRepository, times(2)).save(any(ChatMessage.class));
    }

    @Test
    @DisplayName("getBoundedHistory bounds conversation to the last maxHistoryMessages in chronological order")
    void getBoundedHistory_ExceedsMax_ReturnsLatestMessages() {
        List<ChatMessage> allMessages = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            ChatMessage msg = new ChatMessage(testSession, (i % 2 == 1) ? ChatMessage.ROLE_ADMIN : ChatMessage.ROLE_ASSISTANT, "Message " + i);
            msg.setId((long) i);
            allMessages.add(msg);
        }

        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(100L)).thenReturn(allMessages);

        List<ChatMessage> bounded = adminChatbotService.getBoundedHistory(100L);

        assertEquals(10, bounded.size());
        assertEquals("Message 6", bounded.get(0).getContent());
        assertEquals(6L, bounded.get(0).getId());
        assertEquals("Message 15", bounded.get(9).getContent());
        assertEquals(15L, bounded.get(9).getId());
    }

    @Test
    @DisplayName("getBoundedHistory handles null session ID and empty messages gracefully")
    void getBoundedHistory_NullOrEmpty_ReturnsEmptyList() {
        assertTrue(adminChatbotService.getBoundedHistory(null).isEmpty());

        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(100L)).thenReturn(Collections.emptyList());
        assertTrue(adminChatbotService.getBoundedHistory(100L).isEmpty());
    }

    @Test
    @DisplayName("getSessionMessages retrieves all messages ordered chronologically")
    void getSessionMessages_ReturnsAllOrdered() {
        ChatMessage msg1 = new ChatMessage(testSession, ChatMessage.ROLE_ADMIN, "Hello");
        ChatMessage msg2 = new ChatMessage(testSession, ChatMessage.ROLE_ASSISTANT, "Hi");
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(100L)).thenReturn(List.of(msg1, msg2));

        List<ChatMessage> result = adminChatbotService.getSessionMessages(100L);

        assertEquals(2, result.size());
        assertEquals("Hello", result.get(0).getContent());
        assertEquals("Hi", result.get(1).getContent());
    }

    @Test
    @DisplayName("getSessionsForAdmin retrieves sessions ordered by creation date descending")
    void getSessionsForAdmin_ReturnsAdminSessions() {
        ChatSession session1 = new ChatSession("admin");
        ChatSession session2 = new ChatSession("admin");
        when(chatSessionRepository.findByAdminUserIdOrderByCreatedAtDesc("admin")).thenReturn(List.of(session1, session2));

        List<ChatSession> sessions = adminChatbotService.getSessionsForAdmin("admin");

        assertEquals(2, sessions.size());
        assertTrue(adminChatbotService.getSessionsForAdmin(null).isEmpty());
        assertTrue(adminChatbotService.getSessionsForAdmin("   ").isEmpty());
    }

    @Test
    @DisplayName("getSession retrieves session by ID correctly")
    void getSession_ReturnsSessionIfExists() {
        when(chatSessionRepository.findById(100L)).thenReturn(Optional.of(testSession));

        Optional<ChatSession> result = adminChatbotService.getSession(100L);

        assertTrue(result.isPresent());
        assertEquals(100L, result.get().getId());
        assertTrue(adminChatbotService.getSession(null).isEmpty());
    }

    @Test
    @DisplayName("sessionIsolation ensures different sessions do not share or leak history")
    void sessionIsolation_DifferentSessionsDoNotShareHistory() {
        ChatSession sessionA = new ChatSession("admin");
        sessionA.setId(201L);
        ChatSession sessionB = new ChatSession("admin");
        sessionB.setId(202L);

        ChatMessage msgA = new ChatMessage(sessionA, ChatMessage.ROLE_ADMIN, "Question for Session A");
        ChatMessage msgB = new ChatMessage(sessionB, ChatMessage.ROLE_ADMIN, "Question for Session B");

        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(201L)).thenReturn(List.of(msgA));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(202L)).thenReturn(List.of(msgB));

        List<ChatMessage> historyA = adminChatbotService.getBoundedHistory(201L);
        List<ChatMessage> historyB = adminChatbotService.getBoundedHistory(202L);

        assertEquals(1, historyA.size());
        assertEquals("Question for Session A", historyA.get(0).getContent());

        assertEquals(1, historyB.size());
        assertEquals("Question for Session B", historyB.get(0).getContent());
    }

    @Test
    @DisplayName("processChat handles GREETING with controlled response without querying RAG, PGVector, or Gemini")
    void processChat_Greeting_ReturnsControlledResponse_WithoutRagOrGemini() {
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(testSession);
        when(intentService.detectIntent("Hello")).thenReturn(IntentService.INTENT_GREETING);

        ChatRequest request = new ChatRequest("Hello");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals(100L, response.getSessionId());
        assertEquals(IntentService.CONTROLLED_HELP_RESPONSE, response.getMessage());

        verify(ragService, never()).retrieveContext(anyString(), anyString());
        verify(geminiService, never()).generateResponse(anyString(), anyString());
        verify(geminiService, never()).generateResponse(anyString(), anyString(), anyList());
        verify(chatMessageRepository, times(2)).save(any(ChatMessage.class));
    }

    @Test
    @DisplayName("processChat handles greeting variants (Hi, How are you?) with controlled response")
    void processChat_GreetingVariants_ReturnControlledResponse() {
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(testSession);
        when(intentService.detectIntent("Hi")).thenReturn(IntentService.INTENT_GREETING);

        ChatResponse responseHi = adminChatbotService.processChat(new ChatRequest("Hi"));
        assertEquals(IntentService.CONTROLLED_HELP_RESPONSE, responseHi.getMessage());

        when(intentService.detectIntent("How are you?")).thenReturn(IntentService.INTENT_GREETING);
        ChatResponse responseHowAreYou = adminChatbotService.processChat(new ChatRequest("How are you?"));
        assertEquals(IntentService.CONTROLLED_HELP_RESPONSE, responseHowAreYou.getMessage());

        verify(ragService, never()).retrieveContext(anyString(), anyString());
        verify(geminiService, never()).generateResponse(anyString(), anyString());
    }

    @Test
    @DisplayName("processChat handles OUT_OF_SCOPE with controlled response without querying RAG or Gemini")
    void processChat_OutOfScope_ReturnsControlledResponse_WithoutRagOrGemini() {
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(testSession);
        when(intentService.detectIntent("What is the weather today?")).thenReturn(IntentService.INTENT_OUT_OF_SCOPE);

        ChatRequest request = new ChatRequest("What is the weather today?");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals(100L, response.getSessionId());
        assertEquals(IntentService.CONTROLLED_HELP_RESPONSE, response.getMessage());

        verify(ragService, never()).retrieveContext(anyString(), anyString());
        verify(geminiService, never()).generateResponse(anyString(), anyString());
    }

    @Test
    @DisplayName("processChat for 'What is soybean?' routes through existing Knowledge RAG and Gemini flow")
    void processChat_Knowledge_WhatIsSoybean_FlowPreserved() {
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(testSession);
        when(intentService.detectIntent("What is soybean?")).thenReturn(IntentService.INTENT_COMMODITY_INFORMATION);
        when(ragService.retrieveContext("What is soybean?", IntentService.INTENT_COMMODITY_INFORMATION))
                .thenReturn("Soybean is an oilseed commodity.");
        when(geminiService.generateResponse("What is soybean?", "Soybean is an oilseed commodity."))
                .thenReturn("Soybean is a major oilseed crop grown in India.");

        ChatRequest request = new ChatRequest("What is soybean?");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("Soybean is a major oilseed crop grown in India.", response.getMessage());
        verify(ragService).retrieveContext("What is soybean?", IntentService.INTENT_COMMODITY_INFORMATION);
        verify(geminiService).generateResponse("What is soybean?", "Soybean is an oilseed commodity.");
    }

    @Test
    @DisplayName("processChat for 'What is today's soybean rate?' routes through existing Marketplace flow")
    void processChat_Marketplace_WhatIsTodaysSoybeanRate_FlowPreserved() {
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(testSession);
        when(intentService.detectIntent("What is today's soybean rate?")).thenReturn(IntentService.INTENT_CURRENT_RATE);
        when(ragService.retrieveContext("What is today's soybean rate?", IntentService.INTENT_CURRENT_RATE))
                .thenReturn("Current rate: Sangli 42000 per MT.");
        when(geminiService.generateResponse("What is today's soybean rate?", "Current rate: Sangli 42000 per MT."))
                .thenReturn("Today's soybean rate in Sangli is Rs 42,000 per MT.");

        ChatRequest request = new ChatRequest("What is today's soybean rate?");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("Today's soybean rate in Sangli is Rs 42,000 per MT.", response.getMessage());
        verify(ragService).retrieveContext("What is today's soybean rate?", IntentService.INTENT_CURRENT_RATE);
        verify(geminiService).generateResponse("What is today's soybean rate?", "Current rate: Sangli 42000 per MT.");
    }

    @Test
    @DisplayName("processChat for historical rate query attaches chartData and sets displayType to CHART")
    void processChat_HistoricalRateQuery_SetsChartDataAndDisplayType() {
        MarketplaceDataService mockMarketplaceDataService = org.mockito.Mockito.mock(MarketplaceDataService.class);
        AdminChatbotService serviceWithMarketplace = new AdminChatbotService(
                chatSessionRepository,
                chatMessageRepository,
                intentService,
                ragService,
                geminiService,
                mockMarketplaceDataService,
                10
        );

        String question = "Show me the last 7 days of Soybean rates in Sangli";
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(testSession);
        when(intentService.detectIntent(question)).thenReturn(IntentService.INTENT_RATE_TREND);
        org.mockito.Mockito.lenient().when(ragService.retrieveContext(question, IntentService.INTENT_RATE_TREND))
                .thenReturn("Soybean rates in Sangli: Oct 1: 4200, Oct 7: 4250");
        org.mockito.Mockito.lenient().when(geminiService.generateResponse(question, "Soybean rates in Sangli: Oct 1: 4200, Oct 7: 4250"))
                .thenReturn("Here are the last 7 days of Soybean rates in Sangli.");

        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        List<com.example.Commodity_Admin_AI.dto.ChartData> chartPoints = List.of(
                new com.example.Commodity_Admin_AI.dto.ChartData(today.minusDays(5).toString(), new java.math.BigDecimal("4200.0"), "Soybean", "Sangli", "Quintal"),
                new com.example.Commodity_Admin_AI.dto.ChartData(today.toString(), new java.math.BigDecimal("4250.0"), "Soybean", "Sangli", "Quintal")
        );
        when(mockMarketplaceDataService.fetchHistoricalRates("Soybean", "Sangli")).thenReturn(chartPoints);

        ChatRequest request = new ChatRequest(question);
        ChatResponse response = serviceWithMarketplace.processChat(request);

        assertNotNull(response);
        assertEquals("RATE", response.getDisplayType());
        assertNotNull(response.getChartData());
        assertEquals(2, response.getChartData().size());
        assertEquals(new java.math.BigDecimal("4200.0"), response.getChartData().get(0).getRate());
        assertEquals(new java.math.BigDecimal("4250.0"), response.getChartData().get(1).getRate());
        verify(mockMarketplaceDataService).fetchHistoricalRates("Soybean", "Sangli");
    }

    @Test
    @DisplayName("processChat for explicit rate chart query sets displayType to CHART")
    void processChat_ChartRateQuery_SetsDisplayTypeToChart() {
        MarketplaceDataService mockMarketplaceDataService = org.mockito.Mockito.mock(MarketplaceDataService.class);
        AdminChatbotService serviceWithMarketplace = new AdminChatbotService(
                chatSessionRepository,
                chatMessageRepository,
                intentService,
                ragService,
                geminiService,
                mockMarketplaceDataService,
                10
        );

        String question = "Show me a chart of Soybean rates for the last 7 days";
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(testSession);
        when(intentService.detectIntent(question)).thenReturn(IntentService.INTENT_RATE_CHART);

        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        List<com.example.Commodity_Admin_AI.dto.ChartData> chartPoints = List.of(
                new com.example.Commodity_Admin_AI.dto.ChartData(today.minusDays(5).toString(), new java.math.BigDecimal("4200.0"), "Soybean", "Sangli", "Quintal"),
                new com.example.Commodity_Admin_AI.dto.ChartData(today.toString(), new java.math.BigDecimal("4250.0"), "Soybean", "Sangli", "Quintal")
        );
        when(mockMarketplaceDataService.fetchHistoricalRates("Soybean", null)).thenReturn(chartPoints);

        ChatRequest request = new ChatRequest(question);
        ChatResponse response = serviceWithMarketplace.processChat(request);

        assertNotNull(response);
        assertEquals("CHART", response.getDisplayType());
        assertNotNull(response.getChartData());
        assertEquals(2, response.getChartData().size());
    }

    @Test
    @DisplayName("processChat with Marathi historical rate query returns localized Marathi fallback and chartData when Gemini fails")
    void processChat_MarathiHistoricalRateQuery_GeminiFails_ReturnsLocalizedMarathiFallbackAndChartData() {
        MarketplaceDataService mockMarketplaceDataService = org.mockito.Mockito.mock(MarketplaceDataService.class);
        AdminChatbotService serviceWithMarketplace = new AdminChatbotService(
                chatSessionRepository,
                chatMessageRepository,
                intentService,
                ragService,
                geminiService,
                mockMarketplaceDataService,
                10
        );

        String question = "गेल्या ७ दिवसांचे सोयाबीनचे दर दाखवा.";
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(testSession);
        when(intentService.detectIntent(question)).thenReturn(IntentService.INTENT_RATE_TREND);
        org.mockito.Mockito.lenient().when(ragService.retrieveContext(eq(question), eq(IntentService.INTENT_RATE_TREND)))
                .thenReturn("Market context");
        org.mockito.Mockito.lenient().when(geminiService.generateResponse(eq(question), eq("Market context"), anyList(), eq("Marathi")))
                .thenReturn("An error occurred while communicating with the AI service: Failed to generate content");

        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        List<com.example.Commodity_Admin_AI.dto.ChartData> chartPoints = List.of(
                new com.example.Commodity_Admin_AI.dto.ChartData(today.minusDays(6).toString(), new java.math.BigDecimal("4180.0"), "Soybean", "Sangli", "Quintal"),
                new com.example.Commodity_Admin_AI.dto.ChartData(today.minusDays(5).toString(), new java.math.BigDecimal("4200.0"), "Soybean", "Sangli", "Quintal"),
                new com.example.Commodity_Admin_AI.dto.ChartData(today.minusDays(4).toString(), new java.math.BigDecimal("4210.0"), "Soybean", "Sangli", "Quintal"),
                new com.example.Commodity_Admin_AI.dto.ChartData(today.minusDays(3).toString(), new java.math.BigDecimal("4225.0"), "Soybean", "Sangli", "Quintal"),
                new com.example.Commodity_Admin_AI.dto.ChartData(today.minusDays(2).toString(), new java.math.BigDecimal("4235.0"), "Soybean", "Sangli", "Quintal"),
                new com.example.Commodity_Admin_AI.dto.ChartData(today.minusDays(1).toString(), new java.math.BigDecimal("4240.0"), "Soybean", "Sangli", "Quintal"),
                new com.example.Commodity_Admin_AI.dto.ChartData(today.toString(), new java.math.BigDecimal("4250.0"), "Soybean", "Sangli", "Quintal")
        );
        when(mockMarketplaceDataService.fetchHistoricalRates("Soybean", null)).thenReturn(chartPoints);

        ChatRequest request = new ChatRequest(null, question, "mr");
        ChatResponse response = serviceWithMarketplace.processChat(request);

        assertNotNull(response);
        assertEquals("RATE", response.getDisplayType());
        assertNotNull(response.getChartData());
        assertEquals(7, response.getChartData().size());
        assertEquals(new java.math.BigDecimal("4180.0"), response.getChartData().get(0).getRate());
        assertEquals(new java.math.BigDecimal("4250.0"), response.getChartData().get(6).getRate());
        assertTrue(response.getMessage().contains("मार्केटप्लेसच्या नोंदीनुसार"));
        assertTrue(response.getMessage().contains("सोयाबीनचे मागील ७ दिवसांचे दर"));
        assertTrue(response.getMessage().contains("सांगलीतील"));
        assertTrue(response.getMessage().contains("| " + today.minusDays(6) + " |"));
        assertTrue(!response.getMessage().contains("Failed to generate content"));
        verify(mockMarketplaceDataService).fetchHistoricalRates("Soybean", null);
    }

    @Test
    @DisplayName("processChat with 'order of soybean' returns ORDERS displayType and clear explanation of missing orders API")
    void processChat_OrderQuery_English_ReturnsOrdersUnavailableResponse() {
        String question = "order of soybean";
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(testSession);
        when(intentService.detectIntent(question)).thenReturn(IntentService.INTENT_ORDERS);

        ChatRequest request = new ChatRequest(null, question, "en");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("ORDERS", response.getDisplayType());
        assertTrue(response.getMessage().contains("placed order data for Soybean is currently unavailable"));
        assertTrue(response.getMessage().contains("Soybean"));
        assertTrue(response.getMessage().contains("GET /api/ai/orders"));
        assertTrue(response.getMessage().contains("orders database entity/table"));
        assertTrue(response.getMessage().contains("Understanding Marketplace Data:"));
        assertTrue(response.getMessage().contains("Placed Orders:"));
        assertTrue(response.getMessage().contains("Buyer Demands:"));
        assertTrue(response.getMessage().contains("Market Rates:"));
    }

    @Test
    @DisplayName("processChat with 'Show soybean orders' recognizes orders and returns clear explanation")
    void processChat_ShowSoybeanOrders_English() {
        String question = "Show soybean orders";
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(testSession);
        when(intentService.detectIntent(question)).thenReturn(IntentService.INTENT_ORDERS);

        ChatRequest request = new ChatRequest(null, question, "en");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("ORDERS", response.getDisplayType());
        assertTrue(response.getMessage().contains("placed order data for Soybean is currently unavailable"));
        assertTrue(response.getMessage().contains("GET /api/ai/orders"));
    }

    @Test
    @DisplayName("processChat with Marathi orders query returns localized Marathi explanation")
    void processChat_OrderQuery_Marathi() {
        String question = "सोयाबीनच्या ऑर्डर्स दाखवा";
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(testSession);
        when(intentService.detectIntent(question)).thenReturn(IntentService.INTENT_ORDERS);

        ChatRequest request = new ChatRequest(null, question, "mr");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("ORDERS", response.getDisplayType());
        assertTrue(response.getMessage().contains("सोयाबीनसाठी प्रत्यक्ष पूर्ण झालेल्या किंवा नोंदवलेल्या ऑर्डर्स (Placed Orders) सध्या उपलब्ध नाहीत"));
        assertTrue(response.getMessage().contains("/api/ai/orders"));
        assertTrue(response.getMessage().contains("मार्केटप्लेस नोंदींमधील फरक:"));
    }

    @Test
    @DisplayName("processChat with Hindi orders query returns localized Hindi explanation")
    void processChat_OrderQuery_Hindi() {
        String question = "सोयाबीन के ऑर्डर्स";
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(testSession);
        when(intentService.detectIntent(question)).thenReturn(IntentService.INTENT_ORDERS);

        ChatRequest request = new ChatRequest(null, question, "hi");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("ORDERS", response.getDisplayType());
        assertTrue(response.getMessage().contains("सोयाबीन के लिए वास्तविक ऑर्डर्स (Placed Orders) का डेटा वर्तमान में उपलब्ध नहीं है"));
        assertTrue(response.getMessage().contains("/api/ai/orders"));
        assertTrue(response.getMessage().contains("मार्केटप्लेस रिकॉर्ड्स में अंतर:"));
    }

    @Test
    @DisplayName("processChat with parties query returns clear unavailable response explaining double-blind model")
    void processChat_Parties_English() {
        String question = "Show parties list";
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(testSession);
        when(intentService.detectIntent(question)).thenReturn(IntentService.INTENT_UNKNOWN);

        ChatRequest request = new ChatRequest(null, question, "en");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("UNAVAILABLE", response.getDisplayType());
        assertTrue(response.getMessage().contains("Party and trader directory records are currently unavailable"));
        assertTrue(response.getMessage().contains("double-blind trading model"));
        assertTrue(response.getMessage().contains("GET /api/ai/parties"));
    }

    @Test
    @DisplayName("processChat with Marathi parties query returns localized Marathi explanation")
    void processChat_Parties_Marathi() {
        String question = "व्यापारी यादी दाखवा";
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(testSession);
        when(intentService.detectIntent(question)).thenReturn(IntentService.INTENT_UNKNOWN);

        ChatRequest request = new ChatRequest(null, question, "mr");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("UNAVAILABLE", response.getDisplayType());
        assertTrue(response.getMessage().contains("व्यापारी आणि पार्टींची निर्देशिका (Parties Directory) सध्या उपलब्ध नाही"));
        assertTrue(response.getMessage().contains("डबल-ब्लाइंड"));
        assertTrue(response.getMessage().contains("/api/ai/parties"));
    }

    @Test
    @DisplayName("processChat with notification query returns clear unavailable response")
    void processChat_Notifications_English() {
        String question = "Show all recent notifications";
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(testSession);
        when(intentService.detectIntent(question)).thenReturn(IntentService.INTENT_NOTIFICATION_STATUS);

        ChatRequest request = new ChatRequest(null, question, "en");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("NOTIFICATION", response.getDisplayType());
        assertTrue(response.getMessage().contains("Marketplace notifications and alert logs are currently unavailable"));
        assertTrue(response.getMessage().contains("GET /api/ai/notifications"));
    }

    @Test
    @DisplayName("processChat with Marathi notification query returns localized explanation")
    void processChat_Notifications_Marathi() {
        String question = "सर्व सूचना दाखवा";
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(testSession);
        when(intentService.detectIntent(question)).thenReturn(IntentService.INTENT_NOTIFICATION_STATUS);

        ChatRequest request = new ChatRequest(null, question, "mr");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("NOTIFICATION", response.getDisplayType());
        assertTrue(response.getMessage().contains("मार्केटप्लेस सूचना आणि अलर्ट लॉग (Notifications) सध्या उपलब्ध नाहीत"));
        assertTrue(response.getMessage().contains("/api/ai/notifications"));
    }

    @Test
    @DisplayName("processChat with WhatsApp status query returns clear unavailable response")
    void processChat_WhatsApp_English() {
        String question = "Check whatsapp logs";
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(testSession);
        when(intentService.detectIntent(question)).thenReturn(IntentService.INTENT_WHATSAPP_STATUS);

        ChatRequest request = new ChatRequest(null, question, "en");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("WHATSAPP", response.getDisplayType());
        assertTrue(response.getMessage().contains("WhatsApp message dispatch logs and delivery status are currently unavailable"));
        assertTrue(response.getMessage().contains("GET /api/ai/whatsapp-logs"));
    }

    @Test
    @DisplayName("processChat with Marathi WhatsApp query returns localized explanation")
    void processChat_WhatsApp_Marathi() {
        String question = "व्हॉट्सॲप लॉग्स दाखवा";
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(testSession);
        when(intentService.detectIntent(question)).thenReturn(IntentService.INTENT_WHATSAPP_STATUS);

        ChatRequest request = new ChatRequest(null, question, "mr");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("WHATSAPP", response.getDisplayType());
        assertTrue(response.getMessage().contains("व्हॉट्सॲप संदेश नोंदी आणि पाठवलेली स्थिती (WhatsApp Logs) सध्या उपलब्ध नाहीत"));
        assertTrue(response.getMessage().contains("/api/ai/whatsapp-logs"));
    }

    @Test
    @DisplayName("generateHistoricalRateResponse includes Type column distinguishing historical records and asking prices")
    void generateHistoricalRateResponse_SevenDatedPoints_DistinguishesTypes() {
        List<ChartData> sevenPoints = List.of(
                new ChartData("2026-10-15", new BigDecimal("4250.0"), "Soybean", "Sangli", "Quintal", "Historical Record"),
                new ChartData("2026-10-16", new BigDecimal("4500.0"), "Soybean", "Sangli", "Quintal", "Asking Price"),
                new ChartData("2026-10-17", new BigDecimal("4800.0"), "Soybean", "Sangli", "Quintal", "Asking Price"),
                new ChartData("2026-10-18", new BigDecimal("4600.0"), "Soybean", "Sangli", "Quintal", "Asking Price"),
                new ChartData("2026-10-19", new BigDecimal("4300.0"), "Soybean", "Sangli", "Quintal", "Asking Price"),
                new ChartData("2026-10-20", new BigDecimal("4600.0"), "Soybean", "Sangli", "Quintal", "Asking Price"),
                new ChartData("2026-10-21", new BigDecimal("4650.0"), "Soybean", "Sangli", "Quintal", "Asking Price")
        );
        AdminChatbotService.HistoricalDateWindow window = new AdminChatbotService.HistoricalDateWindow(
                java.time.LocalDate.of(2026, 10, 15),
                java.time.LocalDate.of(2026, 10, 21),
                7,
                true
        );

        String result = adminChatbotService.generateHistoricalRateResponse(sevenPoints, "en", "Soybean", "Sangli", window, Collections.emptyList());

        assertNotNull(result);
        assertTrue(result.contains("| Date | Rate (INR per Quintal) | Type | Location |"));
        assertTrue(result.contains("| 2026-10-15 | ₹4250 | Historical Record | Sangli |"));
        assertTrue(result.contains("| 2026-10-16 | ₹4500 | Asking Price | Sangli |"));
        assertTrue(result.contains("| 2026-10-17 | ₹4800 | Asking Price | Sangli |"));
        assertTrue(result.contains("| 2026-10-18 | ₹4600 | Asking Price | Sangli |"));
        assertTrue(result.contains("| 2026-10-19 | ₹4300 | Asking Price | Sangli |"));
        assertTrue(result.contains("| 2026-10-20 | ₹4600 | Asking Price | Sangli |"));
        assertTrue(result.contains("| 2026-10-21 | ₹4650 | Asking Price | Sangli |"));
    }

    @Test
    @DisplayName("generateCurrentRateResponse selects latest valid date (Oct 21 at 4650) and excludes future dates (Nov 30)")
    void generateCurrentRateResponse_LatestNonFutureRate_PrefersOctober21AskingPrice() {
        List<com.example.Commodity_Admin_AI.dto.MarketplaceRateDto> rates = List.of(
                new com.example.Commodity_Admin_AI.dto.MarketplaceRateDto("Soybean", "Sangli", new BigDecimal("4300.0"), "Quintal", "2026-10-19"),
                new com.example.Commodity_Admin_AI.dto.MarketplaceRateDto("Soybean", "Sangli", new BigDecimal("4500.0"), "Quintal", "2026-11-30"), // Future date
                new com.example.Commodity_Admin_AI.dto.MarketplaceRateDto("Soybean", "Sangli", new BigDecimal("4500.0"), "Quintal", "2026-10-16"),
                new com.example.Commodity_Admin_AI.dto.MarketplaceRateDto("Soybean", "Sangli", new BigDecimal("4650.0"), "Quintal", "2026-10-21")
        );
        rates.forEach(r -> r.setPriceType("Asking Price"));

        String result = adminChatbotService.generateCurrentRateResponse(rates, "en", "Soybean", "Sangli", "What is the Soybean asking price in Sangli?");

        assertNotNull(result);
        assertTrue(result.contains("4650"), "Must select October 21 at 4650, not 4300 or 4500");
        assertTrue(result.contains("2026-10-21"), "Must report date 2026-10-21");
        assertFalse(result.contains("2026-11-30"), "Future date 2026-11-30 must NOT be selected");
        assertFalse(result.contains("2026-10-19"), "October 19 at 4300 must NOT be selected as current rate");
        assertTrue(result.toLowerCase().contains("asking price"), "Must be clearly labeled as an asking price");
    }

    @Test
    @DisplayName("generateCurrentRateResponse for all commodities returns single row for Soybean with no duplicates or future records")
    void generateCurrentRateResponse_AllCommodities_ReturnsOnlyRealRegisteredCommoditiesWithoutDuplicates() {
        List<com.example.Commodity_Admin_AI.dto.MarketplaceRateDto> rates = List.of(
                new com.example.Commodity_Admin_AI.dto.MarketplaceRateDto("Soybean", "Sangli", new BigDecimal("4300.0"), "Quintal", "2026-10-19"),
                new com.example.Commodity_Admin_AI.dto.MarketplaceRateDto("Soybean", "Sangli", new BigDecimal("4500.0"), "Quintal", "2026-11-30"),
                new com.example.Commodity_Admin_AI.dto.MarketplaceRateDto("Soybean", "Sangli", new BigDecimal("4650.0"), "Quintal", "2026-10-21")
        );
        rates.forEach(r -> r.setPriceType("Asking Price"));

        String result = adminChatbotService.generateCurrentRateResponse(rates, "en", null, null, "Show today's rates for all commodities.");

        assertNotNull(result);
        assertTrue(result.contains("| Soybean | ₹4650.0 | Asking Price | Sangli | 2026-10-21 |"));
        assertFalse(result.contains("2026-11-30"), "Future date 2026-11-30 must be excluded");
        assertFalse(result.contains("2026-10-19"), "Older date 2026-10-19 must be deduplicated");
    }

    @Test
    @DisplayName("extractCommodities does not mistakenly extract Rice from the word price")
    void extractCommodities_WordBoundary_DoesNotMatchRiceInPrice() {
        String q = "Which commodity has the highest asking price?";
        List<String> commodities = adminChatbotService.extractCommodities(q);
        assertNotNull(commodities);
        assertFalse(commodities.contains("Rice"), "Word 'price' must NOT match commodity 'Rice'");
        assertTrue(commodities.isEmpty());
    }

    @Test
    @DisplayName("generateHighestAskingPriceResponse never invents Rice and explains only Soybean is registered")
    void generateHighestAskingPriceResponse_OnlySoybean_NeverInventsRiceAndExplainsSingleRegisteredCommodity() {
        List<com.example.Commodity_Admin_AI.dto.MarketplaceRateDto> rates = List.of(
                new com.example.Commodity_Admin_AI.dto.MarketplaceRateDto("Soybean", "Sangli", new BigDecimal("4650.0"), "Quintal", "2026-10-21"),
                new com.example.Commodity_Admin_AI.dto.MarketplaceRateDto("Soybean", "Sangli", new BigDecimal("4800.0"), "Quintal", "2026-10-17")
        );
        rates.forEach(r -> r.setPriceType("Asking Price"));

        String result = adminChatbotService.generateHighestAskingPriceResponse(rates, "en", "Which commodity has the highest asking price?");

        assertNotNull(result);
        assertFalse(result.contains("Rice"), "Must never invent Rice");
        assertTrue(result.contains("Soybean"));
        assertTrue(result.contains("only one registered commodity has available records"));
        assertTrue(result.contains("cross-commodity comparison is not possible"));
        assertTrue(result.contains("4650"));
        assertTrue(result.contains("4800"));
    }

    @Test
    @DisplayName("generatePriceChangeResponse calculates +₹400 and +9.41% distinguishing historical record from asking price")
    void generatePriceChangeResponse_CalculatesAbsoluteAndPercentageChangeAccurately() {
        List<ChartData> data = List.of(
                new ChartData("2026-10-15", new BigDecimal("4250.0"), "Soybean", "Sangli", "Quintal", "Historical Record"),
                new ChartData("2026-10-21", new BigDecimal("4650.0"), "Soybean", "Sangli", "Quintal", "Asking Price")
        );
        AdminChatbotService.HistoricalDateWindow window = new AdminChatbotService.HistoricalDateWindow(
                java.time.LocalDate.of(2026, 10, 15),
                java.time.LocalDate.of(2026, 10, 21),
                7,
                true
        );

        String result = adminChatbotService.generatePriceChangeResponse(data, "en", "Soybean", "Sangli", window);

        assertNotNull(result);
        assertTrue(result.contains("4250"));
        assertTrue(result.contains("4650"));
        assertTrue(result.contains("+₹400 per Quintal") || result.contains("+₹400.0 per Quintal"));
        assertTrue(result.contains("+9.41%"));
        assertTrue(result.contains("Historical Record"));
        assertTrue(result.contains("Asking Price"));
    }

    @Test
    @DisplayName("extractHistoricalDateWindow parses Month-first format and hyphenated day patterns")
    void extractHistoricalDateWindow_MonthFirstAndHyphenatedPatterns() {
        java.time.LocalDate today = java.time.LocalDate.of(2026, 10, 21);

        String q1 = "How much did the Soybean asking price change between October 15 and October 21, 2026?";
        AdminChatbotService.HistoricalDateWindow w1 = adminChatbotService.extractHistoricalDateWindow(q1, today);
        assertEquals(java.time.LocalDate.of(2026, 10, 15), w1.getStartDate());
        assertEquals(java.time.LocalDate.of(2026, 10, 21), w1.getEndDate());
        assertEquals(7, w1.getRequestedDays());

        String q2 = "Show me the 5-day Soybean rate trend";
        AdminChatbotService.HistoricalDateWindow w2 = adminChatbotService.extractHistoricalDateWindow(q2, today);
        assertEquals(java.time.LocalDate.of(2026, 10, 17), w2.getStartDate());
        assertEquals(java.time.LocalDate.of(2026, 10, 21), w2.getEndDate());
        assertEquals(5, w2.getRequestedDays());
    }
}

