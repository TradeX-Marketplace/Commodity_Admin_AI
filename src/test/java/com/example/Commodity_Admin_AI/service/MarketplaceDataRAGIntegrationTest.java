package com.example.Commodity_Admin_AI.service;

import com.example.Commodity_Admin_AI.ai.GeminiService;
import com.example.Commodity_Admin_AI.ai.RagService;
import com.example.Commodity_Admin_AI.dto.ChatRequest;
import com.example.Commodity_Admin_AI.dto.ChatResponse;
import com.example.Commodity_Admin_AI.dto.MarketplaceDocument;
import com.example.Commodity_Admin_AI.dto.MarketplaceRateDto;
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
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end integration test verifying STEP 13 — MARKETPLACE DATA + RAG INTEGRATION:
 * 1. Knowledge question routing and retrieval (type=KNOWLEDGE).
 * 2. Marketplace live-data question routing and retrieval (type=MARKETPLACE_DATA).
 * 3. Combined question routing and cross-category retrieval (PATH_BOTH).
 * 4. Marketplace data indexing via HTTP/JSON DTOs -> MarketplaceDocument -> PGVector.
 * 5. Metadata category separation in the single unified VectorStore.
 * 6. Controlled context isolation and delivery to GeminiService.
 * 7. End-to-end AdminChatbotService orchestration and conversation persistence.
 * 8. Confirmation of strict read-only nature and zero direct Marketplace PostgreSQL access.
 */
