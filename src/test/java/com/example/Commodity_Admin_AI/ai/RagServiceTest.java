package com.example.Commodity_Admin_AI.ai;

import com.example.Commodity_Admin_AI.dto.MarketplaceDocument;
import com.example.Commodity_Admin_AI.service.IntentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagServiceTest {

    @Mock
    private VectorStore vectorStore;

    private IntentService intentService;
    private RagService ragService;

    @BeforeEach
    void setUp() {
        intentService = new IntentService();
        ragService = new RagService(vectorStore, intentService, 4);
    }

    @Test
    @DisplayName("indexKnowledge adds document to VectorStore with type=KNOWLEDGE metadata")
    void indexKnowledge_SetsKnowledgeType() {
        ragService.indexKnowledge("Corn gluten feed is a byproduct of corn starch extraction.",
                Map.of("commodity", "Corn"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());

        List<Document> addedDocs = captor.getValue();
        assertEquals(1, addedDocs.size());

        Document doc = addedDocs.get(0);
        assertTrue(doc.getText().contains("Corn gluten feed"));
        assertEquals("KNOWLEDGE", doc.getMetadata().get("type"));
        assertEquals("Corn", doc.getMetadata().get("commodity"));
    }

    @Test
    @DisplayName("indexMarketplaceDocuments converts and indexes MarketplaceDocument items with type=MARKETPLACE_DATA")
    void indexMarketplaceDocuments_SetsMarketplaceDataType() {
        MarketplaceDocument mpDoc = new MarketplaceDocument(
                "Market Rate: Soybean in Sangli is 42000 per MT",
                Map.of("commodity", "Soybean", "location", "Sangli")
        );

        ragService.indexMarketplaceDocuments(List.of(mpDoc));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());

        List<Document> addedDocs = captor.getValue();
        assertEquals(1, addedDocs.size());

        Document doc = addedDocs.get(0);
        assertTrue(doc.getText().contains("Soybean in Sangli"));
        assertEquals("MARKETPLACE_DATA", doc.getMetadata().get("type"));
        assertEquals("Soybean", doc.getMetadata().get("commodity"));
        assertEquals("Sangli", doc.getMetadata().get("location"));
    }

    @Test
    @DisplayName("retrieveContext applies type == KNOWLEDGE filter for commodity info questions")
    void retrieveContext_KnowledgeIntent_AppliesFilter() {
        Document retrievedDoc = new Document("Corn is an important cereal crop.", Map.of("type", "KNOWLEDGE"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(retrievedDoc));

        String context = ragService.retrieveContext("Tell me about Corn", IntentService.INTENT_COMMODITY_INFORMATION);

        assertNotNull(context);
        assertTrue(context.contains("Corn is an important cereal crop"));

        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(requestCaptor.capture());

        SearchRequest capturedRequest = requestCaptor.getValue();
        assertEquals("Tell me about Corn", capturedRequest.getQuery());
        assertTrue(capturedRequest.hasFilterExpression());
    }

    @Test
    @DisplayName("retrieveContext applies type == MARKETPLACE_DATA filter for rate queries")
    void retrieveContext_MarketplaceIntent_AppliesFilter() {
        Document retrievedDoc = new Document("Rate: Soybean in Sangli is 42000", Map.of("type", "MARKETPLACE_DATA"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(retrievedDoc));

        String context = ragService.retrieveContext("What is the current soybean rate in Sangli?", IntentService.INTENT_CURRENT_RATE);

        assertNotNull(context);
        assertTrue(context.contains("Rate: Soybean in Sangli is 42000"));

        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(requestCaptor.capture());

        SearchRequest capturedRequest = requestCaptor.getValue();
        assertEquals("What is the current soybean rate in Sangli?", capturedRequest.getQuery());
        assertTrue(capturedRequest.hasFilterExpression());
    }

    @Test
    @DisplayName("retrieveContext does not restrict filter when both marketplace and knowledge are requested")
    void retrieveContext_CombinedQuestion_SearchesAcrossBoth() {
        Document doc1 = new Document("Rate: Soybean 42000", Map.of("type", "MARKETPLACE_DATA"));
        Document doc2 = new Document("Soybean is a species of legume", Map.of("type", "KNOWLEDGE"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc1, doc2));

        String question = "What is the current soybean rate in Sangli and explain soybean?";
        String context = ragService.retrieveContext(question, IntentService.INTENT_CURRENT_RATE);

        assertNotNull(context);
        assertTrue(context.contains("Rate: Soybean 42000"));
        assertTrue(context.contains("Soybean is a species of legume"));

        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(requestCaptor.capture());

        SearchRequest capturedRequest = requestCaptor.getValue();
        assertNull(capturedRequest.getFilterExpression(), "Combined question should search across both categories");
    }

    @Test
    @DisplayName("retrieveContext returns empty string safely when VectorStore is inactive")
    void retrieveContext_NullVectorStore_ReturnsEmpty() {
        RagService inactiveRagService = new RagService(null, intentService, 4);
        String context = inactiveRagService.retrieveContext("What is corn?", IntentService.INTENT_COMMODITY_INFORMATION);
        assertEquals("", context);
    }

    @Test
    @DisplayName("createAdvisor creates QuestionAnswerAdvisor bound to VectorStore")
    void createAdvisor_Success() {
        QuestionAnswerAdvisor advisor = ragService.createAdvisor();
        assertNotNull(advisor);
    }

    @Test
    @DisplayName("createAdvisor with filter expression creates filtered QuestionAnswerAdvisor")
    void createAdvisor_WithFilter() {
        QuestionAnswerAdvisor advisor = ragService.createAdvisor("type == 'MARKETPLACE_DATA'");
        assertNotNull(advisor);
    }

    @Test
    @DisplayName("createAdvisorForIntent dynamically resolves filter expression for MARKETPLACE_DATA")
    void createAdvisorForIntent_MarketplaceData() {
        QuestionAnswerAdvisor advisor = ragService.createAdvisorForIntent(
                "What is the soybean rate?", IntentService.INTENT_CURRENT_RATE);
        assertNotNull(advisor);
    }

    @Test
    @DisplayName("createAdvisorForIntent dynamically resolves filter expression for KNOWLEDGE")
    void createAdvisorForIntent_Knowledge() {
        QuestionAnswerAdvisor advisor = ragService.createAdvisorForIntent(
                "What is corn gluten feed?", IntentService.INTENT_COMMODITY_INFORMATION);
        assertNotNull(advisor);
    }

    @Test
    @DisplayName("createAdvisor returns null when VectorStore is inactive")
    void createAdvisor_NullVectorStore_ReturnsNull() {
        RagService inactiveRagService = new RagService(null, intentService, 4);
        assertNull(inactiveRagService.createAdvisor());
        assertNull(inactiveRagService.createAdvisor("type == 'KNOWLEDGE'"));
        assertNull(inactiveRagService.createAdvisorForIntent("question", IntentService.INTENT_CURRENT_RATE));
    }
}
