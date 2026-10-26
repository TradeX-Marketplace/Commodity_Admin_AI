package com.example.Commodity_Admin_AI.ai;

import com.example.Commodity_Admin_AI.dto.BuyerDemandDto;
import com.example.Commodity_Admin_AI.dto.ChatRequest;
import com.example.Commodity_Admin_AI.dto.ChatResponse;
import com.example.Commodity_Admin_AI.dto.MarketplaceDocument;
import com.example.Commodity_Admin_AI.dto.MarketplaceRateDto;
import com.example.Commodity_Admin_AI.entity.ChatMessage;
import com.example.Commodity_Admin_AI.entity.ChatSession;
import com.example.Commodity_Admin_AI.repository.ChatMessageRepository;
import com.example.Commodity_Admin_AI.repository.ChatSessionRepository;
import com.example.Commodity_Admin_AI.service.AdminChatbotService;
import com.example.Commodity_Admin_AI.service.IntentService;
import com.example.Commodity_Admin_AI.service.MarketplaceDataService;
import com.example.Commodity_Admin_AI.service.MarketplaceDataSyncScheduler;
import com.example.Commodity_Admin_AI.service.MarketplaceDataSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comprehensive test suite verifying Gemini embedding quota failure handling:
 * 1. HTTP 429 RESOURCE_EXHAUSTED handling during marketplace document indexing.
 * 2. Backoff enforcement preventing repeated embedding retries during the quota window.
 * 3. Recovery after quota becomes available (quota window reset).
 * 4. HTTP 429 handling and recovery during vector similarity search retrieval.
 * 5. Document deduplication and change-detection (in-memory and PostgreSQL vector_store).
 * 6. Continued application and sync availability when indexing fails.
 * 7. Clear multilingual fallback when vector retrieval is unavailable (no false RAG claims).
 * 8. Uninterrupted non-RAG operations (rates, demands, trends, orders, greetings, write rejection).
 */
