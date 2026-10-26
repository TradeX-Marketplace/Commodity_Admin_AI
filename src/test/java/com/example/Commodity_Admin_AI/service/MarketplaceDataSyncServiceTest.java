package com.example.Commodity_Admin_AI.service;

import com.example.Commodity_Admin_AI.ai.RagService;
import com.example.Commodity_Admin_AI.dto.MarketplaceDocument;
import com.example.Commodity_Admin_AI.dto.MarketplaceRateDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketplaceDataSyncServiceTest {

    @Mock
    private MarketplaceDataService marketplaceDataService;

    @Mock
    private RagService ragService;

    private MarketplaceDataSyncService syncService;

    @BeforeEach
    void setUp() {
        syncService = new MarketplaceDataSyncService(marketplaceDataService, ragService);
    }

    @Test
    @DisplayName("syncMarketRates returns empty and sets BACKEND_UNCONFIGURED when backend URL is missing")
    void syncMarketRates_Unconfigured_ReturnsEmpty() {
        when(marketplaceDataService.isConfigured()).thenReturn(false);

        List<MarketplaceDocument> result = syncService.syncMarketRates();

        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertEquals("BACKEND_UNCONFIGURED", syncService.getLastSyncStatus());
    }

    @Test
    @DisplayName("syncMarketRates validates records and converts valid items into searchable documents")
    void syncMarketRates_ValidData_ConvertsDocuments() {
        when(marketplaceDataService.isConfigured()).thenReturn(true);

        MarketplaceRateDto validRate = new MarketplaceRateDto("Soybean", "Sangli", new BigDecimal("42000"), "MT", "2026-09-26");
        MarketplaceRateDto invalidRate = new MarketplaceRateDto("", "Sangli", null, "", "2026-09-26");

        when(marketplaceDataService.fetchMarketRates()).thenReturn(List.of(validRate, invalidRate));
        when(marketplaceDataService.convertToSearchableDocument(validRate))
                .thenReturn(new MarketplaceDocument("Market Rate Information: Soybean", Map.of("commodity", "Soybean")));

        List<MarketplaceDocument> result = syncService.syncMarketRates();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("SUCCESS", syncService.getLastSyncStatus());
        assertEquals(1, syncService.getLastSyncedCount());
        assertNotNull(syncService.getLastSyncTime());

        verify(marketplaceDataService).convertToSearchableDocument(validRate);
    }

    @Test
    @DisplayName("syncAndIndexMarketRates indexes converted documents into PGVector via RagService")
    void syncAndIndexMarketRates_Success() {
        when(marketplaceDataService.isConfigured()).thenReturn(true);

        MarketplaceRateDto validRate = new MarketplaceRateDto("Soybean", "Sangli", new BigDecimal("42000"), "MT", "2026-09-26");
        MarketplaceDocument doc = new MarketplaceDocument("Market Rate Information: Soybean", Map.of("commodity", "Soybean"));

        when(marketplaceDataService.fetchMarketRates()).thenReturn(List.of(validRate));
        when(marketplaceDataService.convertToSearchableDocument(validRate)).thenReturn(doc);

        int indexedCount = syncService.syncAndIndexMarketRates();

        assertEquals(1, indexedCount);
        verify(ragService).indexMarketplaceDocuments(List.of(doc));
    }

    @Test
    @DisplayName("syncAndIndexMarketRates handles unconfigured backend without calling RagService")
    void syncAndIndexMarketRates_Unconfigured_DoesNotIndex() {
        when(marketplaceDataService.isConfigured()).thenReturn(false);

        int indexedCount = syncService.syncAndIndexMarketRates();

        assertEquals(0, indexedCount);
        verify(ragService, never()).indexMarketplaceDocuments(any());
    }

    @Test
    @DisplayName("syncAndIndexMarketRates works safely when RagService is null")
    void syncAndIndexMarketRates_NullRagService() {
        MarketplaceDataSyncService serviceWithoutRag = new MarketplaceDataSyncService(marketplaceDataService, null);
        when(marketplaceDataService.isConfigured()).thenReturn(true);

        MarketplaceRateDto validRate = new MarketplaceRateDto("Soybean", "Sangli", new BigDecimal("42000"), "MT", "2026-09-26");
        MarketplaceDocument doc = new MarketplaceDocument("Market Rate Information: Soybean", Map.of("commodity", "Soybean"));

        when(marketplaceDataService.fetchMarketRates()).thenReturn(List.of(validRate));
        when(marketplaceDataService.convertToSearchableDocument(validRate)).thenReturn(doc);

        int indexedCount = serviceWithoutRag.syncAndIndexMarketRates();
        assertEquals(1, indexedCount);
    }

    @Test
    @DisplayName("syncAllMarketplaceSources succeeds with supported Tradex records (commodities, supplies, demands, bids)")
    void syncAllMarketplaceSources_SupportedTradexRecords_Success() {
        when(marketplaceDataService.isConfigured()).thenReturn(true);

        MarketplaceDocument commDoc = new MarketplaceDocument("commodities:1", "Commodity: Wheat", Map.of("source", "commodities"));
        MarketplaceDocument supplyDoc = new MarketplaceDocument("supplies:2", "Supply: Soybean", Map.of("source", "supplies"));
        MarketplaceDocument demandDoc = new MarketplaceDocument("demands:3", "Demand: Chana", Map.of("source", "demands"));
        MarketplaceDocument bidDoc = new MarketplaceDocument("bids:4", "Bid: Cotton", Map.of("source", "bids"));

        List<MarketplaceDocument> supportedDocs = List.of(commDoc, supplyDoc, demandDoc, bidDoc);
        when(marketplaceDataService.fetchAllMarketplaceDocuments()).thenReturn(supportedDocs);

        List<MarketplaceDocument> result = syncService.syncAllMarketplaceSources();

        assertNotNull(result);
        assertEquals(4, result.size());
        assertEquals("SUCCESS", syncService.getLastSyncStatus());
        assertEquals(4, syncService.getLastSyncedCount());
        assertNotNull(syncService.getLastSyncTime());
    }

    @Test
    @DisplayName("syncAndIndexMarketRates indexes supported Tradex records into PGVector via RagService")
    void syncAndIndexMarketRates_SupportedTradexRecords_IndexesToRag() {
        when(marketplaceDataService.isConfigured()).thenReturn(true);

        MarketplaceDocument commDoc = new MarketplaceDocument("commodities:1", "Commodity: Wheat", Map.of("source", "commodities"));
        MarketplaceDocument supplyDoc = new MarketplaceDocument("supplies:2", "Supply: Soybean", Map.of("source", "supplies"));
        MarketplaceDocument demandDoc = new MarketplaceDocument("demands:3", "Demand: Chana", Map.of("source", "demands"));
        MarketplaceDocument bidDoc = new MarketplaceDocument("bids:4", "Bid: Cotton", Map.of("source", "bids"));

        List<MarketplaceDocument> supportedDocs = List.of(commDoc, supplyDoc, demandDoc, bidDoc);
        when(marketplaceDataService.fetchAllMarketplaceDocuments()).thenReturn(supportedDocs);

        int indexedCount = syncService.syncAndIndexMarketRates();

        assertEquals(4, indexedCount);
        verify(ragService).indexMarketplaceDocuments(supportedDocs);
    }

    @Test
    @DisplayName("syncAllMarketplaceSources handles empty responses from all endpoints by setting EMPTY_DATA")
    void syncAllMarketplaceSources_AllEmpty_SetsEmptyData() {
        when(marketplaceDataService.isConfigured()).thenReturn(true);
        when(marketplaceDataService.fetchAllMarketplaceDocuments()).thenReturn(List.of());
        when(marketplaceDataService.fetchMarketRates()).thenReturn(List.of());

        List<MarketplaceDocument> result = syncService.syncAllMarketplaceSources();

        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertEquals("EMPTY_DATA", syncService.getLastSyncStatus());
        verify(ragService, never()).indexMarketplaceDocuments(any());
    }

    @Test
    @DisplayName("syncAllMarketplaceSources indexes available records when some endpoints are empty")
    void syncAllMarketplaceSources_PartialEmpty_IndexesAvailableRecords() {
        when(marketplaceDataService.isConfigured()).thenReturn(true);

        // Only supplies and bids returned, commodities and demands empty
        MarketplaceDocument supplyDoc = new MarketplaceDocument("supplies:2", "Supply: Soybean", Map.of("source", "supplies"));
        MarketplaceDocument bidDoc = new MarketplaceDocument("bids:4", "Bid: Cotton", Map.of("source", "bids"));
        List<MarketplaceDocument> partialDocs = List.of(supplyDoc, bidDoc);

        when(marketplaceDataService.fetchAllMarketplaceDocuments()).thenReturn(partialDocs);

        int indexedCount = syncService.syncAndIndexMarketRates();

        assertEquals(2, indexedCount);
        assertEquals("SUCCESS", syncService.getLastSyncStatus());
        verify(ragService).indexMarketplaceDocuments(partialDocs);
    }
}
