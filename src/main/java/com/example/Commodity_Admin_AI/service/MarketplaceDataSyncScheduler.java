package com.example.Commodity_Admin_AI.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Startup and scheduled synchronization component for marketplace data.
 * <p>
 * Orchestrates marketplace data ingestion from the Marketplace Backend REST API
 * into PGVector via MarketplaceDataSyncService and RagService:
 * 1. Executes an initial sync immediately upon application startup via ApplicationRunner,
 *    ensuring all core beans (RagService, VectorStore, etc.) are fully initialized.
 * 2. Periodically executes scheduled synchronization based on a configurable interval.
 * 3. Safely catches and logs errors so that backend outages never block application startup.
 * 4. Prevents duplicate/concurrent execution through atomic state management.
 */
@Component
@ConditionalOnProperty(name = "marketplace.sync.enabled", havingValue = "true", matchIfMissing = true)
public class MarketplaceDataSyncScheduler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceDataSyncScheduler.class);

    private final MarketplaceDataSyncService marketplaceDataSyncService;
    private final AtomicBoolean isSyncInProgress = new AtomicBoolean(false);

    @Autowired
    public MarketplaceDataSyncScheduler(MarketplaceDataSyncService marketplaceDataSyncService) {
        this.marketplaceDataSyncService = marketplaceDataSyncService;
    }

    /**
     * Executes initial synchronization on application startup after Spring Boot
     * context initialization has completed.
     *
     * @param args incoming application arguments
     */
    @Override
    public void run(ApplicationArguments args) {
        log.info("Application startup runner triggered: starting initial marketplace data synchronization.");
        syncMarketplaceData();
    }

    /**
     * Executes periodic recurring synchronization at a configurable interval.
     */
    @Scheduled(
            initialDelayString = "${marketplace.sync.initial-delay-ms:300000}",
            fixedDelayString = "${marketplace.sync.interval-ms:300000}"
    )
    public void scheduledSync() {
        log.info("Scheduled task triggered: starting periodic marketplace data synchronization.");
        syncMarketplaceData();
    }

    /**
     * Orchestrates synchronization by invoking MarketplaceDataSyncService.syncAndIndexMarketRates()
     * with duplicate execution prevention and safe exception handling.
     *
     * @return number of documents indexed, or 0 on skip/failure
     */
    public int syncMarketplaceData() {
        if (!isSyncInProgress.compareAndSet(false, true)) {
            log.info("Marketplace data synchronization is already in progress. Skipping concurrent run.");
            return 0;
        }

        try {
            return marketplaceDataSyncService.syncAndIndexMarketRates();
        } catch (Exception ex) {
            log.info("Marketplace data synchronization failure: {}", ex.getMessage());
            log.error("Unexpected error during marketplace data synchronization: {}", ex.getMessage(), ex);
            return 0;
        } finally {
            isSyncInProgress.set(false);
        }
    }

    public boolean isSyncInProgress() {
        return isSyncInProgress.get();
    }
}