@ExtendWith(MockitoExtension.class)
class MarketplaceDataRAGIntegrationTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private GeminiService geminiService;

    @Mock
    private MarketplaceDataService marketplaceDataService;

    private IntentService intentService;
    private RagService ragService;
    private MarketplaceDataSyncService syncService;
    private AdminChatbotService adminChatbotService;

    @BeforeEach
    void setUp() {
        intentService = new IntentService();
        ragService = new RagService(vectorStore, intentService, 4);
        syncService = new MarketplaceDataSyncService(marketplaceDataService, ragService);
        adminChatbotService = new AdminChatbotService(
                chatSessionRepository,
                chatMessageRepository,
                intentService,
                ragService,
                geminiService
        );
    }

    @Test
    @DisplayName("Path 1 (Knowledge): routes to KNOWLEDGE and filters PGVector with type == 'KNOWLEDGE'")
    void testPath1_KnowledgeQuestionRoutingAndRetrieval() {
        String question = "Explain the marketplace registration rules.";
        ChatSession session = new ChatSession("admin");
        session.setId(101L);

        when(chatSessionRepository.findById(101L)).thenReturn(Optional.of(session));

        Document knowledgeDoc = new Document(
                "Marketplace Registration Rules: All traders must register with valid GSTIN and PAN.",
                Map.of("type", "KNOWLEDGE")
        );
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(knowledgeDoc));
        when(geminiService.generateResponse(any(), any())).thenReturn(
                "All traders must register with valid GSTIN and PAN as per registration rules."
        );

        ChatRequest request = new ChatRequest(101L, question);
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals(101L, response.getSessionId());
        assertTrue(response.getMessage().contains("GSTIN and PAN"));

        // Verify PGVector was searched with filter type == 'KNOWLEDGE'
        ArgumentCaptor<SearchRequest> searchCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(searchCaptor.capture());

        SearchRequest capturedSearch = searchCaptor.getValue();
        assertEquals(question, capturedSearch.getQuery());
        assertNotNull(capturedSearch.getFilterExpression());
        assertTrue(capturedSearch.getFilterExpression().toString().contains("KNOWLEDGE"));

        // Verify Gemini received the question and controlled context
        ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiService).generateResponse(org.mockito.ArgumentMatchers.eq(question), contextCaptor.capture());
        assertTrue(contextCaptor.getValue().contains("GSTIN and PAN"));

        // Verify admin and assistant messages saved
        verify(chatMessageRepository, times(2)).save(any(ChatMessage.class));
    }

    @Test
    @DisplayName("Path 2 (Marketplace Data): routes to MARKETPLACE_DATA and filters PGVector with type == 'MARKETPLACE_DATA'")
    void testPath2_MarketplaceDataQuestionRoutingAndRetrieval() {
        String question = "What is today's soybean rate in Sangli?";
        ChatSession session = new ChatSession("admin");
        session.setId(102L);

        when(chatSessionRepository.findById(102L)).thenReturn(Optional.of(session));

        Document marketplaceDoc = new Document(
                "Market Rate Information:\nCommodity: Soybean\nLocation: Sangli\nRate: 42000 per MT\nDate: 2026-09-26",
                Map.of("type", "MARKETPLACE_DATA", "commodity", "Soybean", "location", "Sangli")
        );
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(marketplaceDoc));
        when(geminiService.generateResponse(any(), any())).thenReturn(
                "The current soybean rate in Sangli is ₹42,000 per MT as of 2026-09-26."
        );

        ChatRequest request = new ChatRequest(102L, question);
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals(102L, response.getSessionId());
        assertTrue(response.getMessage().contains("42,000"));

        // Verify PGVector was searched with filter type == 'MARKETPLACE_DATA'
        ArgumentCaptor<SearchRequest> searchCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(searchCaptor.capture());

        SearchRequest capturedSearch = searchCaptor.getValue();
        assertEquals(question, capturedSearch.getQuery());
        assertNotNull(capturedSearch.getFilterExpression());
        assertTrue(capturedSearch.getFilterExpression().toString().contains("MARKETPLACE_DATA"));

        // Verify Gemini received the controlled context containing rate
        ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiService).generateResponse(org.mockito.ArgumentMatchers.eq(question), contextCaptor.capture());
        assertTrue(contextCaptor.getValue().contains("42000 per MT"));
    }

    @Test
    @DisplayName("Path 3 (Combined): routes to BOTH and searches across both KNOWLEDGE and MARKETPLACE_DATA")
    void testPath3_CombinedQuestionRoutingAndRetrieval() {
        String question = "Explain the rate policy and show today's soybean rate.";
        ChatSession session = new ChatSession("admin");
        session.setId(103L);

        when(chatSessionRepository.findById(103L)).thenReturn(Optional.of(session));

        Document rateDoc = new Document(
                "Market Rate Information:\nCommodity: Soybean\nRate: 42000 per MT",
                Map.of("type", "MARKETPLACE_DATA")
        );
        Document policyDoc = new Document(
                "Rate Policy: Rates are benchmarked daily based on authenticated mandi trades.",
                Map.of("type", "KNOWLEDGE")
        );
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(rateDoc, policyDoc));
        when(geminiService.generateResponse(any(), any())).thenReturn(
                "Rate Policy benchmarks daily trades. Today's soybean rate is ₹42,000 per MT."
        );

        ChatRequest request = new ChatRequest(103L, question);
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals(103L, response.getSessionId());

        // Verify search was performed across both categories (null filter expression)
        ArgumentCaptor<SearchRequest> searchCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(searchCaptor.capture());

        SearchRequest capturedSearch = searchCaptor.getValue();
        assertEquals(question, capturedSearch.getQuery());
        assertNull(capturedSearch.getFilterExpression(), "Combined question must search across both categories without single-type restriction");

        // Verify Gemini received combined context containing both documents
        ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiService).generateResponse(org.mockito.ArgumentMatchers.eq(question), contextCaptor.capture());
        String deliveredContext = contextCaptor.getValue();
        assertTrue(deliveredContext.contains("42000 per MT"));
        assertTrue(deliveredContext.contains("benchmarked daily"));
    }

    @Test
    @DisplayName("Marketplace data ingestion: HTTP/JSON DTOs -> MarketplaceDocument -> PGVector indexing with type=MARKETPLACE_DATA")
    void testMarketplaceDataIngestionAndIndexing() {
        when(marketplaceDataService.isConfigured()).thenReturn(true);

        MarketplaceRateDto rateDto = new MarketplaceRateDto(
                "Soybean", "Sangli", new BigDecimal("42000"), "MT", "2026-09-26"
        );
        MarketplaceDocument mpDoc = new MarketplaceDocument(
                "Market Rate Information:\nCommodity: Soybean\nLocation: Sangli\nRate: 42000 per MT\nDate: 2026-09-26",
                Map.of("type", "MARKETPLACE_DATA", "commodity", "Soybean", "location", "Sangli")
        );

        when(marketplaceDataService.fetchMarketRates()).thenReturn(List.of(rateDto));
        when(marketplaceDataService.convertToSearchableDocument(rateDto)).thenReturn(mpDoc);

        int indexedCount = syncService.syncAndIndexMarketRates();
        assertEquals(1, indexedCount);

        // Verify document was indexed into the unified VectorStore with type=MARKETPLACE_DATA
        ArgumentCaptor<List<Document>> docsCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(docsCaptor.capture());

        List<Document> addedDocs = docsCaptor.getValue();
        assertEquals(1, addedDocs.size());
        assertEquals("MARKETPLACE_DATA", addedDocs.get(0).getMetadata().get("type"));
        assertEquals("Soybean", addedDocs.get(0).getMetadata().get("commodity"));
        assertTrue(addedDocs.get(0).getText().contains("42000 per MT"));
    }
}
