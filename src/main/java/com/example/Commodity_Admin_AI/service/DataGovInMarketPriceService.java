package com.example.Commodity_Admin_AI.service;

import com.example.Commodity_Admin_AI.config.DataGovInProperties;
import com.example.Commodity_Admin_AI.dto.DataGovInRawResponseDto;
import com.example.Commodity_Admin_AI.dto.GlobalMarketPriceRecordDto;
import com.example.Commodity_Admin_AI.dto.GlobalMarketPriceResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service client responsible for querying the official Government of India
 * Open Government Data (data.gov.in) AGMARKNET mandi market prices API.
 */
@Service
public class DataGovInMarketPriceService {

    private static final Logger log = LoggerFactory.getLogger(DataGovInMarketPriceService.class);

    private final DataGovInProperties properties;
    private final RestClient restClient;

    @Autowired
    public DataGovInMarketPriceService(DataGovInProperties properties) {
        this.properties = properties;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));

        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Constructor for unit testing with custom RestClient.
     */
    public DataGovInMarketPriceService(DataGovInProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    /**
     * Checks if the external data.gov.in integration is properly configured with an API key.
     */
    public boolean isConfigured() {
        return properties.isApiKeyConfigured();
    }

    /**
     * Query mandi prices with commodity filter.
     */
    public GlobalMarketPriceResponseDto fetchMarketPrices(String commodity) {
        return fetchMarketPrices(commodity, null, null, null, null, 20, 0);
    }

    /**
     * Query mandi prices with commodity, state, and district filters.
     */
    public GlobalMarketPriceResponseDto fetchMarketPrices(String commodity, String state, String district) {
        return fetchMarketPrices(commodity, state, district, null, null, 20, 0);
    }

    /**
     * Primary query method supporting verified official filters: commodity, state, district, market, arrival_date,
     * limit, and offset.
     *
     * @param commodity   commodity name (e.g. "Wheat", "Soyabean")
     * @param state       state name (e.g. "Maharashtra")
     * @param district    district name (e.g. "Pune", "Sangli")
     * @param market      mandi name (e.g. "Pune")
     * @param arrivalDate arrival date (DD/MM/YYYY)
     * @param limit       max records to return (default 20, max 100)
     * @param offset      pagination offset (default 0)
     * @return structured GlobalMarketPriceResponseDto
     */
    public GlobalMarketPriceResponseDto fetchMarketPrices(String commodity, String state, String district,
                                                          String market, String arrivalDate,
                                                          Integer limit, Integer offset) {
        int effectiveLimit = (limit != null && limit > 0) ? Math.min(limit, 100) : 20;
        int effectiveOffset = (offset != null && offset >= 0) ? offset : 0;

        String cleanCommodity = sanitizeInput(commodity);
        String cleanState = sanitizeInput(state);
        String cleanDistrict = sanitizeInput(district);
        String cleanMarket = sanitizeInput(market);
        String cleanArrivalDate = sanitizeInput(arrivalDate);

        // Security check: verify API key configuration
        if (!properties.isApiKeyConfigured()) {
            log.warn("data.gov.in API key is missing. Set DATA_GOV_IN_API_KEY environment variable to enable live mandi data.");
            return GlobalMarketPriceResponseDto.configError(
                    "data.gov.in API key is not configured. Please set the DATA_GOV_IN_API_KEY environment variable to access live mandi prices.");
        }

        // Build request URI with verified official parameters
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath("/resource/" + properties.getResourceId())
                .queryParam("api-key", properties.getApiKey())
                .queryParam("format", "json")
                .queryParam("limit", effectiveLimit)
                .queryParam("offset", effectiveOffset);

        if (cleanCommodity != null) {
            uriBuilder.queryParam("filters[commodity]", cleanCommodity);
        }
        if (cleanState != null) {
            uriBuilder.queryParam("filters[state]", cleanState);
        }
        if (cleanDistrict != null) {
            uriBuilder.queryParam("filters[district]", cleanDistrict);
        }
        if (cleanMarket != null) {
            uriBuilder.queryParam("filters[market]", cleanMarket);
        }
        if (cleanArrivalDate != null) {
            uriBuilder.queryParam("filters[arrival_date]", cleanArrivalDate);
        }

        String uriString = uriBuilder.build().toUriString();

        // Safe logging without exposing secret API key
        log.info("Querying data.gov.in market prices: resource={}, commodity={}, state={}, district={}, market={}, arrivalDate={}, limit={}, offset={}",
                properties.getResourceId(), cleanCommodity, cleanState, cleanDistrict, cleanMarket, cleanArrivalDate, effectiveLimit, effectiveOffset);

        try {
            DataGovInRawResponseDto rawResponse = restClient.get()
                    .uri(uriString)
                    .retrieve()
                    .body(DataGovInRawResponseDto.class);

            if (rawResponse == null || rawResponse.getRecords() == null || rawResponse.getRecords().isEmpty()) {
                log.info("data.gov.in returned 0 records for commodity={}, state={}, district={}, market={}",
                        cleanCommodity, cleanState, cleanDistrict, cleanMarket);
                return GlobalMarketPriceResponseDto.empty(
                        "No mandi records found matching the requested filters.",
                        effectiveLimit, effectiveOffset, cleanCommodity, cleanState, cleanDistrict, cleanMarket);
            }

            List<GlobalMarketPriceRecordDto> records = new ArrayList<>();
            for (DataGovInRawResponseDto.RawRecord raw : rawResponse.getRecords()) {
                records.add(mapToRecordDto(raw));
            }

            long total = parseTotal(rawResponse.getTotal(), records.size());
            int count = rawResponse.getCount() != null ? rawResponse.getCount() : records.size();

            log.info("Successfully fetched {} data.gov.in mandi price records (total reported: {})",
                    records.size(), total);

            return GlobalMarketPriceResponseDto.success(
                    records, total, count, effectiveLimit, effectiveOffset,
                    cleanCommodity, cleanState, cleanDistrict, cleanMarket);

        } catch (RestClientResponseException ex) {
            log.error("data.gov.in API returned HTTP error {}: {}", ex.getStatusCode(), ex.getStatusText());
            return GlobalMarketPriceResponseDto.apiError(
                    "External data.gov.in API returned status " + ex.getStatusCode().value() + ": " + ex.getStatusText());
        } catch (Exception ex) {
            log.error("Failed to connect to data.gov.in API: {}", ex.getMessage());
            return GlobalMarketPriceResponseDto.apiError(
                    "Failed to communicate with external data.gov.in service: " + ex.getMessage());
        }
    }

    private GlobalMarketPriceRecordDto mapToRecordDto(DataGovInRawResponseDto.RawRecord raw) {
        BigDecimal min = parsePrice(raw.getMinPrice());
        BigDecimal max = parsePrice(raw.getMaxPrice());
        BigDecimal modal = parsePrice(raw.getModalPrice());

        return new GlobalMarketPriceRecordDto(
                raw.getState(),
                raw.getDistrict(),
                raw.getMarket(),
                raw.getCommodity(),
                raw.getVariety(),
                raw.getGrade(),
                raw.getArrivalDate(),
                min,
                max,
                modal
        );
    }

    private BigDecimal parsePrice(String priceStr) {
        if (priceStr == null || priceStr.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(priceStr.trim().replace(",", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private long parseTotal(Object totalObj, long fallback) {
        if (totalObj == null) {
            return fallback;
        }
        if (totalObj instanceof Number) {
            return ((Number) totalObj).longValue();
        }
        try {
            return Long.parseLong(totalObj.toString().trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private String sanitizeInput(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        return input.trim();
    }
}
