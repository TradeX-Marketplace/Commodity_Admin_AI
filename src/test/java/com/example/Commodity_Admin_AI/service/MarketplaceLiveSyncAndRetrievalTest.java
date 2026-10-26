package com.example.Commodity_Admin_AI.service;

import com.example.Commodity_Admin_AI.dto.MarketplaceDocument;
import com.example.Commodity_Admin_AI.marketplaceAllAPi.MarketplaceApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MarketplaceLiveSyncAndRetrievalTest {

    private static final String BASE_URL = "http://localhost:7070";
    private MarketplaceApiClient apiClient;
    private MarketplaceDataService dataService;

    @BeforeEach
    void setUp() {
        apiClient = new MarketplaceApiClient(BASE_URL);
        dataService = new MarketplaceDataService(
                RestClient.builder(),
                BASE_URL,
                "/api/ai/market-rates",
                "",
                apiClient
        );
    }

    private boolean isBackendReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 7070), 2000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Test
    @DisplayName("Verify live synchronization from all six REST APIs against Commodity_Marketplace_Backend :7070")
    void testLiveSyncAllSixApis() {
        assumeTrue(isBackendReachable(), "Commodity_Marketplace_Backend is not reachable on :7070");

        // 1. Commodities (4 records)
        List<MarketplaceDocument> commoditiesDocs = dataService.fetchCommoditiesDocuments();
        assertNotNull(commoditiesDocs);
        assertEquals(4, commoditiesDocs.size(), "Expected 4 commodities from backend");
        for (MarketplaceDocument doc : commoditiesDocs) {
            assertEquals("MARKETPLACE_DATA", doc.getMetadata().get("type"));
            assertEquals("commodities", doc.getMetadata().get("source"));
            assertNotNull(doc.getId(), "Document ID must not be null");
            assertNotNull(doc.getContent(), "Document content must not be null");
        }

        // 2. Market Rates (3 records)
        List<MarketplaceDocument> rateDocs = dataService.fetchMarketRatesDocuments();
        assertNotNull(rateDocs);
        assertEquals(3, rateDocs.size(), "Expected 3 market rates from backend");
        for (MarketplaceDocument doc : rateDocs) {
            assertEquals("MARKETPLACE_DATA", doc.getMetadata().get("type"));
            assertEquals("market-rates", doc.getMetadata().get("source"));
            assertNotNull(doc.getId());
            assertNotNull(doc.getContent());
        }

        // 3. Rate History (8 records)
        List<MarketplaceDocument> historyDocs = dataService.fetchRateHistoryDocuments();
        assertNotNull(historyDocs);
        assertEquals(8, historyDocs.size(), "Expected 8 rate history records from backend");
        for (MarketplaceDocument doc : historyDocs) {
            assertEquals("MARKETPLACE_DATA", doc.getMetadata().get("type"));
            assertEquals("rate-history", doc.getMetadata().get("source"));
            assertNotNull(doc.getId());
            assertNotNull(doc.getContent());
        }

        // 4. Buyer Demands (2 records)
        List<MarketplaceDocument> demandDocs = dataService.fetchBuyerDemandsDocuments();
        assertNotNull(demandDocs);
        assertEquals(2, demandDocs.size(), "Expected 2 buyer demands from backend");
        for (MarketplaceDocument doc : demandDocs) {
            assertEquals("MARKETPLACE_DATA", doc.getMetadata().get("type"));
            assertEquals("buyer-demands", doc.getMetadata().get("source"));
            assertNotNull(doc.getId());
            assertNotNull(doc.getContent());
        }

        // 5. Notifications (2 records)
        List<MarketplaceDocument> notifDocs = dataService.fetchNotificationsDocuments();
        assertNotNull(notifDocs);
        assertEquals(2, notifDocs.size(), "Expected 2 notifications from backend");
        for (MarketplaceDocument doc : notifDocs) {
            assertEquals("MARKETPLACE_DATA", doc.getMetadata().get("type"));
            assertEquals("notifications", doc.getMetadata().get("source"));
            assertNotNull(doc.getId());
            assertNotNull(doc.getContent());
        }

        // 6. WhatsApp Logs (3 records)
        List<MarketplaceDocument> logDocs = dataService.fetchWhatsAppLogsDocuments();
        assertNotNull(logDocs);
        assertEquals(3, logDocs.size(), "Expected 3 WhatsApp logs from backend");
        for (MarketplaceDocument doc : logDocs) {
            assertEquals("MARKETPLACE_DATA", doc.getMetadata().get("type"));
            assertEquals("whatsapp-logs", doc.getMetadata().get("source"));
            assertNotNull(doc.getId());
            assertNotNull(doc.getContent());
        }

        // Total Aggregation across all 6 sources
        List<MarketplaceDocument> allDocs = dataService.fetchAllMarketplaceDocuments();
        assertEquals(22, allDocs.size(), "Total documents across 6 APIs must equal 22 (4+3+8+2+2+3)");
        System.out.println("[LIVE TEST VERIFIED] All 22 documents created from 6 APIs successfully.");
    }
}
