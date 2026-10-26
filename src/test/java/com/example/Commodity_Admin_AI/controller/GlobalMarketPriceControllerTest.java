package com.example.Commodity_Admin_AI.controller;

import com.example.Commodity_Admin_AI.dto.GlobalMarketPriceRecordDto;
import com.example.Commodity_Admin_AI.dto.GlobalMarketPriceResponseDto;
import com.example.Commodity_Admin_AI.service.DataGovInMarketPriceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GlobalMarketPriceControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DataGovInMarketPriceService marketPriceService;

    @BeforeEach
    void setUp() {
        GlobalMarketPriceController controller = new GlobalMarketPriceController(marketPriceService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/global-market/rates returns 200 and records when data is retrieved successfully")
    void getRates_Success_Returns200() throws Exception {
        GlobalMarketPriceRecordDto record = new GlobalMarketPriceRecordDto(
                "Maharashtra", "Pune", "Pune", "Wheat", "Lokwan", "FAQ", "15/10/2026",
                new BigDecimal("2600.00"), new BigDecimal("3100.00"), new BigDecimal("2850.00")
        );
        GlobalMarketPriceResponseDto mockResponse = GlobalMarketPriceResponseDto.success(
                List.of(record), 1, 1, 20, 0, "Wheat", "Maharashtra", null, null
        );

        when(marketPriceService.fetchMarketPrices(eq("Wheat"), eq("Maharashtra"), isNull(), isNull(), isNull(), eq(20), eq(0)))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/api/global-market/rates")
                        .param("commodity", "Wheat")
                        .param("state", "Maharashtra")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.records[0].commodity").value("Wheat"))
                .andExpect(jsonPath("$.records[0].market").value("Pune"))
                .andExpect(jsonPath("$.records[0].modalPrice").value(2850.00))
                .andExpect(jsonPath("$.records[0].priceUnit").value("₹ per Quintal"));

        verify(marketPriceService).fetchMarketPrices(eq("Wheat"), eq("Maharashtra"), isNull(), isNull(), isNull(), eq(20), eq(0));
    }

    @Test
    @DisplayName("GET /api/global-market/rates returns 503 when API key is not configured")
    void getRates_ConfigError_Returns503() throws Exception {
        GlobalMarketPriceResponseDto mockResponse = GlobalMarketPriceResponseDto.configError(
                "data.gov.in API key is not configured. Please set the DATA_GOV_IN_API_KEY environment variable to access live mandi prices."
        );

        when(marketPriceService.fetchMarketPrices(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/api/global-market/rates")
                        .param("commodity", "Wheat"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("CONFIG_ERROR"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("DATA_GOV_IN_API_KEY")));
    }

    @Test
    @DisplayName("GET /api/global-market/rates returns 502 when upstream data.gov.in API errors out")
    void getRates_ApiError_Returns502() throws Exception {
        GlobalMarketPriceResponseDto mockResponse = GlobalMarketPriceResponseDto.apiError(
                "External data.gov.in API returned status 500: Internal Server Error"
        );

        when(marketPriceService.fetchMarketPrices(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/api/global-market/rates")
                        .param("commodity", "Soyabean"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value("API_ERROR"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Internal Server Error")));
    }

    @Test
    @DisplayName("GET /api/global-market/status returns integration configuration health")
    void getStatus_ReturnsHealth() throws Exception {
        when(marketPriceService.isConfigured()).thenReturn(false);

        mockMvc.perform(get("/api/global-market/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("data.gov.in (AGMARKNET)"))
                .andExpect(jsonPath("$.resourceId").value("9ef84268-d588-465a-a308-a864a43d0070"))
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.status").value("API_KEY_REQUIRED"));
    }
}
