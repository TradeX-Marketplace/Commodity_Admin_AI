package com.example.Commodity_Admin_AI.service;

import com.example.Commodity_Admin_AI.config.DataGovInProperties;
import com.example.Commodity_Admin_AI.dto.GlobalMarketPriceResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class DataGovInMarketPriceServiceTest {

    @Test
    @DisplayName("Returns CONFIG_ERROR when DATA_GOV_IN_API_KEY is not configured")
    void testMissingApiKey_ReturnsConfigError() {
        DataGovInProperties properties = new DataGovInProperties(
                "https://api.data.gov.in",
                "9ef84268-d588-465a-a308-a864a43d0070",
                "", // empty API key
                5000,
                10000
        );

        DataGovInMarketPriceService service = new DataGovInMarketPriceService(properties);

        assertFalse(service.isConfigured());

        GlobalMarketPriceResponseDto response = service.fetchMarketPrices("Wheat");

        assertNotNull(response);
        assertEquals("CONFIG_ERROR", response.getStatus());
        assertTrue(response.getMessage().contains("DATA_GOV_IN_API_KEY"));
        assertTrue(response.getRecords().isEmpty());
    }

    @Test
    @DisplayName("Successfully maps data.gov.in response and filters to DTOs")
    void testSuccessfulQuery_MapsRecords() {
        DataGovInProperties properties = new DataGovInProperties(
                "https://api.data.gov.in",
                "9ef84268-d588-465a-a308-a864a43d0070",
                "mock-api-key-12345",
                5000,
                10000
        );

        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.data.gov.in");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        DataGovInMarketPriceService service = new DataGovInMarketPriceService(properties, restClient);

        String jsonPayload = """
                {
                  "status": "ok",
                  "title": "Current Daily Price of Various Commodities from Various Markets (Mandi)",
                  "total": "1",
                  "count": 1,
                  "limit": "20",
                  "offset": "0",
                  "records": [
                    {
                      "state": "Maharashtra",
                      "district": "Pune",
                      "market": "Pune",
                      "commodity": "Wheat",
                      "variety": "Lokwan",
                      "grade": "FAQ",
                      "arrival_date": "15/10/2026",
                      "min_price": "2600.00",
                      "max_price": "3100.00",
                      "modal_price": "2850.00"
                    }
                  ]
                }
                """;

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/resource/9ef84268-d588-465a-a308-a864a43d0070")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(requestTo(org.hamcrest.Matchers.containsString("filters%5Bcommodity%5D=Wheat")))
                .andExpect(requestTo(org.hamcrest.Matchers.containsString("filters%5Bstate%5D=Maharashtra")))
                .andRespond(withSuccess(jsonPayload, MediaType.APPLICATION_JSON));

        GlobalMarketPriceResponseDto response = service.fetchMarketPrices("Wheat", "Maharashtra", null);

        server.verify();

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals(1, response.getTotal());
        assertEquals(1, response.getCount());
        assertEquals(1, response.getRecords().size());

        var record = response.getRecords().get(0);
        assertEquals("Maharashtra", record.getState());
        assertEquals("Pune", record.getDistrict());
        assertEquals("Pune", record.getMarket());
        assertEquals("Wheat", record.getCommodity());
        assertEquals("Lokwan", record.getVariety());
        assertEquals("FAQ", record.getGrade());
        assertEquals("15/10/2026", record.getArrivalDate());
        assertEquals(new BigDecimal("2600.00"), record.getMinPrice());
        assertEquals(new BigDecimal("3100.00"), record.getMaxPrice());
        assertEquals(new BigDecimal("2850.00"), record.getModalPrice());
        assertEquals("₹ per Quintal", record.getPriceUnit());
        assertEquals("data.gov.in (AGMARKNET)", record.getSource());
    }

    @Test
    @DisplayName("Handles empty records response gracefully")
    void testEmptyRecords_ReturnsEmptyStatus() {
        DataGovInProperties properties = new DataGovInProperties(
                "https://api.data.gov.in",
                "9ef84268-d588-465a-a308-a864a43d0070",
                "mock-api-key-12345",
                5000,
                10000
        );

        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.data.gov.in");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        DataGovInMarketPriceService service = new DataGovInMarketPriceService(properties, restClient);

        String jsonPayload = """
                {
                  "status": "ok",
                  "total": 0,
                  "count": 0,
                  "records": []
                }
                """;

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/resource/9ef84268-d588-465a-a308-a864a43d0070")))
                .andRespond(withSuccess(jsonPayload, MediaType.APPLICATION_JSON));

        GlobalMarketPriceResponseDto response = service.fetchMarketPrices("UnknownCrop");

        server.verify();

        assertNotNull(response);
        assertEquals("EMPTY", response.getStatus());
        assertEquals(0, response.getTotal());
        assertTrue(response.getRecords().isEmpty());
    }

    @Test
    @DisplayName("Handles external HTTP error response gracefully without throwing")
    void testHttpError_ReturnsApiErrorStatus() {
        DataGovInProperties properties = new DataGovInProperties(
                "https://api.data.gov.in",
                "9ef84268-d588-465a-a308-a864a43d0070",
                "mock-api-key-12345",
                5000,
                10000
        );

        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.data.gov.in");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        DataGovInMarketPriceService service = new DataGovInMarketPriceService(properties, restClient);

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/resource/9ef84268-d588-465a-a308-a864a43d0070")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        GlobalMarketPriceResponseDto response = service.fetchMarketPrices("Wheat");

        server.verify();

        assertNotNull(response);
        assertEquals("API_ERROR", response.getStatus());
        assertTrue(response.getMessage().contains("429"));
        assertTrue(response.getRecords().isEmpty());
    }
}