@ExtendWith(MockitoExtension.class)
class EmbeddingQuotaAndAvailabilityTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private MarketplaceDataService marketplaceDataService;

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private GeminiService geminiService;

    private IntentService intentService;
    private RagService ragService;
    private MarketplaceDataSyncService syncService;
    private MarketplaceDataSyncScheduler scheduler;
    private AdminChatbotService chatbotService;

    private final HttpClientErrorException quotaExhausted429 =
            new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS,
                    "429 RESOURCE_EXHAUSTED: Quota exceeded for quota metric 'Embeddings Requests' and limit 'Embeddings Requests per minute' of service 'generativelanguage.googleapis.com'. Please retry after 60s.");

    @BeforeEach
    void setUp() {
        intentService = new IntentService();
        ragService = new RagService(vectorStore, intentService, 4, jdbcTemplate, 60000L);
        syncService = new MarketplaceDataSyncService(marketplaceDataService, ragService);
        scheduler = new MarketplaceDataSyncScheduler(syncService);
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

    // =========================================================================
    // 1. HTTP 429 HANDLING DURING INDEXING & BACKOFF ENFORCEMENT
    // =========================================================================

    @Test
    @DisplayName("HTTP 429 during indexing: sets quota exhausted, records retry time, and does not throw exception")
    void indexMarketplaceDocuments_HandlesHttp429Gracefully() {
        MarketplaceDocument doc = new MarketplaceDocument(
                "doc-1",
                "Market Rate Information:\nCommodity: Soybean\nLocation: Sangli\nRate: 42000 per MT",
                Map.of("commodity", "Soybean", "location", "Sangli")
        );

        doThrow(quotaExhausted429).when(vectorStore).add(any());

        ragService.indexMarketplaceDocuments(List.of(doc));

        assertTrue(ragService.isQuotaExhausted(), "RagService must enter quota exhausted state");
        assertFalse(ragService.isEmbeddingQuotaAvailable(), "Embedding quota must not be available");
        assertNotNull(ragService.getQuotaRetryAfter(), "Retry time must be scheduled");
        assertTrue(ragService.getQuotaRetryAfter().isAfter(Instant.now()), "Retry time must be in the future");
    }

    @Test
    @DisplayName("Backoff enforcement: subsequent index calls during active quota window do not call VectorStore.add")
    void indexMarketplaceDocuments_DoesNotRetryDuringQuotaWindow() {
        MarketplaceDocument doc1 = new MarketplaceDocument("doc-1", "Content 1", Map.of());
        MarketplaceDocument doc2 = new MarketplaceDocument("doc-2", "Content 2", Map.of());

        doThrow(quotaExhausted429).when(vectorStore).add(any());

        // First call triggers 429
        ragService.indexMarketplaceDocuments(List.of(doc1));
        assertTrue(ragService.isQuotaExhausted());

        // Second call with different document during the same quota window
        ragService.indexMarketplaceDocuments(List.of(doc2));

        // VectorStore.add must only have been invoked ONCE (the failed first call), never retried during window
        verify(vectorStore, times(1)).add(any());
    }

    // =========================================================================
    // 2. RECOVERY AFTER QUOTA BECOMES AVAILABLE
    // =========================================================================

    @Test
    @DisplayName("Recovery after quota becomes available: clears exhausted state and resumes indexing successfully")
    void indexMarketplaceDocuments_RecoversAfterQuotaBecomesAvailable() {
        MarketplaceDocument doc1 = new MarketplaceDocument("doc-1", "Content 1", Map.of());
        MarketplaceDocument doc2 = new MarketplaceDocument("doc-2", "Content 2", Map.of());

        doThrow(quotaExhausted429).when(vectorStore).add(any());

        // 1. Quota exhaustion occurs
        ragService.indexMarketplaceDocuments(List.of(doc1));
        assertTrue(ragService.isQuotaExhausted());

        // 2. Quota becomes available (e.g. window resets)
        ragService.resetQuotaStatus();
        assertFalse(ragService.isQuotaExhausted());
        assertTrue(ragService.isEmbeddingQuotaAvailable());
        assertNull(ragService.getQuotaRetryAfter());

        // 3. Retry succeeds
        org.mockito.Mockito.reset(vectorStore);
        ragService.indexMarketplaceDocuments(List.of(doc2));

        verify(vectorStore, times(1)).add(any());
        assertFalse(ragService.isQuotaExhausted());
    }

    // =========================================================================
    // 3. HTTP 429 HANDLING & RECOVERY DURING RETRIEVAL
    // =========================================================================

    @Test
    @DisplayName("HTTP 429 during retrieval: handles exception safely, enters backoff, and returns empty list")
    void retrieveDocuments_HandlesHttp429Gracefully() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenThrow(quotaExhausted429);

        List<Document> result = ragService.retrieveDocuments("What is the soybean rate?", "type == 'MARKETPLACE_DATA'", 4);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertTrue(ragService.isQuotaExhausted());
        assertFalse(ragService.isVectorRetrievalAvailable());

        // Subsequent retrieval during quota window should not even call VectorStore.similaritySearch
        List<Document> secondResult = ragService.retrieveDocuments("Another question", null, 4);
        assertTrue(secondResult.isEmpty());
        verify(vectorStore, times(1)).similaritySearch(any(SearchRequest.class));
    }

    @Test
    @DisplayName("Retrieval recovers after quota becomes available")
    void retrieveDocuments_RecoversAfterQuotaReset() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenThrow(quotaExhausted429);

        ragService.retrieveDocuments("question 1", null, 4);
        assertTrue(ragService.isQuotaExhausted());

        // Quota window expires / resets
        ragService.resetQuotaStatus();
        assertFalse(ragService.isQuotaExhausted());

        Document doc = new Document("Soybean Rate: 42000", Map.of("type", "MARKETPLACE_DATA"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        List<Document> recoveredResult = ragService.retrieveDocuments("question 2", null, 4);
        assertEquals(1, recoveredResult.size());
        assertEquals("Soybean Rate: 42000", recoveredResult.get(0).getText());
    }

    // =========================================================================
    // 4. DEDUPLICATION & CHANGE DETECTION
    // =========================================================================

    @Test
    @DisplayName("Deduplication: unchanged documents are not repeatedly embedded")
    void indexMarketplaceDocuments_DeduplicatesUnchangedDocuments() {
        MarketplaceDocument doc = new MarketplaceDocument(
                "doc-rate-1",
                "Market Rate Information:\nCommodity: Soybean\nLocation: Sangli\nRate: 42000 per MT",
                Map.of("commodity", "Soybean")
        );

        // First indexing call -> embeds document
        ragService.indexMarketplaceDocuments(List.of(doc));
        verify(vectorStore, times(1)).add(any());

        // Second indexing call with identical document -> skipped!
        ragService.indexMarketplaceDocuments(List.of(doc));
        verify(vectorStore, times(1)).add(any()); // Still 1 invocation!

        // Third indexing call with modified content -> embedded!
        MarketplaceDocument modifiedDoc = new MarketplaceDocument(
                "doc-rate-1",
                "Market Rate Information:\nCommodity: Soybean\nLocation: Sangli\nRate: 43500 per MT",
                Map.of("commodity", "Soybean")
        );
        ragService.indexMarketplaceDocuments(List.of(modifiedDoc));
        verify(vectorStore, times(2)).add(any()); // Incremented to 2
    }

    @Test
    @DisplayName("Database change-detection: pre-existing documents in PostgreSQL vector_store are skipped")
    void indexMarketplaceDocuments_DatabaseChangeDetection_SkipsExisting() {
        String docId = "doc-existing-1";
        String content = "Market Rate Information:\nCommodity: Wheat\nLocation: Pune\nRate: 2800 per MT";

        MarketplaceDocument doc = new MarketplaceDocument(docId, content, Map.of("commodity", "Wheat"));

        // Simulate database lookup returning the existing row with matching content
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            org.springframework.jdbc.support.rowset.SqlRowSet mockRowSet = org.mockito.Mockito.mock(org.springframework.jdbc.support.rowset.SqlRowSet.class);
            java.sql.ResultSet rs = org.mockito.Mockito.mock(java.sql.ResultSet.class);
            when(rs.getString("id")).thenReturn(docId);
            when(rs.getString("content")).thenReturn(content);
            handler.processRow(rs);
            return null;
        }).when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));

        ragService.indexMarketplaceDocuments(List.of(doc));

        // VectorStore.add must never be called because document exists unchanged in vector_store table
        verify(vectorStore, never()).add(any());
    }

    // =========================================================================
    // 5. CONTINUED APPLICATION & SCHEDULER AVAILABILITY ON FAILURE
    // =========================================================================

    @Test
    @DisplayName("Sync service: continued availability on 429 quota exhaustion")
    void syncAndIndexMarketRates_ContinuesAvailabilityOnQuotaExhaustion() {
        when(marketplaceDataService.isConfigured()).thenReturn(true);
        MarketplaceRateDto rateDto = new MarketplaceRateDto("Soybean", "Sangli", new BigDecimal("42000"), "MT", "2026-10-14");
        MarketplaceDocument doc = new MarketplaceDocument("doc-soybean", "Content", Map.of("commodity", "Soybean"));

        when(marketplaceDataService.fetchMarketRates()).thenReturn(List.of(rateDto));
        when(marketplaceDataService.convertToSearchableDocument(rateDto)).thenReturn(doc);

        doThrow(quotaExhausted429).when(vectorStore).add(any());

        // Initial sync encounters 429
        int count = syncService.syncAndIndexMarketRates();
        assertEquals(1, count, "Fetched document count should be returned even when indexing is deferred");
        assertEquals("QUOTA_EXHAUSTED", syncService.getLastSyncStatus());
        assertTrue(ragService.isQuotaExhausted());

        // Subsequent sync during quota window skips vector indexing cleanly without retrying
        int secondCount = syncService.syncAndIndexMarketRates();
        assertEquals(1, secondCount);
        assertEquals("QUOTA_EXHAUSTED", syncService.getLastSyncStatus());
    }

    @Test
    @DisplayName("Scheduler: handles startup and periodic sync safely when quota exhausted")
    void scheduler_StartupAndScheduledSync_SafeUnderQuotaExhaustion() {
        when(marketplaceDataService.isConfigured()).thenReturn(true);
        MarketplaceRateDto rateDto = new MarketplaceRateDto("Soybean", "Sangli", new BigDecimal("42000"), "MT", "2026-10-14");
        MarketplaceDocument doc = new MarketplaceDocument("doc-soybean", "Content", Map.of("commodity", "Soybean"));

        when(marketplaceDataService.fetchMarketRates()).thenReturn(List.of(rateDto));
        when(marketplaceDataService.convertToSearchableDocument(rateDto)).thenReturn(doc);

        doThrow(quotaExhausted429).when(vectorStore).add(any());

        // Application startup runner
        scheduler.run(new DefaultApplicationArguments(new String[0]));
        assertFalse(scheduler.isSyncInProgress(), "Sync in progress must be released after completion");

        // Scheduled recurring sync
        scheduler.scheduledSync();
        assertFalse(scheduler.isSyncInProgress(), "Sync in progress must be released after scheduled run");
    }

    // =========================================================================
    // 6. CLEAR MULTILINGUAL FALLBACK WHEN VECTOR RETRIEVAL UNAVAILABLE
    // =========================================================================

    @Test
    @DisplayName("Admin chatbot: returns clear English fallback when vector retrieval is unavailable (no false RAG claims)")
    void processChat_KnowledgeQuery_QuotaExhausted_ReturnsClearEnglishFallback() {
        ragService.recordQuotaExhaustion(Duration.ofMinutes(1));
        ChatSession session = new ChatSession("admin");
        session.setId(201L);
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(session);

        ChatRequest request = new ChatRequest(201L, "Explain the marketplace registration rules and specifications.");
        request.setLanguage("English");

        ChatResponse response = chatbotService.processChat(request);

        assertNotNull(response);
        assertEquals(201L, response.getSessionId());
        assertEquals("UNAVAILABLE", response.getDisplayType());
        assertTrue(response.getMessage().contains("temporarily unavailable"), "Must inform user retrieval is temporarily unavailable");
        assertTrue(response.getMessage().contains("quota is exhausted"), "Must clearly cite embedding quota limitation");
        assertTrue(response.getMessage().contains("Direct marketplace data services"), "Must mention direct services remain functional");

        // Gemini LLM was NOT called with empty context to generate false missing-data claims
        verify(geminiService, never()).generateResponse(anyString(), anyString());
    }

    @Test
    @DisplayName("Admin chatbot: returns clear Marathi fallback when vector retrieval is unavailable")
    void processChat_KnowledgeQuery_QuotaExhausted_ReturnsClearMarathiFallback() {
        ragService.recordQuotaExhaustion(Duration.ofMinutes(1));
        ChatSession session = new ChatSession("admin");
        session.setId(202L);
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(session);

        ChatRequest request = new ChatRequest(202L, "बाजारपेठेतील नोंदणीचे नियम काय आहेत?");
        request.setLanguage("Marathi");

        ChatResponse response = chatbotService.processChat(request);

        assertNotNull(response);
        assertEquals(202L, response.getSessionId());
        assertEquals("UNAVAILABLE", response.getDisplayType());
        assertTrue(response.getMessage().contains("कोटा संपल्यामुळे"), "Must explain quota exhaustion in Marathi");
        assertTrue(response.getMessage().contains("थेट बाजारभाव"), "Must affirm non-RAG services remain operational in Marathi");
    }

    @Test
    @DisplayName("Admin chatbot: returns clear Hindi fallback when vector retrieval is unavailable")
    void processChat_KnowledgeQuery_QuotaExhausted_ReturnsClearHindiFallback() {
        ragService.recordQuotaExhaustion(Duration.ofMinutes(1));
        ChatSession session = new ChatSession("admin");
        session.setId(203L);
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(session);

        ChatRequest request = new ChatRequest(203L, "मार्केटप्लेस रजिस्ट्रेशन के नियम और विवरण क्या हैं?");
        request.setLanguage("Hindi");

        ChatResponse response = chatbotService.processChat(request);

        assertNotNull(response);
        assertEquals(203L, response.getSessionId());
        assertEquals("UNAVAILABLE", response.getDisplayType());
        assertTrue(response.getMessage().contains("कोटा सीमा समाप्त होने के कारण"), "Must explain quota exhaustion in Hindi");
        assertTrue(response.getMessage().contains("लाइव मंडी भाव"), "Must affirm non-RAG services remain operational in Hindi");
    }

    // =========================================================================
    // 7. UNINTERRUPTED NON-RAG FEATURES DURING QUOTA EXHAUSTION
    // =========================================================================

    @Test
    @DisplayName("Non-RAG operations: Greeting works uninterrupted during quota exhaustion")
    void nonRag_Greeting_WorksDuringQuotaExhaustion() {
        ragService.recordQuotaExhaustion(Duration.ofMinutes(1));
        ChatSession session = new ChatSession("admin");
        session.setId(301L);
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(session);

        ChatRequest request = new ChatRequest(301L, "Hello, good morning!");
        ChatResponse response = chatbotService.processChat(request);

        assertNotNull(response);
        assertTrue(response.getMessage().contains("Commodity Marketplace information"));
    }

    @Test
    @DisplayName("Non-RAG operations: Write-rejection works uninterrupted during quota exhaustion")
    void nonRag_WriteOperationRejection_WorksDuringQuotaExhaustion() {
        ragService.recordQuotaExhaustion(Duration.ofMinutes(1));
        ChatSession session = new ChatSession("admin");
        session.setId(302L);
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(session);

        ChatRequest request = new ChatRequest(302L, "Update rate for Soybean to 45000");
        ChatResponse response = chatbotService.processChat(request);

        assertNotNull(response);
        assertTrue(response.getMessage().contains("READ-ONLY"));
    }

    @Test
    @DisplayName("Non-RAG operations: Current rate query works uninterrupted using direct marketplace data")
    void nonRag_CurrentRate_WorksDuringQuotaExhaustion() {
        ragService.recordQuotaExhaustion(Duration.ofMinutes(1));
        ChatSession session = new ChatSession("admin");
        session.setId(303L);
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(session);

        MarketplaceRateDto rate = new MarketplaceRateDto("Soybean", "Sangli", new BigDecimal("4250.00"), "Quintal", "2026-10-14");
        when(marketplaceDataService.fetchMarketRates()).thenReturn(List.of(rate));

        ChatRequest request = new ChatRequest(303L, "What is today's soybean rate in Sangli?");
        ChatResponse response = chatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("RATE", response.getDisplayType());
        assertTrue(response.getMessage().contains("4250.00"));
        assertTrue(response.getMessage().contains("Soybean"));
    }

    @Test
    @DisplayName("Non-RAG operations: Buyer demand query works uninterrupted using direct marketplace data")
    void nonRag_BuyerDemand_WorksDuringQuotaExhaustion() {
        ragService.recordQuotaExhaustion(Duration.ofMinutes(1));
        ChatSession session = new ChatSession("admin");
        session.setId(304L);
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(session);

        BuyerDemandDto demand = new BuyerDemandDto(1L, "ABC Foods", 1L, "Soybean", 500.0, "Quintal", "Sangli", "2026-10-20", "OPEN", "2026-10-14");
        when(marketplaceDataService.fetchBuyerDemands("Soybean", "Sangli")).thenReturn(List.of(demand));

        ChatRequest request = new ChatRequest(304L, "What is the demand for Soybean in Sangli?");
        ChatResponse response = chatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("DEMAND", response.getDisplayType());
        assertTrue(response.getMessage().contains("ABC Foods"));
        assertTrue(response.getMessage().contains("Soybean"));
    }

    @Test
    @DisplayName("Non-RAG operations: Orders query works uninterrupted during quota exhaustion")
    void nonRag_Orders_WorksDuringQuotaExhaustion() {
        ragService.recordQuotaExhaustion(Duration.ofMinutes(1));
        ChatSession session = new ChatSession("admin");
        session.setId(305L);
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(session);

        ChatRequest request = new ChatRequest(305L, "Show placed orders for Soybean in Sangli");
        ChatResponse response = chatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("ORDERS", response.getDisplayType());
        assertTrue(response.getMessage().contains("placed order data"));
        assertTrue(response.getMessage().contains("Orders API"));
    }
}
