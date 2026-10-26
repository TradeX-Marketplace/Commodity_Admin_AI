package com.example.Commodity_Admin_AI.ai;

import com.example.Commodity_Admin_AI.config.RagAdvisorConfig;
import com.example.Commodity_Admin_AI.dto.MarketplaceDocument;
import com.example.Commodity_Admin_AI.service.IntentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service managing RAG retrieval and vector indexing in PGVector via Spring AI VectorStore.
 * Maintains a single unified PGVector system storing:
 * 1. KNOWLEDGE data (commodity explanations, specs, business rules, static documentation)
 * 2. MARKETPLACE_DATA (indexed rates, demands, listings, notifications, trends)
 * <p>
 * Uses metadata filtering ('type == KNOWLEDGE' vs 'type == MARKETPLACE_DATA') to isolate
 * or combine contexts based on intent.
 * <p>
 * EMBEDDING QUOTA & RESILIENCE:
 * - Gracefully intercepts and handles Gemini embedding quota exhaustion (HTTP 429 RESOURCE_EXHAUSTED).
 * - Tracks quota window and retry-after times, enforcing backoff to prevent repeated calls during exhaustion.
 * - Implements deduplication and change-detection against both in-memory fingerprints and the existing
 *   PGVector schema (`vector_store` table) to prevent re-embedding unchanged documents.
 * - Ensures REST API and non-RAG operations continue operating without interruption.
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    public static final String METADATA_TYPE = "type";
    public static final String TYPE_KNOWLEDGE = "KNOWLEDGE";
    public static final String TYPE_MARKETPLACE_DATA = "MARKETPLACE_DATA";

    private final VectorStore vectorStore;
    private final IntentService intentService;
    private final int defaultTopK;
    private final JdbcTemplate jdbcTemplate;
    private final long quotaBackoffMs;

    // Quota backoff state management
    private final AtomicReference<Instant> quotaRetryAfter = new AtomicReference<>(null);
    private final AtomicBoolean quotaExhausted = new AtomicBoolean(false);

    // In-memory document fingerprint cache: dedupeKey -> SHA-256 hex string
    private final ConcurrentMap<String, String> indexedDocumentHashes = new ConcurrentHashMap<>();

    @Autowired
    public RagService(
            @Autowired(required = false) VectorStore vectorStore,
            IntentService intentService,
            @Value("${spring.ai.vectorstore.pgvector.top-k:4}") int defaultTopK,
            @Autowired(required = false) JdbcTemplate jdbcTemplate,
            @Value("${rag.embedding.quota-backoff-ms:60000}") long quotaBackoffMs) {
        this.vectorStore = vectorStore;
        this.intentService = intentService;
        this.defaultTopK = defaultTopK > 0 ? defaultTopK : 4;
        this.jdbcTemplate = jdbcTemplate;
        this.quotaBackoffMs = quotaBackoffMs > 0 ? quotaBackoffMs : 60000L;
    }

    public RagService(
            VectorStore vectorStore,
            IntentService intentService,
            int defaultTopK,
            JdbcTemplate jdbcTemplate) {
        this(vectorStore, intentService, defaultTopK, jdbcTemplate, 60000L);
    }

    public RagService(
            VectorStore vectorStore,
            IntentService intentService,
            int defaultTopK) {
        this(vectorStore, intentService, defaultTopK, null, 60000L);
    }

    /**
     * Retrieves controlled context from PGVector based on the question and detected intent.
     * Determines whether knowledge, marketplace data, or both should be searched.
     *
     * @param question the natural language question
     * @param intent the detected question intent
     * @return formatted context string ready for LLM consumption
     */
    public String retrieveContext(String question, String intent) {
        if (question == null || question.isBlank()) {
            return "";
        }

        if (vectorStore == null) {
            log.info("VectorStore bean is not active. Returning empty RAG context.");
            return "";
        }

        if (isQuotaExhausted()) {
            log.warn("[RAG] Vector retrieval unavailable: Embedding quota exhausted until {}. Returning empty context.",
                    quotaRetryAfter.get());
            return "";
        }

        String retrievalPath = intentService != null
                ? intentService.determineRetrievalPath(question, intent)
                : IntentService.PATH_UNKNOWN;

        String filterExpression = resolveFilterExpression(retrievalPath);
        log.info("[RAG] Intent: {}", intent);
        log.info("[RAG] Retrieval filter: {}", filterExpression != null ? filterExpression : "NONE");

        int topK = resolveTopK(question, defaultTopK);
        List<Document> documents = retrieveDocuments(question, filterExpression, topK);
        log.info("[RAG] Documents retrieved: {}", documents != null ? documents.size() : 0);

        String formatted = formatDocuments(documents);
        log.info("[RAG] Context length: {}", formatted != null ? formatted.length() : 0);

        return formatted;
    }

    private int resolveTopK(String question, int fallbackTopK) {
        if (question == null) {
            return fallbackTopK;
        }
        String lower = question.toLowerCase(Locale.ROOT);
        if (lower.contains("all") || lower.contains("trend") || lower.contains("history") || lower.contains("7 day") || lower.contains("7-day")
                || lower.contains("7 दिवस") || lower.contains("७ दिवस") || lower.contains("दिवसांचे") || lower.contains("दिवसांचा")
                || lower.contains("गेल्या") || lower.contains("मागील") || lower.contains("7 दिन") || lower.contains("७ दिन") || lower.contains("पिछले")) {
            return Math.max(fallbackTopK, 12);
        }
        return fallbackTopK;
    }

    /**
     * Performs similarity search against PGVector with optional metadata filtering.
     *
     * @param query the query text
     * @param filterExpression optional Spring AI metadata filter expression (e.g. "type == 'KNOWLEDGE'")
     * @param topK maximum number of documents to retrieve
     * @return list of matching Document instances
     */
    public List<Document> retrieveDocuments(String query, String filterExpression, int topK) {
        if (vectorStore == null || query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        if (isQuotaExhausted()) {
            log.warn("[RAG] Skipping similarity search: Embedding quota window active until {} (HTTP 429).",
                    quotaRetryAfter.get());
            return Collections.emptyList();
        }

        int k = topK > 0 ? topK : defaultTopK;
        SearchRequest.Builder requestBuilder = SearchRequest.builder()
                .query(query)
                .topK(k);

        if (filterExpression != null && !filterExpression.isBlank()) {
            requestBuilder.filterExpression(filterExpression);
        }

        try {
            List<Document> results = vectorStore.similaritySearch(requestBuilder.build());
            if (quotaExhausted.getAndSet(false)) {
                quotaRetryAfter.set(null);
                log.info("Gemini embedding quota recovered during similarity search.");
            }
            return results != null ? results : Collections.emptyList();
        } catch (Exception ex) {
            if (isQuotaExhaustionException(ex)) {
                Duration retryDuration = extractRetryDuration(ex, Duration.ofMillis(quotaBackoffMs));
                recordQuotaExhaustion(retryDuration);
                log.warn("[RAG] Similarity search failed due to embedding quota exhaustion (HTTP 429 RESOURCE_EXHAUSTED). Quota window active until {}: {}",
                        quotaRetryAfter.get(), ex.getMessage());
            } else {
                log.error("Similarity search failed in PGVector: {}", ex.getMessage());
            }
            return Collections.emptyList();
        }
    }

    /**
     * Creates a Spring AI QuestionAnswerAdvisor bound to the unified PGVector store.
     *
     * @return configured QuestionAnswerAdvisor, or null if VectorStore is inactive
     */
    public QuestionAnswerAdvisor createAdvisor() {
        return createAdvisor(null, defaultTopK);
    }

    /**
     * Creates a Spring AI QuestionAnswerAdvisor with a metadata filter expression.
     *
     * @param filterExpression metadata filter expression
     * @return configured QuestionAnswerAdvisor, or null if VectorStore is inactive
     */
    public QuestionAnswerAdvisor createAdvisor(String filterExpression) {
        return createAdvisor(filterExpression, defaultTopK);
    }

    /**
     * Creates a Spring AI QuestionAnswerAdvisor dynamically scoped to the detected intent.
     *
     * @param question the admin question
     * @param intent the detected intent
     * @return configured QuestionAnswerAdvisor, or null if VectorStore is inactive
     */
    public QuestionAnswerAdvisor createAdvisorForIntent(String question, String intent) {
        if (vectorStore == null) {
            return null;
        }
        String retrievalPath = intentService != null
                ? intentService.determineRetrievalPath(question, intent)
                : IntentService.PATH_UNKNOWN;
        String filterExpression = resolveFilterExpression(retrievalPath);
        return createAdvisor(filterExpression, defaultTopK);
    }

    /**
     * Helper to build a QuestionAnswerAdvisor with given filter expression and topK.
     *
     * @param filterExpression optional metadata filter expression
     * @param topK maximum number of documents to retrieve
     * @return configured QuestionAnswerAdvisor, or null if VectorStore is inactive
     */
    public QuestionAnswerAdvisor createAdvisor(String filterExpression, int topK) {
        if (vectorStore == null) {
            log.warn("Cannot create QuestionAnswerAdvisor: VectorStore is inactive.");
            return null;
        }

        int k = topK > 0 ? topK : defaultTopK;
        SearchRequest.Builder requestBuilder = SearchRequest.builder().topK(k);
        if (filterExpression != null && !filterExpression.isBlank()) {
            requestBuilder.filterExpression(filterExpression);
        }

        return QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(requestBuilder.build())
                .promptTemplate(new PromptTemplate(RagAdvisorConfig.CONTROLLED_ADVISOR_TEMPLATE))
                .build();
    }

    /**
     * Indexes a knowledge document into PGVector.
     * Automatically tags the document with metadata type = KNOWLEDGE.
     *
     * @param content the knowledge text content
     * @param metadata custom metadata key-value pairs
     */
    public void indexKnowledge(String content, Map<String, Object> metadata) {
        if (vectorStore == null || content == null || content.isBlank()) {
            log.warn("Cannot index knowledge document: VectorStore inactive or content empty.");
            return;
        }

        if (isQuotaExhausted()) {
            log.warn("Skipping knowledge document indexing: Embedding quota window active until {} (HTTP 429).",
                    quotaRetryAfter.get());
            return;
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        if (metadata != null) {
            meta.putAll(metadata);
        }
        meta.put(METADATA_TYPE, TYPE_KNOWLEDGE);

        Document doc = new Document(content, meta);
        try {
            vectorStore.add(List.of(doc));
            if (quotaExhausted.getAndSet(false)) {
                quotaRetryAfter.set(null);
                log.info("Gemini embedding quota recovered during knowledge indexing.");
            }
            log.debug("Indexed 1 KNOWLEDGE document into PGVector.");
        } catch (Exception ex) {
            if (isQuotaExhaustionException(ex)) {
                Duration retryDuration = extractRetryDuration(ex, Duration.ofMillis(quotaBackoffMs));
                recordQuotaExhaustion(retryDuration);
                log.warn("Knowledge indexing failed due to embedding quota exhaustion (HTTP 429 RESOURCE_EXHAUSTED). Will retry after: {}",
                        quotaRetryAfter.get());
            } else {
                log.error("Failed to index knowledge document into PGVector: {}", ex.getMessage(), ex);
                throw ex;
            }
        }
    }

    /**
     * Indexes approved marketplace documents into PGVector with deduplication and 429 quota protection.
     * Automatically tags all documents with metadata type = MARKETPLACE_DATA.
     *
     * @param marketplaceDocuments list of prepared MarketplaceDocument objects
     */
    public void indexMarketplaceDocuments(List<MarketplaceDocument> marketplaceDocuments) {
        if (vectorStore == null || marketplaceDocuments == null || marketplaceDocuments.isEmpty()) {
            log.debug("Skipping marketplace indexing: VectorStore inactive or document list empty.");
            return;
        }

        if (isQuotaExhausted()) {
            log.warn("Skipping marketplace document indexing: Embedding quota window active until {} (HTTP 429 backoff).",
                    quotaRetryAfter.get());
            return;
        }

        List<MarketplaceDocument> validDocs = new ArrayList<>();
        List<String> idsToCheck = new ArrayList<>();
        for (MarketplaceDocument mpDoc : marketplaceDocuments) {
            if (mpDoc != null && mpDoc.getContent() != null && !mpDoc.getContent().isBlank()) {
                validDocs.add(mpDoc);
                if (mpDoc.getId() != null && !mpDoc.getId().isBlank()) {
                    idsToCheck.add(mpDoc.getId());
                }
            }
        }

        if (validDocs.isEmpty()) {
            return;
        }

        // Preload existing documents from PostgreSQL vector_store table in a single query if available
        Map<String, String> dbDocs = getExistingDatabaseDocuments(idsToCheck);

        List<Document> springDocuments = new ArrayList<>();
        List<MarketplaceDocument> documentsToEmbed = new ArrayList<>();

        for (MarketplaceDocument mpDoc : validDocs) {
            String docId = mpDoc.getId();
            String content = mpDoc.getContent();
            String currentHash = computeHash(content);
            String dedupeKey = (docId != null && !docId.isBlank()) ? docId : "content:" + currentHash;

            // 1. In-memory fingerprint cache check
            if (currentHash.equals(indexedDocumentHashes.get(dedupeKey))) {
                continue; // Unchanged!
            }

            // 2. PostgreSQL vector_store database check
            if (docId != null && dbDocs.containsKey(docId)) {
                String dbContent = dbDocs.get(docId);
                if (content.equals(dbContent)) {
                    indexedDocumentHashes.put(dedupeKey, currentHash);
                    continue; // Unchanged in database!
                }
            }

            // Document is new or changed: prepare for embedding
            documentsToEmbed.add(mpDoc);
            Map<String, Object> meta = new LinkedHashMap<>(mpDoc.getMetadata());
            meta.put(METADATA_TYPE, TYPE_MARKETPLACE_DATA);
            if (docId != null && !docId.isBlank()) {
                springDocuments.add(new Document(docId, content, meta));
            } else {
                springDocuments.add(new Document(content, meta));
            }
        }

        if (springDocuments.isEmpty()) {
            log.info("Marketplace document indexing: all {} documents are unchanged. Skipping vector embedding call.",
                    validDocs.size());
            return;
        }

        log.info("Embedding and indexing {} new/updated MARKETPLACE_DATA documents ({} skipped as unchanged).",
                springDocuments.size(), validDocs.size() - springDocuments.size());

        try {
            vectorStore.add(springDocuments);

            // Record hashes of successfully indexed documents
            for (MarketplaceDocument indexedDoc : documentsToEmbed) {
                String dedupeKey = (indexedDoc.getId() != null && !indexedDoc.getId().isBlank())
                        ? indexedDoc.getId()
                        : "content:" + computeHash(indexedDoc.getContent());
                indexedDocumentHashes.put(dedupeKey, computeHash(indexedDoc.getContent()));
            }

            if (quotaExhausted.getAndSet(false)) {
                quotaRetryAfter.set(null);
                log.info("Gemini embedding quota recovered. Marketplace documents indexed successfully.");
            }
            log.info("Indexed {} MARKETPLACE_DATA documents into PGVector.", springDocuments.size());
        } catch (Exception ex) {
            if (isQuotaExhaustionException(ex)) {
                Duration retryDuration = extractRetryDuration(ex, Duration.ofMillis(quotaBackoffMs));
                recordQuotaExhaustion(retryDuration);
                log.warn("Gemini embedding quota exhausted during document indexing (HTTP 429 RESOURCE_EXHAUSTED). Embedding calls suspended until {}. Details: {}",
                        quotaRetryAfter.get(), ex.getMessage());
            } else {
                log.error("Failed to index marketplace documents into PGVector: {}", ex.getMessage(), ex);
                throw ex;
            }
        }
    }

    /**
     * Checks if a document with the given ID and content is already indexed and unchanged.
     *
     * @param id document identifier
     * @param content text content of the document
     * @return true if document exists with identical content, false otherwise
     */
    public boolean isDocumentUnchanged(String id, String content) {
        if (id == null || id.isBlank() || content == null) {
            return false;
        }

        String currentHash = computeHash(content);
        String cachedHash = indexedDocumentHashes.get(id);
        if (currentHash.equals(cachedHash)) {
            return true;
        }

        if (jdbcTemplate != null) {
            try {
                String sql = "SELECT content FROM vector_store WHERE id::text = ?";
                List<String> results = jdbcTemplate.query(
                        sql,
                        (rs, rowNum) -> rs.getString("content"),
                        id
                );
                if (!results.isEmpty()) {
                    String existingContent = results.get(0);
                    if (content.equals(existingContent)) {
                        indexedDocumentHashes.put(id, currentHash);
                        return true;
                    }
                }
            } catch (Exception ex) {
                log.debug("Database verification for document id {} skipped: {}", id, ex.getMessage());
            }
        }

        return false;
    }

    /**
     * Queries PostgreSQL vector_store table in a single batch to retrieve existing document contents.
     *
     * @param ids list of document IDs
     * @return map of id -> content for existing documents
     */
    public Map<String, String> getExistingDatabaseDocuments(List<String> ids) {
        if (jdbcTemplate == null || ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> found = new LinkedHashMap<>();
        try {
            String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
            String sql = "SELECT id::text, content FROM vector_store WHERE id::text IN (" + placeholders + ")";
            jdbcTemplate.query(sql, rs -> {
                found.put(rs.getString("id"), rs.getString("content"));
            }, ids.toArray());
        } catch (Exception ex) {
            log.debug("Batch document lookup from vector_store skipped: {}", ex.getMessage());
        }
        return found;
    }

    /**
     * Records a quota exhaustion event and sets the backoff window.
     *
     * @param duration duration to wait before retrying embedding calls
     */
    public void recordQuotaExhaustion(Duration duration) {
        Duration effectiveDuration = (duration != null && !duration.isNegative() && !duration.isZero())
                ? duration
                : Duration.ofMillis(quotaBackoffMs);
        Instant retryTime = Instant.now().plus(effectiveDuration);
        quotaRetryAfter.set(retryTime);
        quotaExhausted.set(true);
        log.warn("Gemini embedding quota exhausted (HTTP 429 RESOURCE_EXHAUSTED). Embedding operations suspended until {} (retry after {}s).",
                retryTime, effectiveDuration.toSeconds());
    }

    /**
     * Resets quota status (used on recovery or in tests).
     */
    public void resetQuotaStatus() {
        quotaRetryAfter.set(null);
        quotaExhausted.set(false);
        log.info("Embedding quota status reset. Vector operations available.");
    }

    /**
     * Returns true if embedding quota is currently exhausted and in backoff window.
     *
     * @return true if quota is exhausted, false otherwise
     */
    public boolean isQuotaExhausted() {
        Instant retryAfter = quotaRetryAfter.get();
        if (retryAfter == null) {
            return false;
        }
        if (Instant.now().isAfter(retryAfter)) {
            return false;
        }
        return true;
    }

    /**
     * Returns true if embedding operations can be attempted.
     *
     * @return true if quota is available, false if in backoff
     */
    public boolean isEmbeddingQuotaAvailable() {
        return !isQuotaExhausted();
    }

    /**
     * Returns the timestamp when embedding calls can be retried.
     *
     * @return retry Instant or null if not exhausted
     */
    public Instant getQuotaRetryAfter() {
        return quotaRetryAfter.get();
    }

    /**
     * Returns true if vector retrieval is configured and quota is available.
     *
     * @return true if vector search can be performed
     */
    public boolean isVectorRetrievalAvailable() {
        return vectorStore != null && !isQuotaExhausted();
    }

    /**
     * Inspects an exception to determine if it represents an HTTP 429 / Quota Exhaustion error.
     *
     * @param t throwable to check
     * @return true if 429 / RESOURCE_EXHAUSTED
     */
    public static boolean isQuotaExhaustionException(Throwable t) {
        if (t == null) {
            return false;
        }
        Integer statusCode = extractHttpStatusCode(t);
        if (statusCode != null && statusCode == 429) {
            return true;
        }
        String msg = t.getMessage();
        if (msg != null) {
            String lower = msg.toLowerCase(Locale.ROOT);
            if (lower.contains("429") || lower.contains("resource_exhausted") || lower.contains("quota exceeded")
                    || lower.contains("rate limit") || lower.contains("too many requests") || lower.contains("quota")) {
                return true;
            }
        }
        if (t.getCause() != null && t.getCause() != t) {
            return isQuotaExhaustionException(t.getCause());
        }
        return false;
    }

    /**
     * Extracts HTTP status code from exception hierarchy if available.
     *
     * @param t throwable
     * @return HTTP status integer or null
     */
    public static Integer extractHttpStatusCode(Throwable t) {
        if (t == null) {
            return null;
        }
        if (t instanceof org.springframework.web.client.HttpStatusCodeException httpEx) {
            return httpEx.getStatusCode().value();
        }
        if (t instanceof org.springframework.web.client.RestClientResponseException restEx) {
            return restEx.getStatusCode().value();
        }
        String msg = t.getMessage();
        if (msg != null) {
            Matcher m = Pattern.compile("\\b([45]\\d{2})\\b").matcher(msg);
            if (m.find()) {
                try {
                    return Integer.parseInt(m.group(1));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (t.getCause() != null && t.getCause() != t) {
            return extractHttpStatusCode(t.getCause());
        }
        return null;
    }

    /**
     * Extracts retry duration from exception message or headers, falling back to default.
     *
     * @param t throwable
     * @param defaultDuration fallback duration
     * @return extracted or fallback duration
     */
    public static Duration extractRetryDuration(Throwable t, Duration defaultDuration) {
        if (t == null) {
            return defaultDuration;
        }
        String msg = t.getMessage();
        if (msg != null) {
            Matcher m = Pattern.compile("(?i)(?:retry[- ]after|retry in|reset in|wait)\\s*[:=]?\s*(\\d+)\s*(s|sec|second)?").matcher(msg);
            if (m.find()) {
                try {
                    long seconds = Long.parseLong(m.group(1));
                    if (seconds > 0) {
                        return Duration.ofSeconds(seconds);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (t.getCause() != null && t.getCause() != t) {
            return extractRetryDuration(t.getCause(), defaultDuration);
        }
        return defaultDuration;
    }

    /**
     * Computes SHA-256 hash for document content to detect modifications.
     *
     * @param text content string
     * @return hex-encoded SHA-256 hash
     */
    public String computeHash(String text) {
        if (text == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(text.hashCode());
        }
    }

    /**
     * Formats retrieved documents into a clean, unified context text block.
     *
     * @param documents list of retrieved documents
     * @return formatted context string
     */
    public String formatDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (Document doc : documents) {
            if (doc != null && doc.getText() != null && !doc.getText().isBlank()) {
                sb.append(doc.getText().trim()).append("\n\n");
            }
        }
        return sb.toString().trim();
    }

    public String resolveFilterExpression(String retrievalPath) {
        if (IntentService.PATH_KNOWLEDGE.equals(retrievalPath)) {
            return "type == 'KNOWLEDGE'";
        }
        if (IntentService.PATH_MARKETPLACE_DATA.equals(retrievalPath)) {
            return "type == 'MARKETPLACE_DATA'";
        }
        // For PATH_BOTH or PATH_UNKNOWN, return null to search across both categories
        return null;
    }

    public VectorStore getVectorStore() {
        return vectorStore;
    }

    public int getDefaultTopK() {
        return defaultTopK;
    }

    public Map<String, String> getIndexedDocumentHashes() {
        return Collections.unmodifiableMap(indexedDocumentHashes);
    }

    public void clearDocumentHashes() {
        indexedDocumentHashes.clear();
    }
}
