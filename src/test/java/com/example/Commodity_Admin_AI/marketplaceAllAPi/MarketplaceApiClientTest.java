package com.example.Commodity_Admin_AI.marketplaceAllAPi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MarketplaceApiClientTest {

    private MarketplaceApiClient apiClient;
    private static final String BASE_URL = "http://localhost:8080";

    @BeforeEach
    void setUp() {
        apiClient = new MarketplaceApiClient(BASE_URL);
    }

    private boolean isAiControllerAvailable() {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL("http://localhost:8080/api/ai/commodities").openConnection();
            con.setRequestMethod("GET");
            con.setConnectTimeout(2000);
            con.setReadTimeout(2000);
            return con.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @DisplayName("Verify getBaseUrl returns configured base URL")
    void testGetBaseUrl() {
        assertEquals(BASE_URL, apiClient.getBaseUrl());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Unsupported Legacy Endpoints (Verified Exception Contracts)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Verify getMarketRates throws UnsupportedOperationException (no Tradex equivalent)")
    void testGetMarketRates_Unsupported() {
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class, () -> apiClient.getMarketRates());
        assertTrue(ex.getMessage().contains("No direct equivalent endpoint exists in Tradex backend"));
    }

    @Test
    @DisplayName("Verify getRateHistory throws UnsupportedOperationException (no Tradex equivalent)")
    void testGetRateHistory_Unsupported() {
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class, () -> apiClient.getRateHistory());
        assertTrue(ex.getMessage().contains("No direct equivalent endpoint exists in Tradex backend"));

        UnsupportedOperationException ex2 = assertThrows(UnsupportedOperationException.class, () -> apiClient.getRateHistory("Soybean", "Sangli"));
        assertTrue(ex2.getMessage().contains("No direct equivalent endpoint exists in Tradex backend"));
    }

    @Test
    @DisplayName("Verify getNotifications throws UnsupportedOperationException (unsupported in Tradex)")
    void testGetNotifications_Unsupported() {
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class, () -> apiClient.getNotifications());
        assertTrue(ex.getMessage().contains("Unsupported endpoint in Tradex backend"));
    }

    @Test
    @DisplayName("Verify getWhatsAppLogs throws UnsupportedOperationException (unsupported in Tradex)")
    void testGetWhatsAppLogs_Unsupported() {
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class, () -> apiClient.getWhatsAppLogs());
        assertTrue(ex.getMessage().contains("Unsupported endpoint in Tradex backend"));
    }

    @Test
    @DisplayName("Verify getOrders throws UnsupportedOperationException (Tradex uses Deals/Contracts)")
    void testGetOrders_Unsupported() {
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class, () -> apiClient.getOrders());
        assertTrue(ex.getMessage().contains("No direct equivalent endpoint exists in Tradex backend"));

        UnsupportedOperationException ex2 = assertThrows(UnsupportedOperationException.class, () -> apiClient.getOrders(Map.of("status", "COMPLETED")));
        assertTrue(ex2.getMessage().contains("No direct equivalent endpoint exists in Tradex backend"));
    }

    @Test
    @DisplayName("Verify getOrderById throws UnsupportedOperationException (Tradex uses Deals/Contracts)")
    void testGetOrderById_Unsupported() {
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class, () -> apiClient.getOrderById(1L));
        assertTrue(ex.getMessage().contains("No direct equivalent endpoint exists in Tradex backend"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Live Tradex Endpoints (Tested conditionally when Tradex AiMarketplaceController is active on :8080)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Verify getCommodities calls GET /api/ai/commodities against Tradex backend")
    void testGetCommodities_Live() {
        assumeTrue(isAiControllerAvailable(), "Tradex /api/ai controller not active on port 8080");

        String response = apiClient.getCommodities();
        assertNotNull(response);
        assertFalse(response.isBlank());
        assertTrue(response.contains("success"));
        System.out.println("[LIVE TEST] /api/ai/commodities response:\n" + response);
    }

    @Test
    @DisplayName("Verify getSupplies calls GET /api/ai/supplies against Tradex backend")
    void testGetSupplies_Live() {
        assumeTrue(isAiControllerAvailable(), "Tradex /api/ai controller not active on port 8080");

        String response = apiClient.getSupplies();
        assertNotNull(response);
        assertFalse(response.isBlank());
        assertTrue(response.contains("success"));
        System.out.println("[LIVE TEST] /api/ai/supplies response:\n" + response);
    }

    @Test
    @DisplayName("Verify getDemands calls GET /api/ai/demands against Tradex backend")
    void testGetDemands_Live() {
        assumeTrue(isAiControllerAvailable(), "Tradex /api/ai controller not active on port 8080");

        String response = apiClient.getDemands();
        assertNotNull(response);
        assertFalse(response.isBlank());
        assertTrue(response.contains("success"));
        System.out.println("[LIVE TEST] /api/ai/demands response:\n" + response);
    }

    @Test
    @DisplayName("Verify getBids calls GET /api/ai/bids against Tradex backend")
    void testGetBids_Live() {
        assumeTrue(isAiControllerAvailable(), "Tradex /api/ai controller not active on port 8080");

        String response = apiClient.getBids();
        assertNotNull(response);
        assertFalse(response.isBlank());
        assertTrue(response.contains("success"));
        System.out.println("[LIVE TEST] /api/ai/bids response:\n" + response);
    }
}
