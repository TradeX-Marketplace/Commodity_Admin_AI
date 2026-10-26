package com.example.Commodity_Admin_AI.controller;

import com.example.Commodity_Admin_AI.dto.GlobalMarketPriceResponseDto;
import com.example.Commodity_Admin_AI.service.DataGovInMarketPriceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller exposing read-only endpoints for external global/national mandi market prices
 * powered by the official data.gov.in (AGMARKNET) dataset.
 *
 * All operations are strictly read-only HTTP GET requests.
 * No data is written to or mutated in the internal marketplace database.
 */
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"})
@RestController
@RequestMapping("/api/global-market")
public class GlobalMarketPriceController {

    private final DataGovInMarketPriceService marketPriceService;

    public GlobalMarketPriceController(DataGovInMarketPriceService marketPriceService) {
        this.marketPriceService = marketPriceService;
    }

    /**
     * Query external mandi market prices from the official data.gov.in AGMARKNET dataset.
     *
     * Example:
     * GET /api/global-market/rates?commodity=Wheat&state=Maharashtra&limit=20
     *
     * @param commodity   optional crop/commodity filter (e.g. "Wheat", "Soyabean")
     * @param state       optional state filter (e.g. "Maharashtra")
     * @param district    optional district filter (e.g. "Pune", "Sangli")
     * @param market      optional mandi market filter
     * @param arrivalDate optional arrival date filter (DD/MM/YYYY)
     * @param limit       max records to return (default 20, max 100)
     * @param offset      pagination offset (default 0)
     * @return GlobalMarketPriceResponseDto with status, pagination, and matching mandi records
     */
    @GetMapping("/rates")
    public ResponseEntity<GlobalMarketPriceResponseDto> getMarketRates(
            @RequestParam(required = false) String commodity,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String market,
            @RequestParam(required = false) String arrivalDate,
            @RequestParam(required = false, defaultValue = "20") Integer limit,
            @RequestParam(required = false, defaultValue = "0") Integer offset) {

        GlobalMarketPriceResponseDto response = marketPriceService.fetchMarketPrices(
                commodity, state, district, market, arrivalDate, limit, offset);

        if ("CONFIG_ERROR".equalsIgnoreCase(response.getStatus())) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }

        if ("API_ERROR".equalsIgnoreCase(response.getStatus())) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Diagnostic endpoint indicating whether external mandi price integration is configured.
     * Never exposes credentials or secrets.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getIntegrationStatus() {
        boolean configured = marketPriceService.isConfigured();
        return ResponseEntity.ok(Map.of(
                "provider", "data.gov.in (AGMARKNET)",
                "resourceId", "9ef84268-d588-465a-a308-a864a43d0070",
                "configured", configured,
                "status", configured ? "READY" : "API_KEY_REQUIRED",
                "message", configured
                        ? "External mandi price data source is configured and ready."
                        : "DATA_GOV_IN_API_KEY environment variable is not set."
        ));
    }
}
