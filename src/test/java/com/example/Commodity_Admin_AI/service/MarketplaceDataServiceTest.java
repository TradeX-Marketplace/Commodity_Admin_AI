package com.example.Commodity_Admin_AI.service;

import com.example.Commodity_Admin_AI.dto.MarketplaceDocument;
import com.example.Commodity_Admin_AI.dto.MarketplaceRateDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketplaceDataServiceTest {

    private MarketplaceDataService unconfiguredService;
    private MarketplaceDataService configuredService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        unconfiguredService = new MarketplaceDataService(builder, "", "/api/chatbot/market-rates", "");
        configuredService = new MarketplaceDataService(builder, "https://marketplace.example.com", "/api/chatbot/market-rates", "test-key");
    }

    @Test
    @DisplayName("isConfigured returns true when backend URL is present, false when empty")
    void isConfigured_Verification() {
        assertFalse(unconfiguredService.isConfigured());
        assertTrue(configuredService.isConfigured());
    }

    @Test
    @DisplayName("fetchMarketRates returns empty list when unconfigured without generating fake data")
    void fetchMarketRates_Unconfigured_ReturnsEmpty() {
        List<MarketplaceRateDto> rates = unconfiguredService.fetchMarketRates();
        assertNotNull(rates);
        assertTrue(rates.isEmpty(), "Must return empty list without fabricating fake rates");
    }

    @Test
    @DisplayName("convertToSearchableDocument builds accurate text and metadata structure")
    void convertToSearchableDocument_ValidDto() {
        MarketplaceRateDto dto = new MarketplaceRateDto(
                "Soybean",
                "Sangli",
                new BigDecimal("42000"),
                "MT",
                "2026-09-26"
        );

        MarketplaceDocument document = unconfiguredService.convertToSearchableDocument(dto);

        assertNotNull(document);
        assertTrue(document.getContent().contains("Commodity: Soybean"));
        assertTrue(document.getContent().contains("Location: Sangli"));
        assertTrue(document.getContent().contains("Rate: 42000 per MT"));
        assertTrue(document.getContent().contains("Date: 2026-09-26"));

        assertEquals("MARKETPLACE_DATA", document.getMetadata().get("type"));
        assertEquals("MARKET_RATE", document.getMetadata().get("category"));
        assertEquals("Soybean", document.getMetadata().get("commodity"));
        assertEquals("Sangli", document.getMetadata().get("location"));
        assertEquals(new BigDecimal("42000"), document.getMetadata().get("rate"));
        assertEquals("MT", document.getMetadata().get("unit"));
        assertEquals("2026-09-26", document.getMetadata().get("date"));
    }

    @Test
    @DisplayName("convertToSearchableDocument throws exception when DTO is null")
    void convertToSearchableDocument_NullDto() {
        assertThrows(IllegalArgumentException.class, () -> unconfiguredService.convertToSearchableDocument(null));
    }

    @Test
    @DisplayName("fetchMarketRates deserializes backend JSON format with commodityName, rateDate, currency and extra fields")
    void fetchMarketRates_DeserializesBackendJsonFormat() {
        String backendJson = """
            [
              {
                "id": 1,
                "commodityId": 1,
                "commodityName": "Soybean",
                "location": "Sangli",
                "rate": 4250.0,
                "unit": "Quintal",
                "currency": "INR",
                "rateDate": "2026-10-07",
                "updatedAt": "2026-10-07T13:46:58.261908"
              },
              {
                "id": 2,
                "commodityId": 2,
                "commodityName": "Wheat",
                "location": "Pune",
                "rate": 2800.0,
                "unit": "Quintal",
                "currency": "INR",
                "rateDate": "2026-10-07",
                "updatedAt": "2026-10-07T13:46:58.261908"
              }
            ]
            """;

        com.example.Commodity_Admin_AI.marketplaceAllAPi.MarketplaceApiClient mockClient =
                org.mockito.Mockito.mock(com.example.Commodity_Admin_AI.marketplaceAllAPi.MarketplaceApiClient.class);
        org.mockito.Mockito.when(mockClient.getMarketRates()).thenReturn(backendJson);
        org.mockito.Mockito.when(mockClient.getBaseUrl()).thenReturn("http://localhost:7070");

        MarketplaceDataService service = new MarketplaceDataService(
                RestClient.builder(),
                "http://localhost:7070",
                "/api/ai/market-rates",
                "",
                mockClient
        );

        List<MarketplaceRateDto> rates = service.fetchMarketRates();
        assertNotNull(rates);
        assertEquals(2, rates.size());

        MarketplaceRateDto soybean = rates.get(0);
        assertEquals("Soybean", soybean.getCommodity());
        assertEquals("Sangli", soybean.getLocation());
        assertEquals(new BigDecimal("4250.0"), soybean.getRate());
        assertEquals("Quintal", soybean.getUnit());
        assertEquals("INR", soybean.getCurrency());
        assertEquals("2026-10-07", soybean.getDate());

        MarketplaceDocument doc = service.convertToSearchableDocument(soybean);
        assertNotNull(doc);
        assertTrue(doc.getContent().contains("Commodity: Soybean"));
        assertTrue(doc.getContent().contains("Currency: INR"));
        assertEquals("INR", doc.getMetadata().get("currency"));
    }

    @Test
    @DisplayName("fetchHistoricalRates parses backend response and sorts chronologically")
    void fetchHistoricalRates_ParsesAndSortsChronologically() {
        String historyJson = """
            [
              {
                "id": 2,
                "commodityName": "Soybean",
                "location": "Sangli",
                "rate": 4250.0,
                "unit": "Quintal",
                "rateDate": "2026-10-07"
              },
              {
                "id": 1,
                "commodityName": "Soybean",
                "location": "Sangli",
                "rate": 4200.0,
                "unit": "Quintal",
                "rateDate": "2026-10-01"
              }
            ]
            """;

        com.example.Commodity_Admin_AI.marketplaceAllAPi.MarketplaceApiClient mockClient =
                org.mockito.Mockito.mock(com.example.Commodity_Admin_AI.marketplaceAllAPi.MarketplaceApiClient.class);
        org.mockito.Mockito.when(mockClient.getRateHistory("Soybean", "Sangli")).thenReturn(historyJson);

        MarketplaceDataService service = new MarketplaceDataService(
                RestClient.builder(),
                "http://localhost:7070",
                "/api/ai/market-rates",
                "",
                mockClient
        );

        List<com.example.Commodity_Admin_AI.dto.ChartData> result = service.fetchHistoricalRates("Soybean", "Sangli");
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("2026-10-01", result.get(0).getDate());
        assertEquals(new BigDecimal("4200.0"), result.get(0).getRate());
        assertEquals("2026-10-07", result.get(1).getDate());
        assertEquals(new BigDecimal("4250.0"), result.get(1).getRate());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Tradex API Response Parsing and Synchronization Tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("fetchCommoditiesDocuments parses Tradex Page response with data.content")
    void testFetchCommoditiesDocuments_TradexPageShape() {
        String pageJson = """
            {
              "success": true,
              "message": "Commodities retrieved successfully",
              "data": {
                "content": [
                  {
                    "id": 101,
                    "commodityCode": "WHT-LOK",
                    "commodityName": "Wheat Lokwan",
                    "parentName": "Wheat",
                    "category": "Grains",
                    "subCategory": "Wheat",
                    "defaultUnit": "Quintal",
                    "description": "High grade milling wheat",
                    "status": "ACTIVE"
                  }
                ],
                "totalElements": 1,
                "totalPages": 1
              }
            }
            """;

        com.example.Commodity_Admin_AI.marketplaceAllAPi.MarketplaceApiClient mockClient =
                org.mockito.Mockito.mock(com.example.Commodity_Admin_AI.marketplaceAllAPi.MarketplaceApiClient.class);
        org.mockito.Mockito.when(mockClient.getCommodities()).thenReturn(pageJson);

        MarketplaceDataService service = new MarketplaceDataService(
                RestClient.builder(), "http://localhost:8080", "/api/ai/market-rates", "", mockClient);

        List<MarketplaceDocument> docs = service.fetchCommoditiesDocuments();
        assertNotNull(docs);
        assertEquals(1, docs.size());

        MarketplaceDocument doc = docs.get(0);
        assertEquals("MARKETPLACE_DATA", doc.getMetadata().get("type"));
        assertEquals("commodities", doc.getMetadata().get("source"));
        assertEquals("Wheat Lokwan", doc.getMetadata().get("commodity"));
        assertEquals("WHT-LOK", doc.getMetadata().get("commodityCode"));
        assertEquals("Grains", doc.getMetadata().get("category"));
        assertEquals(101L, doc.getMetadata().get("id"));
        assertTrue(doc.getContent().contains("Name: Wheat Lokwan"));
        assertTrue(doc.getContent().contains("Code: WHT-LOK"));
        assertTrue(doc.getContent().contains("Category: Grains"));
    }

    @Test
    @DisplayName("fetchCommoditiesDocuments returns empty list on empty data.content")
    void testFetchCommoditiesDocuments_EmptyPage() {
        String emptyPageJson = """
            {
              "success": true,
              "message": "Commodities retrieved successfully",
              "data": {
                "content": [],
                "totalElements": 0,
                "totalPages": 0
              }
            }
            """;

        com.example.Commodity_Admin_AI.marketplaceAllAPi.MarketplaceApiClient mockClient =
                org.mockito.Mockito.mock(com.example.Commodity_Admin_AI.marketplaceAllAPi.MarketplaceApiClient.class);
        org.mockito.Mockito.when(mockClient.getCommodities()).thenReturn(emptyPageJson);

        MarketplaceDataService service = new MarketplaceDataService(
                RestClient.builder(), "http://localhost:8080", "/api/ai/market-rates", "", mockClient);

        List<MarketplaceDocument> docs = service.fetchCommoditiesDocuments();
        assertNotNull(docs);
        assertTrue(docs.isEmpty());
    }

    @Test
    @DisplayName("fetchSuppliesDocuments parses Tradex List response with data")
    void testFetchSuppliesDocuments_TradexListShape() {
        String suppliesJson = """
            {
              "success": true,
              "message": "Supplies retrieved successfully",
              "data": [
                {
                  "id": 201,
                  "supplyNumber": "SPL-2026-001",
                  "commodityName": "Soybean",
                  "qualityGrade": "Grade A",
                  "totalQuantity": 500.0,
                  "availableQuantity": 350.0,
                  "askingRate": 4300.0,
                  "location": "Sangli",
                  "originZone": "Maharashtra",
                  "maskedLabel": "SLR-1001",
                  "validTill": "2026-11-30",
                  "status": "AVAILABLE"
                }
              ]
            }
            """;

        com.example.Commodity_Admin_AI.marketplaceAllAPi.MarketplaceApiClient mockClient =
                org.mockito.Mockito.mock(com.example.Commodity_Admin_AI.marketplaceAllAPi.MarketplaceApiClient.class);
        org.mockito.Mockito.when(mockClient.getSupplies()).thenReturn(suppliesJson);

        MarketplaceDataService service = new MarketplaceDataService(
                RestClient.builder(), "http://localhost:8080", "/api/ai/market-rates", "", mockClient);

        List<MarketplaceDocument> docs = service.fetchSuppliesDocuments();
        assertNotNull(docs);
        assertEquals(1, docs.size());

        MarketplaceDocument doc = docs.get(0);
        assertEquals("MARKETPLACE_DATA", doc.getMetadata().get("type"));
        assertEquals("supplies", doc.getMetadata().get("source"));
        assertEquals("SUPPLY", doc.getMetadata().get("category"));
        assertEquals(201L, doc.getMetadata().get("id"));
        assertEquals("Soybean", doc.getMetadata().get("commodity"));
        assertEquals(350.0, doc.getMetadata().get("availableQuantity"));
        assertEquals(4300.0, doc.getMetadata().get("askingRate"));
        assertEquals("Sangli", doc.getMetadata().get("location"));
        assertEquals("SLR-1001", doc.getMetadata().get("seller"));
        assertTrue(doc.getContent().contains("Supply Number: SPL-2026-001"));
        assertTrue(doc.getContent().contains("Commodity: Soybean"));
        assertTrue(doc.getContent().contains("Asking Rate: ₹4300.0"));
    }

    @Test
    @DisplayName("fetchSuppliesDocuments returns empty list on empty data")
    void testFetchSuppliesDocuments_EmptyList() {
        String emptyJson = "{\"success\":true,\"message\":\"Supplies retrieved successfully\",\"data\":[]}";

        com.example.Commodity_Admin_AI.marketplaceAllAPi.MarketplaceApiClient mockClient =
                org.mockito.Mockito.mock(com.example.Commodity_Admin_AI.marketplaceAllAPi.MarketplaceApiClient.class);
        org.mockito.Mockito.when(mockClient.getSupplies()).thenReturn(emptyJson);

        MarketplaceDataService service = new MarketplaceDataService(
                RestClient.builder(), "http://localhost:8080", "/api/ai/market-rates", "", mockClient);

        List<MarketplaceDocument> docs = service.fetchSuppliesDocuments();
        assertNotNull(docs);
        assertTrue(docs.isEmpty());
    }

    @Test
    @DisplayName("fetchDemandsDocuments parses Tradex List response with data")
    void testFetchDemandsDocuments_TradexListShape() {
        String demandsJson = """
            {
              "success": true,
              "message": "Demands retrieved successfully",
              "data": [
                {
                  "id": 301,
                  "demandNumber": "DMD-2026-001",
                  "commodityName": "Chana",
                  "qualityGrade": "FAQ",
                  "quantity": 200.0,
                  "targetRate": 5800.0,
                  "destination": "Akola",
                  "destinationZone": "Vidarbha",
                  "maskedLabel": "BYR-2001",
                  "validTill": "2026-11-15",
                  "status": "OPEN"
                }
              ]
            }
            """;

        com.example.Commodity_Admin_AI.marketplaceAllAPi.MarketplaceApiClient mockClient =
                org.mockito.Mockito.mock(com.example.Commodity_Admin_AI.marketplaceAllAPi.MarketplaceApiClient.class);
        org.mockito.Mockito.when(mockClient.getDemands()).thenReturn(demandsJson);

        MarketplaceDataService service = new MarketplaceDataService(
                RestClient.builder(), "http://localhost:8080", "/api/ai/market-rates", "", mockClient);

        List<MarketplaceDocument> docs = service.fetchDemandsDocuments();
        assertNotNull(docs);
        assertEquals(1, docs.size());

        MarketplaceDocument doc = docs.get(0);
        assertEquals("MARKETPLACE_DATA", doc.getMetadata().get("type"));
        assertEquals("demands", doc.getMetadata().get("source"));
        assertEquals("BUYER_DEMAND", doc.getMetadata().get("category"));
        assertEquals(301L, doc.getMetadata().get("id"));
        assertEquals("Chana", doc.getMetadata().get("commodity"));
        assertEquals(200.0, doc.getMetadata().get("quantity"));
        assertEquals(5800.0, doc.getMetadata().get("targetRate"));
        assertEquals("Akola", doc.getMetadata().get("location"));
        assertEquals("BYR-2001", doc.getMetadata().get("buyer"));
        assertTrue(doc.getContent().contains("Demand Number: DMD-2026-001"));
        assertTrue(doc.getContent().contains("Commodity: Chana"));
        assertTrue(doc.getContent().contains("Target Rate: ₹5800.0"));
    }

    @Test
    @DisplayName("fetchDemandsDocuments returns empty list on empty data")
    void testFetchDemandsDocuments_EmptyList() {
        String emptyJson = "{\"success\":true,\"message\":\"Demands retrieved successfully\",\"data\":[]}";

        com.example.Commodity_Admin_AI.marketplaceAllAPi.MarketplaceApiClient mockClient =
                org.mockito.Mockito.mock(com.example.Commodity_Admin_AI.marketplaceAllAPi.MarketplaceApiClient.class);
        org.mockito.Mockito.when(mockClient.getDemands()).thenReturn(emptyJson);

        MarketplaceDataService service = new MarketplaceDataService(
                RestClient.builder(), "http://localhost:8080", "/api/ai/market-rates", "", mockClient);

        List<MarketplaceDocument> docs = service.fetchDemandsDocuments();
        assertNotNull(docs);
        assertTrue(docs.isEmpty());
    }

    @Test
    @DisplayName("fetchBidsDocuments parses Tradex Page response with data.content")
    void testFetchBidsDocuments_TradexPageShape() {
        String bidsJson = """
            {
              "success": true,
              "message": "Bids retrieved successfully",
              "data": {
                "content": [
                  {
                    "id": 401,
                    "bidNumber": "BID-2026-001",
                    "commodityName": "Cotton",
                    "maskedBidderId": "BDR-4001",
                    "bidRate": 6200.0,
                    "counterRate": 6150.0,
                    "quantity": 100.0,
                    "destination": "Surat",
                    "bidType": "BUY",
                    "remarks": "Immediate delivery needed",
                    "status": "PENDING"
                  }
                ],
                "totalElements": 1,
                "totalPages": 1
              }
            }
            """;

        com.example.Commodity_Admin_AI.marketplaceAllAPi.MarketplaceApiClient mockClient =
                org.mockito.Mockito.mock(com.example.Commodity_Admin_AI.marketplaceAllAPi.MarketplaceApiClient.class);
        org.mockito.Mockito.when(mockClient.getBids()).thenReturn(bidsJson);

        MarketplaceDataService service = new MarketplaceDataService(
                RestClient.builder(), "http://localhost:8080", "/api/ai/market-rates", "", mockClient);

        List<MarketplaceDocument> docs = service.fetchBidsDocuments();
        assertNotNull(docs);
        assertEquals(1, docs.size());

        MarketplaceDocument doc = docs.get(0);
        assertEquals("MARKETPLACE_DATA", doc.getMetadata().get("type"));
        assertEquals("bids", doc.getMetadata().get("source"));
        assertEquals("BID", doc.getMetadata().get("category"));
        assertEquals(401L, doc.getMetadata().get("id"));
        assertEquals("Cotton", doc.getMetadata().get("commodity"));
        assertEquals("BDR-4001", doc.getMetadata().get("bidder"));
        assertEquals(6200.0, doc.getMetadata().get("bidRate"));
        assertEquals(6150.0, doc.getMetadata().get("counterRate"));
        assertTrue(doc.getContent().contains("Bid Number: BID-2026-001"));
        assertTrue(doc.getContent().contains("Commodity: Cotton"));
        assertTrue(doc.getContent().contains("Bid Rate: ₹6200.0"));
    }

    @Test
    @DisplayName("fetchBidsDocuments returns empty list on empty data.content")
    void testFetchBidsDocuments_EmptyPage() {
        String emptyJson = "{\"success\":true,\"message\":\"Bids retrieved successfully\",\"data\":{\"content\":[],\"totalElements\":0}}";

        com.example.Commodity_Admin_AI.marketplaceAllAPi.MarketplaceApiClient mockClient =
                org.mockito.Mockito.mock(com.example.Commodity_Admin_AI.marketplaceAllAPi.MarketplaceApiClient.class);
        org.mockito.Mockito.when(mockClient.getBids()).thenReturn(emptyJson);

        MarketplaceDataService service = new MarketplaceDataService(
                RestClient.builder(), "http://localhost:8080", "/api/ai/market-rates", "", mockClient);

        List<MarketplaceDocument> docs = service.fetchBidsDocuments();
        assertNotNull(docs);
        assertTrue(docs.isEmpty());
    }

    @Test
    @DisplayName("Unsupported legacy sources return empty lists without failing when exceptions thrown")
    void testUnsupportedLegacySources_SkippedGracefully() {
        com.example.Commodity_Admin_AI.marketplaceAllAPi.MarketplaceApiClient mockClient =
                org.mockito.Mockito.mock(com.example.Commodity_Admin_AI.marketplaceAllAPi.MarketplaceApiClient.class);
        org.mockito.Mockito.when(mockClient.getMarketRates()).thenThrow(new UnsupportedOperationException("No market-rates"));
        org.mockito.Mockito.when(mockClient.getRateHistory()).thenThrow(new UnsupportedOperationException("No rate-history"));
        org.mockito.Mockito.when(mockClient.getNotifications()).thenThrow(new UnsupportedOperationException("No notifications"));
        org.mockito.Mockito.when(mockClient.getWhatsAppLogs()).thenThrow(new UnsupportedOperationException("No whatsapp-logs"));
        org.mockito.Mockito.when(mockClient.getOrders()).thenThrow(new UnsupportedOperationException("No orders"));

        MarketplaceDataService service = new MarketplaceDataService(
                RestClient.builder(), "http://localhost:8080", "/api/ai/market-rates", "", mockClient);

        assertTrue(service.fetchMarketRatesDocuments().isEmpty(), "Market rates should return empty list");
        assertTrue(service.fetchRateHistoryDocuments().isEmpty(), "Rate history should return empty list");
        assertTrue(service.fetchNotificationsDocuments().isEmpty(), "Notifications should return empty list");
        assertTrue(service.fetchWhatsAppLogsDocuments().isEmpty(), "WhatsApp logs should return empty list");
        assertTrue(service.fetchOrdersDocuments().isEmpty(), "Orders should return empty list");
    }

    @Test
    @DisplayName("Empty response from commodities does not prevent supplies, demands, or bids from syncing")
    void testEmptyResponseFromOneEndpoint_DoesNotBlockOthers() {
        com.example.Commodity_Admin_AI.marketplaceAllAPi.MarketplaceApiClient mockClient =
                org.mockito.Mockito.mock(com.example.Commodity_Admin_AI.marketplaceAllAPi.MarketplaceApiClient.class);

        // Commodities is empty
        org.mockito.Mockito.when(mockClient.getCommodities())
                .thenReturn("{\"success\":true,\"message\":\"ok\",\"data\":{\"content\":[]}}");

        // Supplies has 1 record
        String suppliesJson = """
            {
              "success": true,
              "message": "ok",
              "data": [
                {
                  "id": 1,
                  "commodityName": "Soybean",
                  "availableQuantity": 100.0,
                  "askingRate": 4000.0,
                  "status": "AVAILABLE"
                }
              ]
            }
            """;
        org.mockito.Mockito.when(mockClient.getSupplies()).thenReturn(suppliesJson);

        // Demands has 1 record
        String demandsJson = """
            {
              "success": true,
              "message": "ok",
              "data": [
                {
                  "id": 2,
                  "commodityName": "Wheat",
                  "quantity": 50.0,
                  "targetRate": 2800.0,
                  "status": "OPEN"
                }
              ]
            }
            """;
        org.mockito.Mockito.when(mockClient.getDemands()).thenReturn(demandsJson);

        // Bids has 1 record
        String bidsJson = """
            {
              "success": true,
              "message": "ok",
              "data": {
                "content": [
                  {
                    "id": 3,
                    "commodityName": "Cotton",
                    "bidRate": 6000.0,
                    "quantity": 25.0,
                    "status": "ACTIVE"
                  }
                ]
              }
            }
            """;
        org.mockito.Mockito.when(mockClient.getBids()).thenReturn(bidsJson);

        // Legacy sources throw unsupported
        org.mockito.Mockito.when(mockClient.getMarketRates()).thenThrow(new UnsupportedOperationException("none"));
        org.mockito.Mockito.when(mockClient.getRateHistory()).thenThrow(new UnsupportedOperationException("none"));
        org.mockito.Mockito.when(mockClient.getNotifications()).thenThrow(new UnsupportedOperationException("none"));
        org.mockito.Mockito.when(mockClient.getWhatsAppLogs()).thenThrow(new UnsupportedOperationException("none"));

        MarketplaceDataService service = new MarketplaceDataService(
                RestClient.builder(), "http://localhost:8080", "/api/ai/market-rates", "", mockClient);

        List<MarketplaceDocument> allDocs = service.fetchAllMarketplaceDocuments();
        assertNotNull(allDocs);
        assertEquals(3, allDocs.size(), "Must contain 3 documents (1 supply + 1 demand + 1 bid) despite commodities being empty");
    }

    @Test
    @DisplayName("deriveMarketRatesFromSupplies prioritizes validTill over createdAt")
    void deriveMarketRatesFromSupplies_PrioritizesValidTillOverCreatedAt() {
        String suppliesJson = """
            {
              "success": true,
              "message": "Supplies retrieved successfully",
              "data": [
                {
                  "id": 3,
                  "commodityName": "Soybean",
                  "location": "Sangli",
                  "askingRate": 4500.0,
                  "validTill": "2026-10-16",
                  "createdAt": "2026-10-21T14:21:01.874074"
                },
                {
                  "id": 4,
                  "commodityName": "Soybean",
                  "location": "Sangli",
                  "askingRate": 4800.0,
                  "validTill": "2026-10-17",
                  "createdAt": "2026-10-21T14:21:31.80659"
                }
              ]
            }
            """;

        com.example.Commodity_Admin_AI.marketplaceAllAPi.MarketplaceApiClient mockClient =
                org.mockito.Mockito.mock(com.example.Commodity_Admin_AI.marketplaceAllAPi.MarketplaceApiClient.class);
        org.mockito.Mockito.when(mockClient.getSupplies()).thenReturn(suppliesJson);

        MarketplaceDataService service = new MarketplaceDataService(
                RestClient.builder(), "http://localhost:8080", "/api/ai/market-rates", "", mockClient);

        List<MarketplaceRateDto> rates = service.deriveMarketRatesFromSupplies();
        assertNotNull(rates);
        assertEquals(2, rates.size());
        assertEquals("2026-10-16", rates.get(0).getDate());
        assertEquals(new BigDecimal("4500.0"), rates.get(0).getRate());
        assertEquals("2026-10-17", rates.get(1).getDate());
        assertEquals(new BigDecimal("4800.0"), rates.get(1).getRate());
    }
}

