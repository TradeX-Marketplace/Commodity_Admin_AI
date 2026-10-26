package com.example.Commodity_Admin_AI.service;

import com.example.Commodity_Admin_AI.dto.MarketplaceDocument;
import com.example.Commodity_Admin_AI.marketplaceAllAPi.MarketplaceApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class MarketplaceDataServiceSixApisTest {

    private MarketplaceApiClient mockClient;
    private MarketplaceDataService service;

    @BeforeEach
    void setUp() {
        mockClient = Mockito.mock(MarketplaceApiClient.class);
        service = new MarketplaceDataService(
                RestClient.builder(),
                "http://localhost:7070",
                "/api/ai/market-rates",
                "",
                mockClient
        );
    }

    @Test
    @DisplayName("fetchCommoditiesDocuments parses JSON and attaches source=commodities and deterministic UUID")
    void testFetchCommoditiesDocuments() {
        String json = """
            [
              {
                "id": 1,
                "name": "Soybean",
                "category": "Oil Seeds",
                "unit": "Quintal",
                "description": "High quality oil seed",
                "status": "ACTIVE"
              }
            ]
            """;
        when(mockClient.getCommodities()).thenReturn(json);

        List<MarketplaceDocument> docs = service.fetchCommoditiesDocuments();
        assertNotNull(docs);
        assertEquals(1, docs.size());

        MarketplaceDocument doc = docs.get(0);
        assertEquals("MARKETPLACE_DATA", doc.getMetadata().get("type"));
        assertEquals("commodities", doc.getMetadata().get("source"));
        assertEquals("Soybean", doc.getMetadata().get("commodity"));
        assertEquals("Oil Seeds", doc.getMetadata().get("category"));
        assertEquals(1L, doc.getMetadata().get("id"));

        String expectedUuid = UUID.nameUUIDFromBytes("commodities:1".getBytes(StandardCharsets.UTF_8)).toString();
        assertEquals(expectedUuid, doc.getId());
        assertTrue(doc.getContent().contains("Commodity Information:"));
        assertTrue(doc.getContent().contains("Name: Soybean"));
    }

    @Test
    @DisplayName("fetchRateHistoryDocuments parses JSON and attaches source=rate-history and deterministic UUID")
    void testFetchRateHistoryDocuments() {
        String json = """
            [
              {
                "id": 7,
                "commodityName": "Soybean",
                "location": "Sangli",
                "rate": 4250.0,
                "unit": "Quintal",
                "rateDate": "2026-10-07"
              }
            ]
            """;
        when(mockClient.getRateHistory()).thenReturn(json);

        List<MarketplaceDocument> docs = service.fetchRateHistoryDocuments();
        assertNotNull(docs);
        assertEquals(1, docs.size());

        MarketplaceDocument doc = docs.get(0);
        assertEquals("MARKETPLACE_DATA", doc.getMetadata().get("type"));
        assertEquals("rate-history", doc.getMetadata().get("source"));
        assertEquals("Soybean", doc.getMetadata().get("commodity"));
        assertEquals(7L, doc.getMetadata().get("id"));

        String expectedUuid = UUID.nameUUIDFromBytes("rate-history:7".getBytes(StandardCharsets.UTF_8)).toString();
        assertEquals(expectedUuid, doc.getId());
        assertTrue(doc.getContent().contains("Rate History Information:"));
    }

    @Test
    @DisplayName("fetchBuyerDemandsDocuments parses JSON and attaches source=buyer-demands and deterministic UUID")
    void testFetchBuyerDemandsDocuments() {
        String json = """
            [
              {
                "id": 1,
                "buyerName": "ABC Foods Pvt Ltd",
                "commodityName": "Soybean",
                "quantity": 500.0,
                "unit": "Quintal",
                "location": "Sangli",
                "requiredDate": "2026-10-12",
                "status": "OPEN"
              }
            ]
            """;
        when(mockClient.getBuyerDemands()).thenReturn(json);

        List<MarketplaceDocument> docs = service.fetchBuyerDemandsDocuments();
        assertNotNull(docs);
        assertEquals(1, docs.size());

        MarketplaceDocument doc = docs.get(0);
        assertEquals("MARKETPLACE_DATA", doc.getMetadata().get("type"));
        assertEquals("buyer-demands", doc.getMetadata().get("source"));
        assertEquals("Soybean", doc.getMetadata().get("commodity"));
        assertEquals("ABC Foods Pvt Ltd", doc.getMetadata().get("buyer"));

        String expectedUuid = UUID.nameUUIDFromBytes("buyer-demands:1".getBytes(StandardCharsets.UTF_8)).toString();
        assertEquals(expectedUuid, doc.getId());
        assertTrue(doc.getContent().contains("Buyer Demand Information:"));
    }

    @Test
    @DisplayName("fetchNotificationsDocuments parses JSON and attaches source=notifications and deterministic UUID")
    void testFetchNotificationsDocuments() {
        String json = """
            [
              {
                "id": 2,
                "type": "BUYER_DEMAND",
                "title": "New Buyer Demand for Soybean",
                "message": "Requirement created for 500 Quintals at Sangli.",
                "commodityName": "Soybean",
                "status": "UNREAD",
                "createdAt": "2026-10-07T10:06:13.17084"
              }
            ]
            """;
        when(mockClient.getNotifications()).thenReturn(json);

        List<MarketplaceDocument> docs = service.fetchNotificationsDocuments();
        assertNotNull(docs);
        assertEquals(1, docs.size());

        MarketplaceDocument doc = docs.get(0);
        assertEquals("MARKETPLACE_DATA", doc.getMetadata().get("type"));
        assertEquals("notifications", doc.getMetadata().get("source"));
        assertEquals("Soybean", doc.getMetadata().get("commodity"));
        assertEquals(2L, doc.getMetadata().get("id"));

        String expectedUuid = UUID.nameUUIDFromBytes("notifications:2".getBytes(StandardCharsets.UTF_8)).toString();
        assertEquals(expectedUuid, doc.getId());
        assertTrue(doc.getContent().contains("Notification Information:"));
    }

    @Test
    @DisplayName("fetchWhatsAppLogsDocuments parses JSON and attaches source=whatsapp-logs and deterministic UUID")
    void testFetchWhatsAppLogsDocuments() {
        String json = """
            [
              {
                "id": 3,
                "phoneNumber": "******9999",
                "messageType": "DEMAND_ALERT",
                "message": "Sample alert message",
                "commodityName": "Wheat",
                "status": "FAILED",
                "sentAt": "2026-10-07T09:51:13.209005"
              }
            ]
            """;
        when(mockClient.getWhatsAppLogs()).thenReturn(json);

        List<MarketplaceDocument> docs = service.fetchWhatsAppLogsDocuments();
        assertNotNull(docs);
        assertEquals(1, docs.size());

        MarketplaceDocument doc = docs.get(0);
        assertEquals("MARKETPLACE_DATA", doc.getMetadata().get("type"));
        assertEquals("whatsapp-logs", doc.getMetadata().get("source"));
        assertEquals("Wheat", doc.getMetadata().get("commodity"));
        assertEquals(3L, doc.getMetadata().get("id"));

        String expectedUuid = UUID.nameUUIDFromBytes("whatsapp-logs:3".getBytes(StandardCharsets.UTF_8)).toString();
        assertEquals(expectedUuid, doc.getId());
        assertTrue(doc.getContent().contains("WhatsApp Log Information:"));
    }

    @Test
    @DisplayName("fetchAllMarketplaceDocuments aggregates documents across all six REST sources")
    void testFetchAllMarketplaceDocuments() {
        when(mockClient.getCommodities()).thenReturn("[{\"id\":1,\"name\":\"Soybean\",\"category\":\"Oil Seeds\",\"unit\":\"Q\",\"status\":\"ACTIVE\"}]");
        when(mockClient.getMarketRates()).thenReturn("[{\"id\":1,\"commodityName\":\"Soybean\",\"location\":\"Sangli\",\"rate\":4250.0,\"unit\":\"Q\",\"currency\":\"INR\",\"rateDate\":\"2026-10-07\"}]");
        when(mockClient.getRateHistory()).thenReturn("[{\"id\":1,\"commodityName\":\"Soybean\",\"location\":\"Sangli\",\"rate\":4180.0,\"unit\":\"Q\",\"rateDate\":\"2026-10-01\"}]");
        when(mockClient.getBuyerDemands()).thenReturn("[{\"id\":1,\"buyerName\":\"ABC\",\"commodityName\":\"Soybean\",\"quantity\":500.0,\"unit\":\"Q\",\"location\":\"Sangli\",\"requiredDate\":\"2026-10-12\",\"status\":\"OPEN\"}]");
        when(mockClient.getNotifications()).thenReturn("[{\"id\":1,\"type\":\"ALERT\",\"title\":\"Test\",\"message\":\"Msg\",\"commodityName\":\"Soybean\",\"status\":\"UNREAD\",\"createdAt\":\"2026-10-07T10:00:00\"}]");
        when(mockClient.getWhatsAppLogs()).thenReturn("[{\"id\":1,\"phoneNumber\":\"123\",\"messageType\":\"ALERT\",\"message\":\"Msg\",\"commodityName\":\"Soybean\",\"status\":\"SENT\",\"sentAt\":\"2026-10-07T10:00:00\"}]");

        List<MarketplaceDocument> allDocs = service.fetchAllMarketplaceDocuments();
        assertNotNull(allDocs);
        assertEquals(6, allDocs.size(), "Must contain exactly one document from each of the 6 sources");
    }
}
