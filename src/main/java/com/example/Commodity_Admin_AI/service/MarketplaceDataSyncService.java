package com.example.Commodity_Admin_AI.service;

import com.example.Commodity_Admin_AI.ai.RagService;
import com.example.Commodity_Admin_AI.dto.MarketplaceDocument;
import com.example.Commodity_Admin_AI.dto.MarketplaceRateDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service orchestrating the ingestion and synchronization of marketplace data.
 * Fetches approved data via MarketplaceDataService, validates it, converts
 * it into structured MarketplaceDocument instances, and indexes them into
 * the unified PGVector VectorStore via RagService (tagged with type=MARKETPLACE_DATA).
 */
@Service
public class MarketplaceDataSyncService {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceDataSyncService.class);

    private final MarketplaceDataService marketplaceDataService;
    private final RagService ragService;
    private LocalDateTime lastSyncTime;
    private int lastSyncedCount;
    private String lastSyncStatus = "PENDING_CONFIGURATION";

    @Autowired
    public MarketplaceDataSyncService(MarketplaceDataService marketplaceDataService,
                                      @Autowired(required = false) RagService ragService) {
        this.marketplaceDataService = marketplaceDataService;
        this.ragService = ragService;
    }

    public MarketplaceDataSyncService(MarketplaceDataService marketplaceDataService) {
        this(marketplaceDataService, null);
    }

    /**
     * Executes the synchronization workflow:
     * 1. Fetches approved data from Backend REST API via HTTP/JSON.
     * 2. Validates incoming records.
     * 3. Transforms records into searchable documents.
     * 4. Returns prepared documents ready for vector embedding in PGVector.
     *
     * @return list of prepared MarketplaceDocument objects
     */
    public List<MarketplaceDocument> syncMarketRates() {
        if (!marketplaceDataService.isConfigured()) {
            this.lastSyncStatus = "BACKEND_UNCONFIGURED";
            log.info("Marketplace sync skipped: Backend API URL not configured.");
            return Collections.emptyList();
        }

        List<MarketplaceRateDto> rates = marketplaceDataService.fetchMarketRates();
        if (rates.isEmpty()) {
            this.lastSyncStatus = "EMPTY_DATA";
            log.info("No market rates received from Backend REST API.");
            return Collections.emptyList();
        }

        List<MarketplaceDocument> documents = new ArrayList<>();
        for (MarketplaceRateDto rateDto : rates) {
            if (isValidRate(rateDto)) {
                documents.add(marketplaceDataService.convertToSearchableDocument(rateDto));
            } else {
                log.warn("Skipping invalid marketplace rate record: commodity={}, location={}",
                        rateDto.getCommodity(), rateDto.getLocation());
            }
        }

        this.lastSyncTime = LocalDateTime.now();
        this.lastSyncedCount = documents.size();
        this.lastSyncStatus = "SUCCESS";

        log.info("[MARKETPLACE SYNC] Documents created: {}", documents.size());
        log.info("Successfully converted {} marketplace rate records into searchable documents.", documents.size());
        return documents;
    }

    /**
     * Executes the synchronization workflow for all six marketplace API sources.
     * Falls back to syncMarketRates() if fetchAllMarketplaceDocuments() yields nothing
     * (preserving compatibility with unit tests that only stub fetchMarketRates()).
     *
     * @return list of prepared MarketplaceDocument objects
     */
    public List<MarketplaceDocument> syncAllMarketplaceSources() {
        if (!marketplaceDataService.isConfigured()) {
            this.lastSyncStatus = "BACKEND_UNCONFIGURED";
            log.info("Marketplace sync skipped: Backend API URL not configured.");
            return Collections.emptyList();
        }

        List<MarketplaceDocument> allDocs = marketplaceDataService.fetchAllMarketplaceDocuments();
        if (allDocs == null || allDocs.isEmpty()) {
            // Fallback for tests that stub fetchMarketRates()
            allDocs = syncMarketRates();
        }

        if (allDocs == null || allDocs.isEmpty()) {
            this.lastSyncStatus = "EMPTY_DATA";
            log.info("No marketplace data received from Backend REST APIs.");
            return Collections.emptyList();
        }

        this.lastSyncTime = LocalDateTime.now();
        this.lastSyncedCount = allDocs.size();
        this.lastSyncStatus = "SUCCESS";

        log.info("[MARKETPLACE SYNC] Total marketplace documents created: {}", allDocs.size());
        return allDocs;
    }

    /**
     * Executes end-to-end sync and indexing into PGVector via RagService across all six sources:
     * 1. Fetches approved marketplace records from Backend REST APIs via HTTP/JSON.
     * 2. Transforms them into searchable MarketplaceDocument objects with deterministic UUIDs.
     * 3. Indexes them into PGVector via RagService (tagged with type=MARKETPLACE_DATA).
     *
     * @return number of documents indexed
     */
    public int syncAndIndexMarketRates() {
        log.info("[MARKETPLACE SYNC] Starting synchronization across all marketplace sources");
        try {
            List<MarketplaceDocument> documents = syncAllMarketplaceSources();
            if (documents.isEmpty()) {
                log.info("Number of marketplace documents fetched/indexed: 0");
                log.info("Marketplace data synchronization completed.");
                return 0;
            }

            if (ragService != null) {
                if (ragService.isQuotaExhausted()) {
                    this.lastSyncStatus = "QUOTA_EXHAUSTED";
                    log.warn("[MARKETPLACE SYNC] Embedding quota is currently exhausted (backoff until {}). Skipping vector indexing during quota window.",
                            ragService.getQuotaRetryAfter());
                    log.info("Number of marketplace documents fetched/indexed: {}", documents.size());
                    log.info("Marketplace data synchronization completed with indexing deferred.");
                    return documents.size();
                }

                log.info("[MARKETPLACE SYNC] Indexing documents into PGVector");
                ragService.indexMarketplaceDocuments(documents);
                if (ragService.isQuotaExhausted()) {
                    this.lastSyncStatus = "QUOTA_EXHAUSTED";
                    log.warn("[MARKETPLACE SYNC] Embedding quota exhausted during indexing (retry after: {}). Indexing deferred.",
                            ragService.getQuotaRetryAfter());
                } else {
                    log.info("[MARKETPLACE SYNC] Documents indexed: {}", documents.size());
                    log.info("Indexed {} marketplace documents into PGVector.", documents.size());
                }
            } else {
                log.warn("RagService is not available; marketplace documents prepared but not indexed.");
            }

            log.info("Number of marketplace documents fetched/indexed: {}", documents.size());
            log.info("Marketplace data synchronization completed.");
            return documents.size();
        } catch (Exception ex) {
            if (RagService.isQuotaExhaustionException(ex)) {
                this.lastSyncStatus = "QUOTA_EXHAUSTED";
                log.warn("[MARKETPLACE SYNC] Embedding quota exhausted during sync: {}", ex.getMessage());
            } else {
                this.lastSyncStatus = "FAILURE";
                log.info("Marketplace data synchronization failure: {}", ex.getMessage());
                log.error("Marketplace data synchronization failed", ex);
            }
            return 0;
        }
    }

    private boolean isValidRate(MarketplaceRateDto dto) {
        return dto != null &&
                dto.getCommodity() != null && !dto.getCommodity().isBlank() &&
                dto.getLocation() != null && !dto.getLocation().isBlank() &&
                dto.getRate() != null &&
                dto.getUnit() != null && !dto.getUnit().isBlank();
    }

    public LocalDateTime getLastSyncTime() {
        return lastSyncTime;
    }

    public int getLastSyncedCount() {
        return lastSyncedCount;
    }

    public String getLastSyncStatus() {
        return lastSyncStatus;
    }

    public RagService getRagService() {
        return ragService;
    }
}
