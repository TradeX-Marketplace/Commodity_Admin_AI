package com.example.Commodity_Admin_AI.service;

import com.example.Commodity_Admin_AI.dto.BuyerDemandDto;
import com.example.Commodity_Admin_AI.dto.ChartData;
import com.example.Commodity_Admin_AI.dto.MarketplaceDocument;
import com.example.Commodity_Admin_AI.dto.MarketplaceRateDto;
import com.example.Commodity_Admin_AI.dto.OrderDto;
import com.example.Commodity_Admin_AI.dto.OrderFilterDto;
import com.example.Commodity_Admin_AI.dto.PartyDto;
import com.example.Commodity_Admin_AI.dto.PartiesResultDto;
import com.example.Commodity_Admin_AI.dto.SupplyDto;
import com.example.Commodity_Admin_AI.dto.BidDto;
import com.example.Commodity_Admin_AI.dto.NegotiationDto;
import com.example.Commodity_Admin_AI.dto.NegotiationRoundDto;
import com.example.Commodity_Admin_AI.marketplaceAllAPi.MarketplaceApiClient;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service responsible for receiving approved marketplace data from the
 * Marketplace Backend project via controlled HTTP/JSON REST APIs.
 *
 * Strictly adheres to architectural boundaries:
 * - Does NOT connect directly to Marketplace PostgreSQL.
 * - Does NOT use Marketplace database credentials.
 * - Communicates strictly via HTTP/JSON.
 * - Does NOT invent fake live data when the backend is unconfigured.
 */
@Service
public class MarketplaceDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceDataService.class);

    private final RestClient restClient;
    private final String backendBaseUrl;
    private final String marketRatesEndpoint;
    private final String apiKey;
    private final MarketplaceApiClient marketplaceApiClient;
    private JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private SyntheticOrderDataProvider syntheticOrderDataProvider;

    @Value("${marketplace.orders.synthetic-data-enabled:false}")
    private boolean syntheticOrdersEnabled;

    @Autowired
    public MarketplaceDataService(
            @Value("${marketplace.backend.url:${marketplace.backend.base-url:}}") String backendBaseUrl,
            @Value("${marketplace.backend.endpoints.market-rates:/api/ai/market-rates}") String marketRatesEndpoint,
            @Value("${marketplace.backend.api-key:}") String apiKey,
            @Autowired(required = false) MarketplaceApiClient marketplaceApiClient) {
        this(RestClient.builder(), backendBaseUrl, marketRatesEndpoint, apiKey, marketplaceApiClient, null);
    }

    public MarketplaceDataService(
            RestClient.Builder restClientBuilder,
            String backendBaseUrl,
            String marketRatesEndpoint,
            String apiKey) {
        this(restClientBuilder, backendBaseUrl, marketRatesEndpoint, apiKey, null, null);
    }

    public MarketplaceDataService(
            RestClient.Builder restClientBuilder,
            String backendBaseUrl,
            String marketRatesEndpoint,
            String apiKey,
            MarketplaceApiClient marketplaceApiClient) {
        this(restClientBuilder, backendBaseUrl, marketRatesEndpoint, apiKey, marketplaceApiClient, null);
    }

    public MarketplaceDataService(
            RestClient.Builder restClientBuilder,
            String backendBaseUrl,
            String marketRatesEndpoint,
            String apiKey,
            MarketplaceApiClient marketplaceApiClient,
            JdbcTemplate jdbcTemplate) {
        this.backendBaseUrl = backendBaseUrl != null ? backendBaseUrl.trim() : "";
        this.marketRatesEndpoint = marketRatesEndpoint != null ? marketRatesEndpoint.trim() : "/api/ai/market-rates";
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.marketplaceApiClient = marketplaceApiClient;
        this.jdbcTemplate = jdbcTemplate;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        RestClient.Builder builder = restClientBuilder != null ? restClientBuilder : RestClient.builder();
        this.restClient = builder.requestFactory(requestFactory).build();
        this.objectMapper = new ObjectMapper();
    }

    @Autowired(required = false)
    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Autowired(required = false)
    public void setSyntheticOrderDataProvider(SyntheticOrderDataProvider syntheticOrderDataProvider) {
        this.syntheticOrderDataProvider = syntheticOrderDataProvider;
    }

    public void setSyntheticOrdersEnabled(boolean syntheticOrdersEnabled) {
        this.syntheticOrdersEnabled = syntheticOrdersEnabled;
    }

    public boolean isSyntheticOrdersEnabled() {
        return this.syntheticOrdersEnabled;
    }

    public SyntheticOrderDataProvider getSyntheticOrderDataProvider() {
        return this.syntheticOrderDataProvider;
    }

    /**
     * Checks whether the Marketplace Backend REST API base URL is configured.
     *
     * @return true if backend URL or MarketplaceApiClient is available, false otherwise
     */
    public boolean isConfigured() {
        return !backendBaseUrl.isBlank();
    }

    /**
     * Safely extracts the array of records from either:
     * - Tradex Spring Data Page: { "data": { "content": [ ... ] } }
     * - Tradex List response: { "data": [ ... ] }
     * - Legacy direct array: [ ... ]
     * Returns null if no array of records is found.
     */
    public JsonNode extractRecords(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        if (root.isArray()) {
            return root;
        }
        JsonNode data = root.path("data");
        if (data.isArray()) {
            return data;
        }
        JsonNode content = data.path("content");
        if (content.isArray()) {
            return content;
        }
        return null;
    }

    /**
     * Fetches approved market rates from the Marketplace Backend via HTTP/JSON.
     * If the backend is not yet configured or endpoint contract is pending,
     * returns an empty list without generating fake data.
     *
     * @return list of MarketplaceRateDto received from backend, or empty list
     */
    public List<MarketplaceRateDto> fetchMarketRates() {
        if (!isConfigured()) {
            log.info("Marketplace Backend API URL is not configured. Returning empty data without fabricating fake rates.");
            return Collections.emptyList();
        }

        try {
            log.info("[MARKETPLACE SYNC] Base URL: {}", backendBaseUrl);
            log.info("[MARKETPLACE SYNC] Endpoint: {}", marketRatesEndpoint);

            List<MarketplaceRateDto> rates;
            String json = null;

            if (marketplaceApiClient != null) {
                json = marketplaceApiClient.getMarketRates();
                log.info("[MARKETPLACE SYNC] HTTP response received");
            } else {
                String url = backendBaseUrl + marketRatesEndpoint;
                RestClient.RequestHeadersSpec<?> requestSpec = restClient.get()
                        .uri(url)
                        .accept(MediaType.APPLICATION_JSON);

                if (!apiKey.isBlank()) {
                    requestSpec.header("X-API-KEY", apiKey);
                }

                json = requestSpec.retrieve().body(String.class);
                log.info("[MARKETPLACE SYNC] HTTP response received");
            }

            if (json == null || json.isBlank()) {
                log.info("[MARKETPLACE SYNC] Records fetched: 0");
                return Collections.emptyList();
            }

            rates = objectMapper.readValue(json, new TypeReference<List<MarketplaceRateDto>>() {});
            int count = rates != null ? rates.size() : 0;
            log.info("[MARKETPLACE SYNC] Records fetched: {}", count);

            return rates != null ? rates : Collections.emptyList();
        } catch (UnsupportedOperationException ex) {
            log.warn("[MARKETPLACE SYNC] Skipping unsupported legacy source [market-rates]: {}. Deriving current market rates from active Tradex supplies.", ex.getMessage());
            return deriveMarketRatesFromSupplies();
        } catch (Exception ex) {
            log.warn("Failed to fetch market rates from Backend REST API: {}. Falling back to deriving from active supplies.", ex.getMessage());
            return deriveMarketRatesFromSupplies();
        }
    }

    /**
     * Derives current market rates from active Tradex supplies when the standalone /api/ai/market-rates
     * endpoint is not exposed by the Tradex backend.
     */
    public List<MarketplaceRateDto> deriveMarketRatesFromSupplies() {
        if (!isConfigured()) {
            return Collections.emptyList();
        }
        try {
            String json = null;
            if (marketplaceApiClient != null) {
                json = marketplaceApiClient.getSupplies();
            } else {
                String url = backendBaseUrl + "/api/ai/supplies";
                RestClient.RequestHeadersSpec<?> requestSpec = restClient.get()
                        .uri(url)
                        .accept(MediaType.APPLICATION_JSON);
                if (!apiKey.isBlank()) {
                    requestSpec.header("X-API-KEY", apiKey);
                }
                json = requestSpec.retrieve().body(String.class);
            }
            if (json == null || json.isBlank()) {
                return Collections.emptyList();
            }
            JsonNode root = objectMapper.readTree(json);
            JsonNode records = extractRecords(root);
            if (records == null || !records.isArray() || records.isEmpty()) {
                return Collections.emptyList();
            }
            List<MarketplaceRateDto> rates = new ArrayList<>();
            for (JsonNode node : records) {
                double askingRate = node.path("askingRate").asDouble(0.0);
                if (askingRate <= 0) continue;
                String commodity = node.path("commodityName").asText("");
                String location = node.path("location").asText("");
                String dateStr;
                if (node.hasNonNull("validTill") && !node.path("validTill").asText().isBlank()) {
                    String vt = node.path("validTill").asText().trim();
                    dateStr = vt.contains("T") ? vt.substring(0, vt.indexOf('T')) : vt;
                } else if (node.hasNonNull("createdAt") && !node.path("createdAt").asText().isBlank()) {
                    String created = node.path("createdAt").asText().trim();
                    dateStr = created.contains("T") ? created.substring(0, created.indexOf('T')) : created.substring(0, Math.min(10, created.length()));
                } else {
                    dateStr = java.time.LocalDate.now(ZoneId.of("Asia/Kolkata")).toString();
                }
                MarketplaceRateDto dto = new MarketplaceRateDto(
                        commodity,
                        location,
                        BigDecimal.valueOf(askingRate),
                        "Quintal",
                        dateStr
                );
                dto.setPriceType("Asking Price");
                if (node.hasNonNull("id")) {
                    dto.setId(node.path("id").asLong());
                }
                rates.add(dto);
            }
            log.info("Derived {} current market rates from active Tradex supplies", rates.size());
            return rates;
        } catch (Exception e) {
            log.warn("Failed to derive market rates from supplies: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Converts a validated MarketplaceRateDto into a searchable document format
     * ready for vector embedding and PGVector ingestion in Step 10.
     *
     * @param rateDto the approved rate DTO
     * @return structured MarketplaceDocument containing formatted text and metadata
     */
    public MarketplaceDocument convertToSearchableDocument(MarketplaceRateDto rateDto) {
        if (rateDto == null) {
            throw new IllegalArgumentException("MarketplaceRateDto cannot be null");
        }

        String currency = (rateDto.getCurrency() != null && !rateDto.getCurrency().isBlank())
                ? rateDto.getCurrency() : "INR";

        String content = String.format(
                "Market Rate Information:\n" +
                "Commodity: %s\n" +
                "Location: %s\n" +
                "Rate: %s per %s\n" +
                "Currency: %s\n" +
                "Date: %s",
                rateDto.getCommodity(),
                rateDto.getLocation(),
                rateDto.getRate(),
                rateDto.getUnit(),
                currency,
                rateDto.getDate()
        );

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("type", "MARKETPLACE_DATA");
        metadata.put("source", "market-rates");
        metadata.put("category", "MARKET_RATE");
        if (rateDto.getId() != null) {
            metadata.put("id", rateDto.getId());
        }
        metadata.put("commodity", rateDto.getCommodity());
        metadata.put("location", rateDto.getLocation());
        metadata.put("rate", rateDto.getRate());
        metadata.put("unit", rateDto.getUnit());
        metadata.put("currency", currency);
        metadata.put("date", rateDto.getDate());

        String docId = rateDto.getId() != null
                ? UUID.nameUUIDFromBytes(("market-rates:" + rateDto.getId()).getBytes(StandardCharsets.UTF_8)).toString()
                : UUID.nameUUIDFromBytes(("market-rates:" + rateDto.getCommodity() + ":" + rateDto.getLocation() + ":" + rateDto.getDate()).getBytes(StandardCharsets.UTF_8)).toString();

        return new MarketplaceDocument(docId, content, metadata);
    }

    /**
     * Fetches and converts market rates into searchable documents.
     * When market-rates endpoint is unsupported, gracefully skips to avoid duplicate vector indexing with supplies.
     */
    public List<MarketplaceDocument> fetchMarketRatesDocuments() {
        if (!isConfigured() || marketplaceApiClient == null) {
            return Collections.emptyList();
        }
        try {
            String json = marketplaceApiClient.getMarketRates();
            if (json == null || json.isBlank()) {
                return Collections.emptyList();
            }
            List<MarketplaceRateDto> rates = objectMapper.readValue(json, new TypeReference<List<MarketplaceRateDto>>() {});
            if (rates == null || rates.isEmpty()) {
                return Collections.emptyList();
            }
            List<MarketplaceDocument> docs = new ArrayList<>();
            for (MarketplaceRateDto rate : rates) {
                if (rate != null) {
                    docs.add(convertToSearchableDocument(rate));
                }
            }
            return docs;
        } catch (UnsupportedOperationException ex) {
            log.warn("[MARKETPLACE SYNC] Skipping unsupported legacy source [market-rates]: {}", ex.getMessage());
            return Collections.emptyList();
        } catch (Exception ex) {
            log.error("Failed to fetch market rates documents from Backend REST API: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetches commodities from GET /api/ai/commodities and converts them into searchable documents.
     */
    public List<MarketplaceDocument> fetchCommoditiesDocuments() {
        if (!isConfigured() || marketplaceApiClient == null) {
            return Collections.emptyList();
        }
        try {
            int currentPage = 0;
            int totalPages = 1;
            List<MarketplaceDocument> docs = new ArrayList<>();

            while (currentPage < totalPages && currentPage < 50) {
                String json = (currentPage == 0)
                        ? marketplaceApiClient.getCommodities()
                        : marketplaceApiClient.getCommodities(currentPage, 20);
                if (json == null || json.isBlank()) {
                    break;
                }
                JsonNode root = objectMapper.readTree(json);
                JsonNode records = extractRecords(root);
                if (records == null || !records.isArray() || records.isEmpty()) {
                    if (currentPage == 0) {
                        log.info("[MARKETPLACE SYNC] No commodities records found in response.");
                    }
                    break;
                }

                JsonNode dataNode = root.path("data");
                if (dataNode.hasNonNull("totalPages")) {
                    totalPages = dataNode.path("totalPages").asInt(1);
                }

                for (JsonNode node : records) {
                    long recordId = node.path("id").asLong();
                    String name = node.hasNonNull("commodityName")
                            ? node.path("commodityName").asText("")
                            : node.path("name").asText("");
                    String code = node.path("commodityCode").asText("");
                    String category = node.path("category").asText("");
                    String subCategory = node.path("subCategory").asText("");
                    String unit = node.hasNonNull("defaultUnit")
                            ? node.path("defaultUnit").asText("")
                            : node.path("unit").asText("");
                    String description = node.path("description").asText("");
                    String status = node.path("status").asText("");

                    StringBuilder sb = new StringBuilder();
                    sb.append("Commodity Information:\n");
                    sb.append("Name: ").append(name).append("\n");
                    if (!code.isBlank()) {
                        sb.append("Code: ").append(code).append("\n");
                    }
                    sb.append("Category: ").append(category).append("\n");
                    if (!subCategory.isBlank()) {
                        sb.append("Sub-Category: ").append(subCategory).append("\n");
                    }
                    sb.append("Unit: ").append(unit).append("\n");
                    sb.append("Status: ").append(status).append("\n");
                    sb.append("Description: ").append(description);

                    Map<String, Object> metadata = new LinkedHashMap<>();
                    metadata.put("type", "MARKETPLACE_DATA");
                    metadata.put("source", "commodities");
                    metadata.put("category", category.isBlank() ? "COMMODITY" : category);
                    metadata.put("id", recordId);
                    metadata.put("commodity", name);
                    if (!code.isBlank()) {
                        metadata.put("commodityCode", code);
                    }
                    metadata.put("unit", unit);
                    metadata.put("status", status);

                    String docId = UUID.nameUUIDFromBytes(("commodities:" + recordId).getBytes(StandardCharsets.UTF_8)).toString();
                    docs.add(new MarketplaceDocument(docId, sb.toString(), metadata));
                }

                if (dataNode.hasNonNull("last") && dataNode.path("last").asBoolean(true)) {
                    break;
                }
                currentPage++;
            }
            log.info("[MARKETPLACE SYNC] Commodities documents fetched: {}", docs.size());
            return docs;
        } catch (UnsupportedOperationException ex) {
            log.warn("[MARKETPLACE SYNC] Skipping unsupported commodities endpoint: {}", ex.getMessage());
            return Collections.emptyList();
        } catch (Exception ex) {
            log.error("Failed to fetch commodities from Backend REST API: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetches supplies from GET /api/ai/supplies and converts them into searchable documents.
     */
    public List<MarketplaceDocument> fetchSuppliesDocuments() {
        if (!isConfigured() || marketplaceApiClient == null) {
            return Collections.emptyList();
        }
        try {
            String json = marketplaceApiClient.getSupplies();
            if (json == null || json.isBlank()) {
                return Collections.emptyList();
            }
            JsonNode root = objectMapper.readTree(json);
            JsonNode records = extractRecords(root);
            if (records == null || !records.isArray() || records.isEmpty()) {
                log.info("[MARKETPLACE SYNC] No supplies records found in response.");
                return Collections.emptyList();
            }
            List<MarketplaceDocument> docs = new ArrayList<>();
            for (JsonNode node : records) {
                long recordId = node.path("id").asLong();
                String supplyNumber = node.path("supplyNumber").asText("");
                String commodityName = node.path("commodityName").asText("");
                String parentCommodity = node.path("parentCommodityName").asText("");
                String qualityGrade = node.hasNonNull("qualityGrade")
                        ? node.path("qualityGrade").asText("")
                        : node.path("qualityName").asText("");
                double totalQuantity = node.path("totalQuantity").asDouble(0.0);
                double availableQuantity = node.path("availableQuantity").asDouble(0.0);
                double askingRate = node.path("askingRate").asDouble(0.0);
                String location = node.path("location").asText("");
                String originZone = node.path("originZone").asText("");
                String maskedLabel = node.path("maskedLabel").asText("");
                String validTill = node.path("validTill").asText("");
                String status = node.path("status").asText("");

                StringBuilder sb = new StringBuilder();
                sb.append("Supply Information:\n");
                if (!supplyNumber.isBlank()) {
                    sb.append("Supply Number: ").append(supplyNumber).append("\n");
                }
                sb.append("Commodity: ").append(commodityName).append("\n");
                if (!qualityGrade.isBlank()) {
                    sb.append("Quality Grade: ").append(qualityGrade).append("\n");
                }
                sb.append("Available Quantity: ").append(availableQuantity).append("\n");
                sb.append("Total Quantity: ").append(totalQuantity).append("\n");
                if (askingRate > 0) {
                    sb.append("Asking Rate: ₹").append(askingRate).append("\n");
                }
                if (!location.isBlank()) {
                    sb.append("Location: ").append(location).append("\n");
                }
                if (!originZone.isBlank()) {
                    sb.append("Origin Zone: ").append(originZone).append("\n");
                }
                if (!maskedLabel.isBlank()) {
                    sb.append("Seller: ").append(maskedLabel).append("\n");
                }
                if (!validTill.isBlank()) {
                    sb.append("Valid Till: ").append(validTill).append("\n");
                }
                sb.append("Status: ").append(status);

                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("type", "MARKETPLACE_DATA");
                metadata.put("source", "supplies");
                metadata.put("category", "SUPPLY");
                metadata.put("id", recordId);
                if (!supplyNumber.isBlank()) {
                    metadata.put("supplyNumber", supplyNumber);
                }
                metadata.put("commodity", commodityName);
                metadata.put("availableQuantity", availableQuantity);
                metadata.put("totalQuantity", totalQuantity);
                if (askingRate > 0) {
                    metadata.put("askingRate", askingRate);
                }
                if (!location.isBlank()) {
                    metadata.put("location", location);
                }
                if (!maskedLabel.isBlank()) {
                    metadata.put("seller", maskedLabel);
                }
                if (!validTill.isBlank()) {
                    metadata.put("date", validTill);
                }
                metadata.put("status", status);

                String docId = UUID.nameUUIDFromBytes(("supplies:" + recordId).getBytes(StandardCharsets.UTF_8)).toString();
                docs.add(new MarketplaceDocument(docId, sb.toString(), metadata));
            }
            log.info("[MARKETPLACE SYNC] Supplies documents fetched: {}", docs.size());
            return docs;
        } catch (UnsupportedOperationException ex) {
            log.warn("[MARKETPLACE SYNC] Skipping unsupported supplies endpoint: {}", ex.getMessage());
            return Collections.emptyList();
        } catch (Exception ex) {
            log.error("Failed to fetch supplies from Backend REST API: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetches rate history from GET /api/ai/rate-history and converts them into searchable documents.
     */
    public List<MarketplaceDocument> fetchRateHistoryDocuments() {
        if (!isConfigured() || marketplaceApiClient == null) {
            return Collections.emptyList();
        }
        try {
            String json = marketplaceApiClient.getRateHistory();
            if (json == null || json.isBlank()) {
                return Collections.emptyList();
            }
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                return Collections.emptyList();
            }
            List<MarketplaceDocument> docs = new ArrayList<>();
            for (JsonNode node : root) {
                long recordId = node.path("id").asLong();
                String commodityName = node.path("commodityName").asText("");
                String location = node.path("location").asText("");
                double rate = node.path("rate").asDouble();
                String unit = node.path("unit").asText("");
                String rateDate = node.path("rateDate").asText("");

                String content = String.format(
                        "Rate History Information:\n" +
                        "Commodity: %s\n" +
                        "Location: %s\n" +
                        "Rate: %s per %s\n" +
                        "Date: %s",
                        commodityName, location, rate, unit, rateDate
                );

                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("type", "MARKETPLACE_DATA");
                metadata.put("source", "rate-history");
                metadata.put("category", "RATE_HISTORY");
                metadata.put("id", recordId);
                metadata.put("commodity", commodityName);
                metadata.put("location", location);
                metadata.put("rate", rate);
                metadata.put("unit", unit);
                metadata.put("date", rateDate);

                String docId = UUID.nameUUIDFromBytes(("rate-history:" + recordId).getBytes(StandardCharsets.UTF_8)).toString();
                docs.add(new MarketplaceDocument(docId, content, metadata));
            }
            log.info("[MARKETPLACE SYNC] Rate history documents fetched: {}", docs.size());
            return docs;
        } catch (UnsupportedOperationException ex) {
            log.warn("[MARKETPLACE SYNC] Skipping unsupported legacy source [rate-history]: {}", ex.getMessage());
            return Collections.emptyList();
        } catch (Exception ex) {
            log.error("Failed to fetch rate history from Backend REST API: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetches authoritative historical rate records from the Marketplace Backend
     * and maps them into structured ChartData points sorted chronologically.
     *
     * @param commodity optional commodity filter
     * @param location optional location filter
     * @return chronologically sorted list of ChartData points
     */
    public List<ChartData> fetchHistoricalRates(String commodity, String location) {
        if (!isConfigured()) {
            return fetchHistoricalRatesFromDatabase(commodity, location);
        }
        try {
            String json = null;
            if (marketplaceApiClient != null) {
                try {
                    json = marketplaceApiClient.getRateHistory(commodity, location);
                } catch (UnsupportedOperationException ex) {
                    log.warn("[MARKETPLACE SYNC] Skipping unsupported legacy source [rate-history]: {}", ex.getMessage());
                }
            } else {
                String url = backendBaseUrl + "/api/ai/rate-history";
                RestClient.RequestHeadersSpec<?> spec = restClient.get()
                        .uri(uriBuilder -> {
                            uriBuilder.path("/api/ai/rate-history");
                            if (commodity != null && !commodity.isBlank()) {
                                uriBuilder.queryParam("commodity", commodity.trim());
                            }
                            if (location != null && !location.isBlank()) {
                                uriBuilder.queryParam("location", location.trim());
                            }
                            return uriBuilder.build();
                        })
                        .accept(MediaType.APPLICATION_JSON);
                if (!apiKey.isBlank()) {
                    spec.header("X-API-KEY", apiKey);
                }
                json = spec.retrieve().body(String.class);
            }

            if (json != null && !json.isBlank()) {
                JsonNode root = objectMapper.readTree(json);
                if (root.isArray()) {
                    List<ChartData> chartPoints = new ArrayList<>();
                    for (JsonNode node : root) {
                        String rateDate = node.path("rateDate").asText("");
                        double rateVal = node.path("rate").asDouble();
                        String comm = node.path("commodityName").asText("");
                        String loc = node.path("location").asText("");
                        String unit = node.path("unit").asText("");

                        if (commodity != null && !commodity.isBlank() && !comm.equalsIgnoreCase(commodity.trim())) {
                            continue;
                        }
                        if (location != null && !location.isBlank() && !loc.equalsIgnoreCase(location.trim())) {
                            continue;
                        }

                        if (!rateDate.isBlank() && rateVal > 0) {
                            chartPoints.add(new ChartData(rateDate, BigDecimal.valueOf(rateVal), comm, loc, unit));
                        }
                    }
                    if (!chartPoints.isEmpty()) {
                        chartPoints.sort(Comparator.comparing(ChartData::getDate));
                        log.info("Fetched {} authoritative historical rate records for commodity='{}', location='{}'",
                                chartPoints.size(), commodity, location);
                        return chartPoints;
                    }
                }
            }
        } catch (UnsupportedOperationException ex) {
            log.warn("[MARKETPLACE SYNC] Skipping unsupported legacy source [rate-history]: {}", ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to fetch historical rates from Marketplace Backend: {}", ex.getMessage());
        }

        // Database fallback: query stored RATE_HISTORY and genuine MARKET_RATE from vector_store
        List<ChartData> dbPoints = fetchHistoricalRatesFromDatabase(commodity, location);
        Map<String, ChartData> pointsByDate = new LinkedHashMap<>();
        if (dbPoints != null) {
            for (ChartData cd : dbPoints) {
                if (cd.getDate() != null && !cd.getDate().isBlank()) {
                    pointsByDate.put(cd.getDate().trim(), cd);
                }
            }
        }

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        List<ChartData> result = new ArrayList<>();
        for (ChartData cd : pointsByDate.values()) {
            if (cd.getDate() != null && !cd.getDate().isBlank()) {
                try {
                    LocalDate d = LocalDate.parse(cd.getDate().trim());
                    // Historical market rates must never include future-dated records
                    if (!d.isAfter(today)) {
                        result.add(cd);
                    }
                } catch (Exception ex) {
                    result.add(cd);
                }
            }
        }
        result.sort(Comparator.comparing(ChartData::getDate));
        return result;
    }

    /**
     * Retrieves stored genuine historical rate records from the PostgreSQL vector_store table.
     * Queries both RATE_HISTORY and genuine MARKET_RATE observations with deduplication.
     * Does NOT include active seller supply listings or future-dated records.
     */
    public List<ChartData> fetchHistoricalRatesFromDatabase(String commodity, String location) {
        if (jdbcTemplate == null) {
            return Collections.emptyList();
        }
        try {
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
            StringBuilder sql = new StringBuilder(
                "SELECT metadata->>'date' AS dt, " +
                "       COALESCE(metadata->>'rate', metadata->>'askingRate') AS r, " +
                "       metadata->>'commodity' AS comm, " +
                "       metadata->>'location' AS loc, " +
                "       metadata->>'unit' AS unit, " +
                "       metadata->>'category' AS cat " +
                "FROM vector_store " +
                "WHERE metadata->>'category' IN ('RATE_HISTORY', 'MARKET_RATE') " +
                "  AND metadata->>'rate' IS NOT NULL "
            );
            List<Object> params = new ArrayList<>();
            if (commodity != null && !commodity.isBlank()) {
                sql.append("AND LOWER(metadata->>'commodity') = LOWER(?) ");
                params.add(commodity.trim());
            }
            if (location != null && !location.isBlank()) {
                sql.append("AND LOWER(metadata->>'location') = LOWER(?) ");
                params.add(location.trim());
            }
            sql.append("ORDER BY metadata->>'date' ASC, (CASE WHEN metadata->>'category' = 'RATE_HISTORY' THEN 1 ELSE 2 END) ASC");

            List<ChartData> rawPoints = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
                String dt = rs.getString("dt");
                double r = rs.getDouble("r");
                String comm = rs.getString("comm");
                String loc = rs.getString("loc");
                String u = rs.getString("unit");
                return new ChartData(dt, BigDecimal.valueOf(r), comm, loc, u != null && !u.isBlank() ? u : "Quintal", "Historical Record");
            }, params.toArray());

            if (rawPoints != null && !rawPoints.isEmpty()) {
                Map<String, ChartData> uniqueByDate = new LinkedHashMap<>();
                for (ChartData cd : rawPoints) {
                    if (cd.getDate() != null && !cd.getDate().isBlank()) {
                        try {
                            LocalDate d = LocalDate.parse(cd.getDate().trim());
                            if (!d.isAfter(today)) {
                                uniqueByDate.putIfAbsent(cd.getDate().trim(), cd);
                            }
                        } catch (Exception ex) {
                            uniqueByDate.putIfAbsent(cd.getDate().trim(), cd);
                        }
                    }
                }
                List<ChartData> points = new ArrayList<>(uniqueByDate.values());
                points.sort(Comparator.comparing(ChartData::getDate));
                log.info("Retrieved {} genuine historical rate records from database for commodity='{}', location='{}'",
                        points.size(), commodity, location);
                return points;
            }
            return Collections.emptyList();
        } catch (Exception ex) {
            log.warn("Failed to fetch historical rates from database: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Retrieves active seller asking prices separately from marketplace supply listings.
     * These prices represent active supplier listings, NOT closed historical market rates.
     */
    public List<ChartData> fetchSellerAskingPrices(String commodity, String location) {
        Map<String, ChartData> askingPrices = new LinkedHashMap<>();
        if (jdbcTemplate != null) {
            try {
                StringBuilder sql = new StringBuilder(
                    "SELECT metadata->>'date' AS dt, " +
                    "       metadata->>'askingRate' AS r, " +
                    "       metadata->>'commodity' AS comm, " +
                    "       metadata->>'location' AS loc, " +
                    "       metadata->>'unit' AS unit " +
                    "FROM vector_store " +
                    "WHERE metadata->>'category' = 'SUPPLY' " +
                    "  AND metadata->>'askingRate' IS NOT NULL "
                );
                List<Object> params = new ArrayList<>();
                if (commodity != null && !commodity.isBlank()) {
                    sql.append("AND LOWER(metadata->>'commodity') = LOWER(?) ");
                    params.add(commodity.trim());
                }
                if (location != null && !location.isBlank()) {
                    sql.append("AND LOWER(metadata->>'location') = LOWER(?) ");
                    params.add(location.trim());
                }
                sql.append("ORDER BY metadata->>'date' DESC");

                List<ChartData> dbSupplies = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
                    String dt = rs.getString("dt");
                    double r = rs.getDouble("r");
                    String comm = rs.getString("comm");
                    String loc = rs.getString("loc");
                    String u = rs.getString("unit");
                    return new ChartData(dt, BigDecimal.valueOf(r), comm, loc, u != null && !u.isBlank() ? u : "Quintal", "Asking Price");
                }, params.toArray());

                if (dbSupplies != null) {
                    for (ChartData cd : dbSupplies) {
                        if (cd.getDate() != null) {
                            String key = cd.getDate() + ":" + (cd.getLocation() != null ? cd.getLocation() : "");
                            askingPrices.putIfAbsent(key, cd);
                        }
                    }
                }
            } catch (Exception ex) {
                log.debug("Failed to fetch seller asking prices from database: {}", ex.getMessage());
            }
        }

        try {
            List<SupplyDto> supplies = fetchSupplies(commodity, location);
            if (supplies != null) {
                for (SupplyDto s : supplies) {
                    if (s.getAskingRate() != null && s.getAskingRate() > 0) {
                        String dt = (s.getValidTill() != null && !s.getValidTill().isBlank()) ? s.getValidTill() : LocalDate.now(ZoneId.of("Asia/Kolkata")).toString();
                        String loc = (s.getLocation() != null && !s.getLocation().isBlank()) ? s.getLocation() : "Marketplace";
                        String key = dt + ":" + loc;
                        if (!askingPrices.containsKey(key)) {
                            askingPrices.put(key, new ChartData(
                                dt,
                                BigDecimal.valueOf(s.getAskingRate()),
                                s.getCommodityName(),
                                loc,
                                s.getUnit() != null ? s.getUnit() : "Quintal",
                                "Asking Price"
                            ));
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        List<ChartData> list = new ArrayList<>(askingPrices.values());
        list.sort(Comparator.comparing(ChartData::getDate));
        return list;
    }

    /**
     * Dynamically fetches all known commodity names registered in the marketplace
     * from both the TradeX Backend API and stored PostgreSQL vector_store records.
     */
    public Set<String> fetchKnownCommodityNames() {
        Set<String> names = new LinkedHashSet<>();
        names.add("Soybean");
        names.add("Wheat");
        names.add("MAIZEW");
        names.add("MAIZE");
        names.add("Cotton");

        if (isConfigured() && marketplaceApiClient != null) {
            try {
                String json = marketplaceApiClient.getCommodities(0, 50);
                if (json != null && !json.isBlank()) {
                    JsonNode root = objectMapper.readTree(json);
                    JsonNode records = extractRecords(root);
                    if (records != null && records.isArray()) {
                        for (JsonNode node : records) {
                            String name = node.hasNonNull("commodityName")
                                    ? node.path("commodityName").asText("")
                                    : node.path("name").asText("");
                            if (!name.isBlank()) {
                                names.add(name.trim());
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                log.debug("fetchKnownCommodityNames from API failed: {}", ex.getMessage());
            }
        }

        if (jdbcTemplate != null) {
            try {
                List<String> dbComms = jdbcTemplate.query(
                    "SELECT DISTINCT metadata->>'commodity' AS comm FROM vector_store WHERE metadata->>'commodity' IS NOT NULL",
                    (rs, rowNum) -> rs.getString("comm")
                );
                for (String c : dbComms) {
                    if (c != null && !c.isBlank()) {
                        names.add(c.trim());
                    }
                }
            } catch (Exception ex) {
                log.debug("fetchKnownCommodityNames from DB failed: {}", ex.getMessage());
            }
        }

        return names;
    }

    /**
     * Resolves a commodity input string to its canonical name registered in the marketplace.
     */
    public String resolveCanonicalCommodityName(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String trimmed = input.trim();
        Set<String> known = fetchKnownCommodityNames();
        for (String k : known) {
            if (k.equalsIgnoreCase(trimmed)) {
                return k;
            }
        }
        return trimmed.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + (trimmed.length() > 1 ? trimmed.substring(1) : "");
    }

    /**
     * Fetches demands from GET /api/ai/demands and converts them into searchable documents.
     */
    public List<MarketplaceDocument> fetchDemandsDocuments() {
        return fetchDemandsDocumentsInternal("demands");
    }

    /**
     * Fetches buyer demands and converts them into searchable documents (legacy alias).
     */
    public List<MarketplaceDocument> fetchBuyerDemandsDocuments() {
        return fetchDemandsDocumentsInternal("buyer-demands");
    }

    private List<MarketplaceDocument> fetchDemandsDocumentsInternal(String sourceTag) {
        if (!isConfigured() || marketplaceApiClient == null) {
            return Collections.emptyList();
        }
        try {
            String json = null;
            try {
                json = marketplaceApiClient.getDemands();
            } catch (UnsupportedOperationException ignored) {
            }
            if (json == null || json.isBlank()) {
                try {
                    json = marketplaceApiClient.getBuyerDemands();
                } catch (UnsupportedOperationException ignored) {
                }
            }
            if (json == null || json.isBlank()) {
                return Collections.emptyList();
            }
            JsonNode root = objectMapper.readTree(json);
            JsonNode records = extractRecords(root);
            if (records == null || !records.isArray() || records.isEmpty()) {
                log.info("[MARKETPLACE SYNC] No demands records found in response.");
                return Collections.emptyList();
            }
            List<MarketplaceDocument> docs = new ArrayList<>();
            for (JsonNode node : records) {
                long recordId = node.path("id").asLong();
                String demandNumber = node.path("demandNumber").asText("");
                String buyerName = node.hasNonNull("buyerName")
                        ? node.path("buyerName").asText("")
                        : node.path("maskedLabel").asText("");
                String commodityName = node.path("commodityName").asText("");
                String qualityGrade = node.path("qualityGrade").asText("");
                double quantity = node.path("quantity").asDouble(0.0);
                double targetRate = node.path("targetRate").asDouble(0.0);
                String unit = node.path("unit").asText("");
                String location = node.hasNonNull("location")
                        ? node.path("location").asText("")
                        : node.path("destination").asText("");
                String destinationZone = node.path("destinationZone").asText("");
                String requiredDate = node.hasNonNull("requiredDate")
                        ? node.path("requiredDate").asText("")
                        : node.path("validTill").asText("");
                String status = node.path("status").asText("");

                StringBuilder sb = new StringBuilder();
                sb.append("Buyer Demand Information:\n");
                if (!buyerName.isBlank()) {
                    sb.append("Buyer: ").append(buyerName).append("\n");
                }
                if (!demandNumber.isBlank()) {
                    sb.append("Demand Number: ").append(demandNumber).append("\n");
                }
                sb.append("Commodity: ").append(commodityName).append("\n");
                if (!qualityGrade.isBlank()) {
                    sb.append("Quality Grade: ").append(qualityGrade).append("\n");
                }
                sb.append("Quantity: ").append(quantity).append(unit.isBlank() ? "" : " " + unit).append("\n");
                if (targetRate > 0) {
                    sb.append("Target Rate: ₹").append(targetRate).append("\n");
                }
                if (!location.isBlank()) {
                    sb.append("Location: ").append(location).append("\n");
                }
                if (!destinationZone.isBlank()) {
                    sb.append("Destination Zone: ").append(destinationZone).append("\n");
                }
                if (!requiredDate.isBlank()) {
                    sb.append("Required Date: ").append(requiredDate).append("\n");
                }
                sb.append("Status: ").append(status);

                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("type", "MARKETPLACE_DATA");
                metadata.put("source", sourceTag);
                metadata.put("category", "BUYER_DEMAND");
                metadata.put("id", recordId);
                if (!demandNumber.isBlank()) {
                    metadata.put("demandNumber", demandNumber);
                }
                if (!buyerName.isBlank()) {
                    metadata.put("buyer", buyerName);
                }
                metadata.put("commodity", commodityName);
                metadata.put("quantity", quantity);
                if (!unit.isBlank()) {
                    metadata.put("unit", unit);
                }
                if (targetRate > 0) {
                    metadata.put("targetRate", targetRate);
                }
                if (!location.isBlank()) {
                    metadata.put("location", location);
                }
                if (!requiredDate.isBlank()) {
                    metadata.put("date", requiredDate);
                }
                metadata.put("status", status);

                String docId = UUID.nameUUIDFromBytes((sourceTag + ":" + recordId).getBytes(StandardCharsets.UTF_8)).toString();
                docs.add(new MarketplaceDocument(docId, sb.toString(), metadata));
            }
            log.info("[MARKETPLACE SYNC] Demands documents fetched: {}", docs.size());
            return docs;
        } catch (UnsupportedOperationException ex) {
            log.warn("[MARKETPLACE SYNC] Skipping unsupported demands endpoint: {}", ex.getMessage());
            return Collections.emptyList();
        } catch (Exception ex) {
            log.error("Failed to fetch buyer demands from Backend REST API: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetches bids from GET /api/ai/bids and converts them into searchable documents.
     */
    public List<MarketplaceDocument> fetchBidsDocuments() {
        if (!isConfigured() || marketplaceApiClient == null) {
            return Collections.emptyList();
        }
        try {
            int currentPage = 0;
            int totalPages = 1;
            List<MarketplaceDocument> docs = new ArrayList<>();

            while (currentPage < totalPages && currentPage < 50) {
                String json = (currentPage == 0)
                        ? marketplaceApiClient.getBids()
                        : marketplaceApiClient.getBids(currentPage, 20, false, null);
                if (json == null || json.isBlank()) {
                    break;
                }
                JsonNode root = objectMapper.readTree(json);
                JsonNode records = extractRecords(root);
                if (records == null || !records.isArray() || records.isEmpty()) {
                    if (currentPage == 0) {
                        log.info("[MARKETPLACE SYNC] No bids records found in response.");
                    }
                    break;
                }

                JsonNode dataNode = root.path("data");
                if (dataNode.hasNonNull("totalPages")) {
                    totalPages = dataNode.path("totalPages").asInt(1);
                }

                for (JsonNode node : records) {
                    long recordId = node.path("id").asLong();
                    String bidNumber = node.path("bidNumber").asText("");
                    String commodityName = node.path("commodityName").asText("");
                    String maskedBidderId = node.path("maskedBidderId").asText("");
                    double bidRate = node.path("bidRate").asDouble(0.0);
                    double counterRate = node.path("counterRate").asDouble(0.0);
                    double marketRate = node.path("marketRate").asDouble(0.0);
                    double quantity = node.path("quantity").asDouble(0.0);
                    String destination = node.path("destination").asText("");
                    String bidType = node.path("bidType").asText("");
                    String remarks = node.path("remarks").asText("");
                    String counterRemarks = node.path("counterRemarks").asText("");
                    String status = node.path("status").asText("");

                    StringBuilder sb = new StringBuilder();
                    sb.append("Bid Information:\n");
                    if (!bidNumber.isBlank()) {
                        sb.append("Bid Number: ").append(bidNumber).append("\n");
                    }
                    sb.append("Commodity: ").append(commodityName).append("\n");
                    if (!maskedBidderId.isBlank()) {
                        sb.append("Bidder: ").append(maskedBidderId).append("\n");
                    }
                    if (!bidType.isBlank()) {
                        sb.append("Bid Type: ").append(bidType).append("\n");
                    }
                    sb.append("Quantity: ").append(quantity).append("\n");
                    if (bidRate > 0) {
                        sb.append("Bid Rate: ₹").append(bidRate).append("\n");
                    }
                    if (counterRate > 0) {
                        sb.append("Counter Rate: ₹").append(counterRate).append("\n");
                    }
                    if (!destination.isBlank()) {
                        sb.append("Destination: ").append(destination).append("\n");
                    }
                    if (!remarks.isBlank()) {
                        sb.append("Remarks: ").append(remarks).append("\n");
                    }
                    sb.append("Status: ").append(status);

                    Map<String, Object> metadata = new LinkedHashMap<>();
                    metadata.put("type", "MARKETPLACE_DATA");
                    metadata.put("source", "bids");
                    metadata.put("category", "BID");
                    metadata.put("id", recordId);
                    if (!bidNumber.isBlank()) {
                        metadata.put("bidNumber", bidNumber);
                    }
                    metadata.put("commodity", commodityName);
                    if (!maskedBidderId.isBlank()) {
                        metadata.put("bidder", maskedBidderId);
                    }
                    if (!bidType.isBlank()) {
                        metadata.put("bidType", bidType);
                    }
                    metadata.put("quantity", quantity);
                    if (bidRate > 0) {
                        metadata.put("bidRate", bidRate);
                    }
                    if (counterRate > 0) {
                        metadata.put("counterRate", counterRate);
                    }
                    if (!destination.isBlank()) {
                        metadata.put("destination", destination);
                    }
                    metadata.put("status", status);

                    String docId = UUID.nameUUIDFromBytes(("bids:" + recordId).getBytes(StandardCharsets.UTF_8)).toString();
                    docs.add(new MarketplaceDocument(docId, sb.toString(), metadata));
                }

                if (dataNode.hasNonNull("last") && dataNode.path("last").asBoolean(true)) {
                    break;
                }
                currentPage++;
            }
            log.info("[MARKETPLACE SYNC] Bids documents fetched: {}", docs.size());
            return docs;
        } catch (UnsupportedOperationException ex) {
            log.warn("[MARKETPLACE SYNC] Skipping unsupported bids endpoint: {}", ex.getMessage());
            return Collections.emptyList();
        } catch (Exception ex) {
            log.error("Failed to fetch bids from Backend REST API: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetches all active buyer demands from the TradeX backend without filters.
     *
     * @return list of all BuyerDemandDto records
     */
    public List<BuyerDemandDto> fetchBuyerDemands() {
        return fetchBuyerDemands(null, null);
    }

    /**
     * Fetches structured buyer demand records from the Marketplace Backend,
     * applying commodity and location filters.
     *
     * @param commodity optional commodity filter
     * @param location optional location filter
     * @return list of matching BuyerDemandDto records
     * @throws RuntimeException if backend API communication fails
     */
    public List<BuyerDemandDto> fetchBuyerDemands(String commodity, String location) {
        if (!isConfigured()) {
            log.info("Marketplace Backend API URL is not configured. Returning empty buyer demands.");
            return Collections.emptyList();
        }
        try {
            String json;
            if (marketplaceApiClient != null) {
                json = marketplaceApiClient.getBuyerDemands(commodity, location, null);
            } else {
                String url = backendBaseUrl + "/api/ai/buyer-demands";
                RestClient.RequestHeadersSpec<?> spec = restClient.get()
                        .uri(uriBuilder -> {
                            uriBuilder.path("/api/ai/buyer-demands");
                            if (commodity != null && !commodity.isBlank()) {
                                uriBuilder.queryParam("commodity", commodity.trim());
                            }
                            if (location != null && !location.isBlank()) {
                                uriBuilder.queryParam("location", location.trim());
                            }
                            return uriBuilder.build();
                        })
                        .accept(MediaType.APPLICATION_JSON);
                if (!apiKey.isBlank()) {
                    spec.header("X-API-KEY", apiKey);
                }
                json = spec.retrieve().body(String.class);
            }

            if (json == null || json.isBlank()) {
                return Collections.emptyList();
            }

            JsonNode root = objectMapper.readTree(json);
            JsonNode recordsNode = extractRecords(root);
            if (recordsNode == null || !recordsNode.isArray()) {
                return Collections.emptyList();
            }

            List<BuyerDemandDto> demands = new ArrayList<>();
            for (JsonNode node : recordsNode) {
                BuyerDemandDto dto = new BuyerDemandDto();
                dto.setId(node.path("id").asLong());
                dto.setDemandNumber(node.hasNonNull("demandNumber") ? node.path("demandNumber").asText() : null);
                dto.setCommodityName(node.path("commodityName").asText(""));
                String buyer = node.hasNonNull("buyerName") ? node.path("buyerName").asText("") : node.path("maskedLabel").asText("");
                dto.setBuyerName(buyer);
                dto.setMaskedLabel(node.hasNonNull("maskedLabel") ? node.path("maskedLabel").asText() : null);
                dto.setQuantity(node.path("quantity").asDouble(0.0));
                if (node.hasNonNull("fulfilledQuantity")) {
                    dto.setFulfilledQuantity(node.path("fulfilledQuantity").asDouble(0.0));
                } else {
                    dto.setFulfilledQuantity(0.0);
                }
                if (node.hasNonNull("targetRate")) {
                    dto.setTargetRate(node.path("targetRate").asDouble());
                } else if (node.hasNonNull("targetPrice")) {
                    dto.setTargetRate(node.path("targetPrice").asDouble());
                }
                String unit = node.hasNonNull("unit") && !node.path("unit").asText().isBlank() ? node.path("unit").asText() : "Quintal";
                dto.setUnit(unit);
                String loc = node.hasNonNull("location") ? node.path("location").asText("") : node.path("destination").asText("");
                dto.setLocation(loc);
                dto.setDestination(node.hasNonNull("destination") ? node.path("destination").asText() : null);
                dto.setDestinationZone(node.hasNonNull("destinationZone") ? node.path("destinationZone").asText() : null);
                String reqDate = node.hasNonNull("requiredDate") ? node.path("requiredDate").asText("") : node.path("validTill").asText("");
                dto.setRequiredDate(reqDate);
                dto.setValidTill(node.hasNonNull("validTill") ? node.path("validTill").asText() : null);
                dto.setQualityGrade(node.hasNonNull("qualityGrade") ? node.path("qualityGrade").asText() : null);
                dto.setStatus(node.path("status").asText(""));
                dto.setCreatedBy(node.hasNonNull("createdBy") ? node.path("createdBy").asText() : null);
                dto.setCreatedAt(node.path("createdAt").asText(""));
                demands.add(dto);
            }

            // Safe client-side verification to guarantee filters even if backend endpoint returned broader set
            if (commodity != null && !commodity.isBlank()) {
                demands = demands.stream()
                        .filter(d -> d.getCommodityName() != null && d.getCommodityName().equalsIgnoreCase(commodity.trim()))
                        .collect(Collectors.toList());
            }
            if (location != null && !location.isBlank()) {
                demands = demands.stream()
                        .filter(d -> {
                            String dLoc = d.getLocation();
                            String dDest = d.getDestination();
                            return (dLoc != null && dLoc.equalsIgnoreCase(location.trim()))
                                    || (dDest != null && dDest.equalsIgnoreCase(location.trim()));
                        })
                        .collect(Collectors.toList());
            }

            log.info("Fetched {} buyer demand records for commodity='{}', location='{}'",
                    demands.size(), commodity, location);
            return demands;
        } catch (UnsupportedOperationException ex) {
            log.warn("[MARKETPLACE SYNC] Skipping unsupported buyer demands endpoint: {}", ex.getMessage());
            return Collections.emptyList();
        } catch (Exception ex) {
            log.error("Failed to fetch buyer demands from Backend REST API: {}", ex.getMessage());
            throw new RuntimeException("Failed to fetch buyer demands: " + ex.getMessage(), ex);
        }
    }

    /**
     * Fetches all active commodity supplies from the TradeX backend without filters.
     *
     * @return list of all SupplyDto records
     */
    public List<SupplyDto> fetchSupplies() {
        return fetchSupplies(null, null);
    }

    /**
     * Fetches structured commodity supply records from TradeX GET /api/ai/supplies,
     * applying optional commodity and location filters.
     *
     * @param commodity optional commodity filter
     * @param location optional location filter
     * @return list of matching SupplyDto records
     * @throws RuntimeException if backend API communication fails
     */
    public List<SupplyDto> fetchSupplies(String commodity, String location) {
        if (!isConfigured()) {
            log.info("Marketplace Backend API URL is not configured. Returning empty supplies.");
            return Collections.emptyList();
        }
        try {
            String json;
            if (marketplaceApiClient != null) {
                json = marketplaceApiClient.getSupplies();
            } else {
                String url = backendBaseUrl + "/api/ai/supplies";
                RestClient.RequestHeadersSpec<?> spec = restClient.get()
                        .uri(url)
                        .accept(MediaType.APPLICATION_JSON);
                if (!apiKey.isBlank()) {
                    spec.header("X-API-KEY", apiKey);
                }
                json = spec.retrieve().body(String.class);
            }

            if (json == null || json.isBlank()) {
                return Collections.emptyList();
            }

            JsonNode root = objectMapper.readTree(json);
            JsonNode recordsNode = extractRecords(root);
            if (recordsNode == null || !recordsNode.isArray()) {
                return Collections.emptyList();
            }

            List<SupplyDto> supplies = new ArrayList<>();
            for (JsonNode node : recordsNode) {
                SupplyDto dto = new SupplyDto();
                dto.setId(node.path("id").asLong());
                dto.setSupplyNumber(node.hasNonNull("supplyNumber") ? node.path("supplyNumber").asText() : "N/A");
                if (node.hasNonNull("partyId")) {
                    dto.setPartyId(node.path("partyId").asLong());
                }
                dto.setPartyName(node.hasNonNull("partyName") ? node.path("partyName").asText("") : "");
                dto.setCommodityName(node.hasNonNull("commodityName") ? node.path("commodityName").asText("") : "");
                dto.setParentCommodityName(node.hasNonNull("parentCommodityName") ? node.path("parentCommodityName").asText("") : "");
                dto.setQualityName(node.hasNonNull("qualityName") ? node.path("qualityName").asText() : null);
                dto.setQualityGrade(node.hasNonNull("qualityGrade") ? node.path("qualityGrade").asText() : null);
                dto.setTotalQuantity(node.path("totalQuantity").asDouble(0.0));
                dto.setAvailableQuantity(node.path("availableQuantity").asDouble(0.0));
                dto.setLockedQuantity(node.path("lockedQuantity").asDouble(0.0));
                String unit = node.hasNonNull("unit") && !node.path("unit").asText().isBlank() ? node.path("unit").asText() : "Quintal";
                dto.setUnit(unit);
                dto.setAskingRate(node.path("askingRate").asDouble(0.0));
                dto.setLocation(node.hasNonNull("location") ? node.path("location").asText("") : "");
                dto.setOriginZone(node.hasNonNull("originZone") ? node.path("originZone").asText() : null);
                if (node.hasNonNull("originManufacturerId")) {
                    dto.setOriginManufacturerId(node.path("originManufacturerId").asLong());
                }
                dto.setOriginManufacturerName(node.hasNonNull("originManufacturerName") ? node.path("originManufacturerName").asText() : null);
                dto.setMaskedLabel(node.hasNonNull("maskedLabel") ? node.path("maskedLabel").asText() : null);
                dto.setLabSpecs(node.hasNonNull("labSpecs") ? node.path("labSpecs").asText() : null);
                dto.setValidTill(node.hasNonNull("validTill") ? node.path("validTill").asText() : null);
                dto.setStatus(node.hasNonNull("status") ? node.path("status").asText("") : "ACTIVE");
                dto.setCreatedBy(node.hasNonNull("createdBy") ? node.path("createdBy").asText() : null);
                dto.setCreatedAt(node.hasNonNull("createdAt") ? node.path("createdAt").asText() : null);
                supplies.add(dto);
            }

            // Client-side exact filtering if requested
            if (commodity != null && !commodity.isBlank()) {
                supplies = supplies.stream()
                        .filter(s -> (s.getCommodityName() != null && s.getCommodityName().trim().equalsIgnoreCase(commodity.trim()))
                                || (s.getParentCommodityName() != null && s.getParentCommodityName().trim().equalsIgnoreCase(commodity.trim())))
                        .collect(Collectors.toList());
            }
            if (location != null && !location.isBlank()) {
                supplies = supplies.stream()
                        .filter(s -> s.getLocation() != null && s.getLocation().trim().equalsIgnoreCase(location.trim()))
                        .collect(Collectors.toList());
            }

            log.info("Fetched {} supply records for commodity='{}', location='{}'",
                    supplies.size(), commodity, location);
            return supplies;
        } catch (UnsupportedOperationException ex) {
            log.warn("[MARKETPLACE SYNC] Skipping unsupported supplies endpoint: {}", ex.getMessage());
            return Collections.emptyList();
        } catch (Exception ex) {
            log.error("Failed to fetch supplies from Backend REST API: {}", ex.getMessage());
            throw new RuntimeException("Failed to fetch supplies: " + ex.getMessage(), ex);
        }
    }

    /**
     * Fetches notifications from GET /api/ai/notifications and converts them into searchable documents.
     */
    public List<MarketplaceDocument> fetchNotificationsDocuments() {
        if (!isConfigured() || marketplaceApiClient == null) {
            return Collections.emptyList();
        }
        try {
            String json = marketplaceApiClient.getNotifications();
            if (json == null || json.isBlank()) {
                return Collections.emptyList();
            }
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                return Collections.emptyList();
            }
            List<MarketplaceDocument> docs = new ArrayList<>();
            for (JsonNode node : root) {
                long recordId = node.path("id").asLong();
                String type = node.path("type").asText("");
                String title = node.path("title").asText("");
                String message = node.path("message").asText("");
                String commodityName = node.path("commodityName").asText("");
                String status = node.path("status").asText("");
                String createdAt = node.path("createdAt").asText("");
                String date = createdAt.contains("T") ? createdAt.substring(0, createdAt.indexOf('T')) : createdAt;

                String content = String.format(
                        "Notification Information:\n" +
                        "Title: %s\n" +
                        "Type: %s\n" +
                        "Commodity: %s\n" +
                        "Status: %s\n" +
                        "Message: %s\n" +
                        "Date: %s",
                        title, type, commodityName, status, message, date
                );

                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("type", "MARKETPLACE_DATA");
                metadata.put("source", "notifications");
                metadata.put("category", "NOTIFICATION");
                metadata.put("id", recordId);
                metadata.put("notificationType", type);
                metadata.put("title", title);
                metadata.put("commodity", commodityName);
                metadata.put("status", status);
                metadata.put("date", date);

                String docId = UUID.nameUUIDFromBytes(("notifications:" + recordId).getBytes(StandardCharsets.UTF_8)).toString();
                docs.add(new MarketplaceDocument(docId, content, metadata));
            }
            log.info("[MARKETPLACE SYNC] Notifications documents fetched: {}", docs.size());
            return docs;
        } catch (UnsupportedOperationException ex) {
            log.warn("[MARKETPLACE SYNC] Skipping unsupported legacy source [notifications]: {}", ex.getMessage());
            return Collections.emptyList();
        } catch (Exception ex) {
            log.error("Failed to fetch notifications from Backend REST API: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetches WhatsApp logs from GET /api/ai/whatsapp-logs and converts them into searchable documents.
     */
    public List<MarketplaceDocument> fetchWhatsAppLogsDocuments() {
        if (!isConfigured() || marketplaceApiClient == null) {
            return Collections.emptyList();
        }
        try {
            String json = marketplaceApiClient.getWhatsAppLogs();
            if (json == null || json.isBlank()) {
                return Collections.emptyList();
            }
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                return Collections.emptyList();
            }
            List<MarketplaceDocument> docs = new ArrayList<>();
            for (JsonNode node : root) {
                long recordId = node.path("id").asLong();
                String phoneNumber = node.path("phoneNumber").asText("");
                String messageType = node.path("messageType").asText("");
                String message = node.path("message").asText("");
                String commodityName = node.path("commodityName").asText("");
                String status = node.path("status").asText("");
                String sentAt = node.path("sentAt").asText("");
                String date = sentAt.contains("T") ? sentAt.substring(0, sentAt.indexOf('T')) : sentAt;

                String content = String.format(
                        "WhatsApp Log Information:\n" +
                        "Commodity: %s\n" +
                        "Message Type: %s\n" +
                        "Recipient: %s\n" +
                        "Status: %s\n" +
                        "Message: %s\n" +
                        "Date: %s",
                        commodityName, messageType, phoneNumber, status, message, date
                );

                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("type", "MARKETPLACE_DATA");
                metadata.put("source", "whatsapp-logs");
                metadata.put("category", "WHATSAPP_LOG");
                metadata.put("id", recordId);
                metadata.put("commodity", commodityName);
                metadata.put("messageType", messageType);
                metadata.put("recipient", phoneNumber);
                metadata.put("status", status);
                metadata.put("date", date);

                String docId = UUID.nameUUIDFromBytes(("whatsapp-logs:" + recordId).getBytes(StandardCharsets.UTF_8)).toString();
                docs.add(new MarketplaceDocument(docId, content, metadata));
            }
            log.info("[MARKETPLACE SYNC] WhatsApp logs documents fetched: {}", docs.size());
            return docs;
        } catch (UnsupportedOperationException ex) {
            log.warn("[MARKETPLACE SYNC] Skipping unsupported legacy source [whatsapp-logs]: {}", ex.getMessage());
            return Collections.emptyList();
        } catch (Exception ex) {
            log.error("Failed to fetch WhatsApp logs from Backend REST API: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetches authoritative customer order records from the Marketplace Backend Orders REST API.
     *
     * @param filter order filter parameters
     * @return list of matching OrderDto records
     * @throws RuntimeException if backend communication fails or returns an error
     */
    public List<OrderDto> fetchOrders(OrderFilterDto filter) {
        if (syntheticOrdersEnabled && syntheticOrderDataProvider != null) {
            log.info("[SYNTHETIC ORDERS] Serving synthetic order test data for filter: {}", filter);
            return syntheticOrderDataProvider.filterOrders(filter);
        }
        if (!isConfigured()) {
            log.info("Marketplace Backend API URL is not configured. Returning empty orders.");
            return Collections.emptyList();
        }
        try {
            Map<String, Object> queryParams = (filter != null) ? filter.toQueryParams() : Collections.emptyMap();
            String json;
            if (marketplaceApiClient != null) {
                json = marketplaceApiClient.getOrders(queryParams);
            } else {
                String url = backendBaseUrl + "/api/ai/orders";
                RestClient.RequestHeadersSpec<?> spec = restClient.get()
                        .uri(uriBuilder -> {
                            uriBuilder.path("/api/ai/orders");
                            for (Map.Entry<String, Object> entry : queryParams.entrySet()) {
                                if (entry.getValue() != null && !entry.getValue().toString().isBlank()) {
                                    uriBuilder.queryParam(entry.getKey(), entry.getValue());
                                }
                            }
                            return uriBuilder.build();
                        })
                        .accept(MediaType.APPLICATION_JSON);
                if (!apiKey.isBlank()) {
                    spec.header("X-API-KEY", apiKey);
                }
                json = spec.retrieve().body(String.class);
            }

            if (json == null || json.isBlank()) {
                return Collections.emptyList();
            }

            List<OrderDto> orders = objectMapper.readValue(json, new TypeReference<List<OrderDto>>() {});
            return orders != null ? orders : Collections.emptyList();
        } catch (UnsupportedOperationException ex) {
            log.warn("[MARKETPLACE SYNC] Skipping unsupported legacy source [orders]: {}", ex.getMessage());
            if (syntheticOrdersEnabled && syntheticOrderDataProvider != null) {
                log.info("[SYNTHETIC ORDERS] Serving synthetic order test data on unsupported API for filter: {}", filter);
                return syntheticOrderDataProvider.filterOrders(filter);
            }
            throw ex;
        } catch (Exception ex) {
            if (syntheticOrdersEnabled && syntheticOrderDataProvider != null) {
                log.warn("[SYNTHETIC ORDERS] External API call failed ({}), falling back to synthetic orders", ex.getMessage());
                return syntheticOrderDataProvider.filterOrders(filter);
            }
            log.error("Failed to fetch orders from Marketplace Backend REST API: {}", ex.getMessage());
            throw new RuntimeException("Failed to fetch orders from Marketplace Backend: " + ex.getMessage(), ex);
        }
    }

    /**
     * Fetches a specific order by ID from the Marketplace Backend Orders REST API.
     *
     * @param id order ID
     * @return OrderDto if found, or null
     */
    public OrderDto fetchOrderById(Long id) {
        if (syntheticOrdersEnabled && syntheticOrderDataProvider != null) {
            return syntheticOrderDataProvider.getOrderById(id);
        }
        if (!isConfigured() || id == null) {
            return null;
        }
        try {
            String json;
            if (marketplaceApiClient != null) {
                json = marketplaceApiClient.getOrderById(id);
            } else {
                String url = backendBaseUrl + "/api/ai/orders/" + id;
                RestClient.RequestHeadersSpec<?> spec = restClient.get()
                        .uri(url)
                        .accept(MediaType.APPLICATION_JSON);
                if (!apiKey.isBlank()) {
                    spec.header("X-API-KEY", apiKey);
                }
                json = spec.retrieve().body(String.class);
            }

            if (json == null || json.isBlank()) {
                return null;
            }

            return objectMapper.readValue(json, OrderDto.class);
        } catch (UnsupportedOperationException ex) {
            log.warn("[MARKETPLACE SYNC] Skipping unsupported legacy source [orders]: {}", ex.getMessage());
            if (syntheticOrdersEnabled && syntheticOrderDataProvider != null) {
                return syntheticOrderDataProvider.getOrderById(id);
            }
            return null;
        } catch (Exception ex) {
            if (syntheticOrdersEnabled && syntheticOrderDataProvider != null) {
                return syntheticOrderDataProvider.getOrderById(id);
            }
            log.error("Failed to fetch order ID {} from Marketplace Backend: {}", id, ex.getMessage());
            throw new RuntimeException("Failed to fetch order " + id + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * Converts an approved OrderDto into a searchable document format for PGVector discovery.
     * Note: In accordance with privacy and authorization policies, buyerPhone is NOT indexed.
     */
    public MarketplaceDocument convertOrderToSearchableDocument(OrderDto orderDto) {
        if (orderDto == null) {
            throw new IllegalArgumentException("OrderDto cannot be null");
        }

        String createdAt = orderDto.getCreatedAt() != null ? orderDto.getCreatedAt() : "";
        String date = createdAt.contains("T") ? createdAt.substring(0, createdAt.indexOf('T')) : createdAt;

        String content = String.format(
                "Order Information:\n" +
                "Order ID: %s\n" +
                "Buyer: %s\n" +
                "Commodity: %s\n" +
                "Quantity: %s %s\n" +
                "Price Per Unit: ₹%s\n" +
                "Total Amount: ₹%s\n" +
                "Market Location: %s\n" +
                "Delivery Location: %s\n" +
                "Status: %s\n" +
                "Date: %s",
                orderDto.getId(),
                orderDto.getBuyerName(),
                orderDto.getCommodityName(),
                orderDto.getQuantity(),
                orderDto.getUnit(),
                orderDto.getPricePerUnit(),
                orderDto.getTotalAmount(),
                orderDto.getMarketLocation(),
                orderDto.getDeliveryLocation(),
                orderDto.getStatus(),
                date
        );

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("type", "MARKETPLACE_DATA");
        metadata.put("source", "orders");
        metadata.put("category", "ORDER");
        metadata.put("id", orderDto.getId());
        metadata.put("orderId", orderDto.getId());
        metadata.put("buyer", orderDto.getBuyerName());
        metadata.put("commodity", orderDto.getCommodityName());
        metadata.put("quantity", orderDto.getQuantity());
        metadata.put("unit", orderDto.getUnit());
        metadata.put("totalAmount", orderDto.getTotalAmount());
        metadata.put("marketLocation", orderDto.getMarketLocation());
        metadata.put("deliveryLocation", orderDto.getDeliveryLocation());
        metadata.put("status", orderDto.getStatus());
        metadata.put("date", date);

        String docId = UUID.nameUUIDFromBytes(("orders:" + orderDto.getId()).getBytes(StandardCharsets.UTF_8)).toString();
        return new MarketplaceDocument(docId, content, metadata);
    }

    /**
     * Fetches orders and converts them into searchable documents.
     */
    public List<MarketplaceDocument> fetchOrdersDocuments() {
        if (syntheticOrdersEnabled && syntheticOrderDataProvider != null) {
            List<OrderDto> orders = syntheticOrderDataProvider.getAllSyntheticOrders();
            List<MarketplaceDocument> docs = new ArrayList<>();
            for (OrderDto o : orders) {
                if (o != null) {
                    docs.add(convertOrderToSearchableDocument(o));
                }
            }
            log.info("[MARKETPLACE SYNC] Synthetic orders documents fetched: {}", docs.size());
            return docs;
        }
        if (!isConfigured() || marketplaceApiClient == null) {
            return Collections.emptyList();
        }
        try {
            String json = marketplaceApiClient.getOrders();
            if (json == null || json.isBlank()) {
                return Collections.emptyList();
            }
            List<OrderDto> orders = objectMapper.readValue(json, new TypeReference<List<OrderDto>>() {});
            if (orders == null || orders.isEmpty()) {
                return Collections.emptyList();
            }
            List<MarketplaceDocument> docs = new ArrayList<>();
            for (OrderDto o : orders) {
                if (o != null) {
                    docs.add(convertOrderToSearchableDocument(o));
                }
            }
            log.info("[MARKETPLACE SYNC] Orders documents fetched: {}", docs.size());
            return docs;
        } catch (UnsupportedOperationException ex) {
            log.warn("[MARKETPLACE SYNC] Skipping unsupported legacy source [orders]: {}", ex.getMessage());
            return Collections.emptyList();
        } catch (Exception ex) {
            log.error("Failed to fetch orders documents from Backend REST API: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Aggregates documents from all supported Tradex and legacy marketplace REST API sources.
     * Supported Tradex endpoints: commodities, supplies, demands, bids.
     * Legacy demo endpoints: market-rates, rate-history, notifications, whatsapp-logs
     * (gracefully skipped with warning if unsupported by backend).
     */
    public List<MarketplaceDocument> fetchAllMarketplaceDocuments() {
        if (!isConfigured()) {
            return Collections.emptyList();
        }
        List<MarketplaceDocument> allDocs = new ArrayList<>();

        // 1. Supported Tradex sources
        try {
            allDocs.addAll(fetchCommoditiesDocuments());
        } catch (Exception ex) {
            log.warn("[MARKETPLACE SYNC] Error fetching commodities documents: {}", ex.getMessage());
        }

        try {
            allDocs.addAll(fetchSuppliesDocuments());
        } catch (Exception ex) {
            log.warn("[MARKETPLACE SYNC] Error fetching supplies documents: {}", ex.getMessage());
        }

        try {
            List<MarketplaceDocument> demandDocs = fetchDemandsDocuments();
            if (demandDocs.isEmpty()) {
                demandDocs = fetchBuyerDemandsDocuments();
            }
            allDocs.addAll(demandDocs);
        } catch (Exception ex) {
            log.warn("[MARKETPLACE SYNC] Error fetching demands documents: {}", ex.getMessage());
        }

        try {
            allDocs.addAll(fetchBidsDocuments());
        } catch (Exception ex) {
            log.warn("[MARKETPLACE SYNC] Error fetching bids documents: {}", ex.getMessage());
        }

        // 2. Legacy demo sources (gracefully skipped if unsupported)
        try {
            allDocs.addAll(fetchMarketRatesDocuments());
        } catch (Exception ex) {
            log.warn("[MARKETPLACE SYNC] Error fetching market rates documents: {}", ex.getMessage());
        }

        try {
            allDocs.addAll(fetchRateHistoryDocuments());
        } catch (Exception ex) {
            log.warn("[MARKETPLACE SYNC] Error fetching rate history documents: {}", ex.getMessage());
        }

        try {
            allDocs.addAll(fetchNotificationsDocuments());
        } catch (Exception ex) {
            log.warn("[MARKETPLACE SYNC] Error fetching notifications documents: {}", ex.getMessage());
        }

        try {
            allDocs.addAll(fetchWhatsAppLogsDocuments());
        } catch (Exception ex) {
            log.warn("[MARKETPLACE SYNC] Error fetching WhatsApp logs documents: {}", ex.getMessage());
        }

        log.info("[MARKETPLACE SYNC] Aggregated {} documents across all marketplace sources.", allDocs.size());
        return allDocs;
    }

    /**
     * Aggregates documents from all REST API sources including orders.
     */
    public List<MarketplaceDocument> fetchAllMarketplaceAndOrderDocuments() {
        List<MarketplaceDocument> allDocs = new ArrayList<>(fetchAllMarketplaceDocuments());
        allDocs.addAll(fetchOrdersDocuments());
        return allDocs;
    }

    /**
     * Fetches all registered parties from the TradeX backend using GET /api/v1/parties.
     * Uses totalElements as the authoritative total count and retrieves subsequent pages if needed.
     */
    public PartiesResultDto fetchParties() {
        return fetchParties(0, 50);
    }

    /**
     * Fetches registered parties for the specified page and size from GET /api/v1/parties.
     */
    public PartiesResultDto fetchParties(int page, int size) {
        if (marketplaceApiClient == null) {
            log.warn("[PARTIES] MarketplaceApiClient is null, returning empty result");
            return new PartiesResultDto(Collections.emptyList(), 0, 0, page, size);
        }

        try {
            String json = marketplaceApiClient.getParties(page, size);
            if (json == null || json.isBlank()) {
                log.warn("[PARTIES] Received empty response from TradeX /api/v1/parties");
                return new PartiesResultDto(Collections.emptyList(), 0, 0, page, size);
            }
            JsonNode root = objectMapper.readTree(json);
            if (root == null || root.isNull()) {
                log.warn("[PARTIES] Received null JSON tree from TradeX /api/v1/parties");
                return new PartiesResultDto(Collections.emptyList(), 0, 0, page, size);
            }

            JsonNode dataNode = root.has("data") ? root.get("data") : root;
            JsonNode contentNode = dataNode.has("content") ? dataNode.get("content") : (dataNode.isArray() ? dataNode : null);

            long totalElements = 0;
            int totalPages = 1;
            int pageNumber = page;
            int pageSize = size;

            if (dataNode.has("totalElements")) {
                totalElements = dataNode.get("totalElements").asLong();
            }
            if (dataNode.has("totalPages")) {
                totalPages = dataNode.get("totalPages").asInt();
            }
            if (dataNode.has("number")) {
                pageNumber = dataNode.get("number").asInt();
            }
            if (dataNode.has("size")) {
                pageSize = dataNode.get("size").asInt();
            }

            List<PartyDto> allParties = new ArrayList<>();
            if (contentNode != null && contentNode.isArray()) {
                for (JsonNode partyNode : contentNode) {
                    PartyDto dto = parsePartyNode(partyNode);
                    if (dto != null) {
                        allParties.add(dto);
                    }
                }
            }

            if (totalElements == 0 && !allParties.isEmpty()) {
                totalElements = allParties.size();
            }

            // If there are more pages and we called fetchParties() with the intention to get all parties,
            // we should retrieve remaining pages.
            if (totalPages > 1 && (page + 1) < totalPages && allParties.size() < totalElements) {
                log.info("[PARTIES] Fetching remaining pages (totalPages={})", totalPages);
                for (int p = 1; p < totalPages; p++) {
                    try {
                        String nextJson = marketplaceApiClient.getParties(p, size);
                        if (nextJson != null && !nextJson.isBlank()) {
                            JsonNode nextPage = objectMapper.readTree(nextJson);
                            if (nextPage != null) {
                                JsonNode nextData = nextPage.has("data") ? nextPage.get("data") : nextPage;
                                JsonNode nextContent = nextData.has("content") ? nextData.get("content") : (nextData.isArray() ? nextData : null);
                                if (nextContent != null && nextContent.isArray()) {
                                    for (JsonNode partyNode : nextContent) {
                                        PartyDto dto = parsePartyNode(partyNode);
                                        if (dto != null) {
                                            allParties.add(dto);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception ex) {
                        log.warn("[PARTIES] Error fetching page {}: {}", p, ex.getMessage());
                    }
                }
            }

            log.info("[PARTIES] Successfully fetched {} party records (totalElements={})", allParties.size(), totalElements);
            return new PartiesResultDto(allParties, totalElements, totalPages, pageNumber, pageSize);

        } catch (Exception e) {
            log.error("[PARTIES] Error retrieving parties from TradeX API: {}", e.getMessage(), e);
            return new PartiesResultDto(Collections.emptyList(), 0, 0, page, size);
        }
    }

    /**
     * Parses a single party JsonNode into a PartyDto.
     */
    public PartyDto parsePartyNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        PartyDto dto = new PartyDto();
        if (node.hasNonNull("id")) dto.setId(node.get("id").asLong());
        if (node.hasNonNull("partyCode")) dto.setPartyCode(node.get("partyCode").asText());
        if (node.hasNonNull("companyName")) dto.setCompanyName(node.get("companyName").asText());
        if (node.hasNonNull("gstin")) dto.setGstin(node.get("gstin").asText());
        if (node.hasNonNull("pan")) dto.setPan(node.get("pan").asText());
        if (node.hasNonNull("contactPerson")) dto.setContactPerson(node.get("contactPerson").asText());
        if (node.hasNonNull("email")) dto.setEmail(node.get("email").asText());
        if (node.hasNonNull("phone")) dto.setPhone(node.get("phone").asText());
        if (node.hasNonNull("alternatePhone")) dto.setAlternatePhone(node.get("alternatePhone").asText());
        if (node.hasNonNull("address")) dto.setAddress(node.get("address").asText());
        if (node.hasNonNull("city")) dto.setCity(node.get("city").asText());
        if (node.hasNonNull("taluka")) dto.setTaluka(node.get("taluka").asText());
        if (node.hasNonNull("plantLocation")) dto.setPlantLocation(node.get("plantLocation").asText());
        if (node.hasNonNull("state")) dto.setState(node.get("state").asText());
        if (node.hasNonNull("country")) dto.setCountry(node.get("country").asText());
        if (node.hasNonNull("postalCode")) dto.setPostalCode(node.get("postalCode").asText());
        else if (node.hasNonNull("pincode")) dto.setPostalCode(node.get("pincode").asText());
        if (node.hasNonNull("description")) dto.setDescription(node.get("description").asText());
        if (node.hasNonNull("website")) dto.setWebsite(node.get("website").asText());
        if (node.hasNonNull("active")) dto.setActive(node.get("active").asBoolean());
        if (node.hasNonNull("isDeleted")) dto.setIsDeleted(node.get("isDeleted").asBoolean());
        if (node.hasNonNull("status")) dto.setStatus(node.get("status").asText());
        if (node.hasNonNull("registrationType")) dto.setRegistrationType(node.get("registrationType").asText());
        if (node.hasNonNull("createdAt")) dto.setCreatedAt(node.get("createdAt").asText());
        if (node.hasNonNull("updatedAt")) dto.setUpdatedAt(node.get("updatedAt").asText());

        if (node.hasNonNull("roles") && node.get("roles").isArray()) {
            List<String> roles = new ArrayList<>();
            for (JsonNode r : node.get("roles")) {
                if (r.isTextual()) {
                    roles.add(r.asText());
                } else if (r.isObject()) {
                    if (r.hasNonNull("name")) {
                        roles.add(r.get("name").asText());
                    } else if (r.hasNonNull("role")) {
                        roles.add(r.get("role").asText());
                    }
                }
            }
            dto.setRoles(roles);
        }
        return dto;
    }

    /**
     * Fetches all bids from the TradeX backend via GET /api/ai/bids.
     */
    public List<BidDto> fetchBids() {
        if (!isConfigured() || marketplaceApiClient == null) {
            log.warn("[BIDS] MarketplaceApiClient is null or not configured, returning empty bids list");
            return Collections.emptyList();
        }
        try {
            String json = marketplaceApiClient.getBids(0, 50, false, null);
            if (json == null || json.isBlank()) {
                log.warn("[BIDS] Received empty response from TradeX /api/ai/bids");
                return Collections.emptyList();
            }
            JsonNode root = objectMapper.readTree(json);
            if (root == null || root.isNull()) {
                return Collections.emptyList();
            }
            JsonNode dataNode = root.has("data") ? root.get("data") : root;
            JsonNode contentNode = dataNode.has("content") ? dataNode.get("content") : (dataNode.isArray() ? dataNode : null);

            if (contentNode == null || !contentNode.isArray()) {
                return Collections.emptyList();
            }

            List<BidDto> bids = new ArrayList<>();
            for (JsonNode node : contentNode) {
                BidDto dto = parseBidNode(node);
                if (dto != null) {
                    bids.add(dto);
                }
            }

            // If there are multiple pages, fetch remaining
            if (dataNode.has("totalPages")) {
                int totalPages = dataNode.get("totalPages").asInt();
                for (int p = 1; p < totalPages && p < 10; p++) {
                    try {
                        String nextJson = marketplaceApiClient.getBids(p, 50, false, null);
                        if (nextJson != null && !nextJson.isBlank()) {
                            JsonNode nextRoot = objectMapper.readTree(nextJson);
                            JsonNode nextData = nextRoot.has("data") ? nextRoot.get("data") : nextRoot;
                            JsonNode nextContent = nextData.has("content") ? nextData.get("content") : (nextData.isArray() ? nextData : null);
                            if (nextContent != null && nextContent.isArray()) {
                                for (JsonNode node : nextContent) {
                                    BidDto dto = parseBidNode(node);
                                    if (dto != null) {
                                        bids.add(dto);
                                    }
                                }
                            }
                        }
                    } catch (Exception ex) {
                        log.warn("[BIDS] Error fetching page {}: {}", p, ex.getMessage());
                    }
                }
            }

            log.info("[BIDS] Successfully fetched {} bid records from TradeX backend", bids.size());
            return bids;
        } catch (Exception ex) {
            log.error("[BIDS] Failed to fetch bids from TradeX API: {}", ex.getMessage(), ex);
            return Collections.emptyList();
        }
    }

    /**
     * Fetches bids created on a specific date from TradeX backend via GET /api/v1/bids/history/by-date?date=YYYY-MM-DD.
     */
    public List<BidDto> fetchBidsByDate(String date) {
        if (!isConfigured() || marketplaceApiClient == null) {
            log.warn("[BIDS] MarketplaceApiClient is null or not configured, returning empty bids list");
            return Collections.emptyList();
        }
        try {
            String json = marketplaceApiClient.getBidsByDate(date);
            if (json == null || json.isBlank()) {
                log.warn("[BIDS] Received empty response from TradeX /api/v1/bids/history/by-date?date={}", date);
                return Collections.emptyList();
            }
            JsonNode root = objectMapper.readTree(json);
            if (root == null || root.isNull()) {
                return Collections.emptyList();
            }
            JsonNode dataNode = root.has("data") ? root.get("data") : root;
            JsonNode contentNode = dataNode.has("content") ? dataNode.get("content") : (dataNode.isArray() ? dataNode : null);

            if (contentNode == null || !contentNode.isArray()) {
                return Collections.emptyList();
            }

            List<BidDto> bids = new ArrayList<>();
            for (JsonNode node : contentNode) {
                BidDto dto = parseBidNode(node);
                if (dto != null) {
                    bids.add(dto);
                }
            }
            log.info("[BIDS] Successfully fetched {} bid records from TradeX by-date endpoint for date={}", bids.size(), date);
            return bids;
        } catch (Exception ex) {
            log.error("[BIDS] Failed to fetch bids by date from TradeX API: {}", ex.getMessage(), ex);
            return Collections.emptyList();
        }
    }

    /**
     * Parses a single Bid JsonNode into BidDto.
     */
    public BidDto parseBidNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        BidDto dto = new BidDto();
        if (node.hasNonNull("id")) dto.setId(node.get("id").asLong());
        if (node.hasNonNull("bidNumber")) dto.setBidNumber(node.get("bidNumber").asText());
        if (node.hasNonNull("commodityName")) dto.setCommodityName(node.get("commodityName").asText());
        if (node.hasNonNull("maskedBidderId")) dto.setMaskedBidderId(node.get("maskedBidderId").asText());
        if (node.hasNonNull("marketRate")) dto.setMarketRate(new BigDecimal(node.get("marketRate").asText()));
        if (node.hasNonNull("bidRate")) dto.setBidRate(new BigDecimal(node.get("bidRate").asText()));
        if (node.hasNonNull("counterRate")) dto.setCounterRate(new BigDecimal(node.get("counterRate").asText()));
        if (node.hasNonNull("quantity")) dto.setQuantity(new BigDecimal(node.get("quantity").asText()));
        if (node.hasNonNull("destination")) dto.setDestination(node.get("destination").asText());
        if (node.hasNonNull("bidType")) dto.setBidType(node.get("bidType").asText());
        if (node.hasNonNull("remarks")) dto.setRemarks(node.get("remarks").asText());
        if (node.hasNonNull("counterRemarks")) dto.setCounterRemarks(node.get("counterRemarks").asText());
        if (node.hasNonNull("status")) dto.setStatus(node.get("status").asText());
        if (node.hasNonNull("createdAt")) dto.setCreatedAt(node.get("createdAt").asText());
        if (node.hasNonNull("updatedAt")) dto.setUpdatedAt(node.get("updatedAt").asText());
        return dto;
    }

    /**
     * Fetches active negotiations from the TradeX backend via GET /api/ai/negotiations.
     */
    public List<NegotiationDto> fetchNegotiations() {
        if (!isConfigured() || marketplaceApiClient == null) {
            log.warn("[NEGOTIATIONS] MarketplaceApiClient is null or not configured, returning empty list");
            return Collections.emptyList();
        }
        try {
            String json = marketplaceApiClient.getNegotiations();
            if (json == null || json.isBlank()) {
                log.warn("[NEGOTIATIONS] Received empty response from TradeX /api/ai/negotiations");
                return Collections.emptyList();
            }
            JsonNode root = objectMapper.readTree(json);
            if (root == null || root.isNull()) {
                return Collections.emptyList();
            }
            JsonNode dataNode = root.has("data") ? root.get("data") : root;
            if (dataNode == null || !dataNode.isArray()) {
                return Collections.emptyList();
            }

            List<NegotiationDto> negotiations = new ArrayList<>();
            for (JsonNode node : dataNode) {
                NegotiationDto dto = parseNegotiationNode(node);
                if (dto != null) {
                    negotiations.add(dto);
                }
            }
            log.info("[NEGOTIATIONS] Successfully fetched {} negotiation records from TradeX backend", negotiations.size());
            return negotiations;
        } catch (Exception ex) {
            log.error("[NEGOTIATIONS] Failed to fetch negotiations from TradeX API: {}", ex.getMessage(), ex);
            return Collections.emptyList();
        }
    }

    /**
     * Parses a single Negotiation JsonNode into NegotiationDto.
     */
    public NegotiationDto parseNegotiationNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        NegotiationDto dto = new NegotiationDto();
        if (node.hasNonNull("id")) dto.setId(node.get("id").asLong());
        if (node.hasNonNull("negotiationCode")) dto.setNegotiationCode(node.get("negotiationCode").asText());
        if (node.hasNonNull("commodityName")) dto.setCommodityName(node.get("commodityName").asText());
        if (node.hasNonNull("quantity")) dto.setQuantity(new BigDecimal(node.get("quantity").asText()));
        if (node.hasNonNull("destination")) dto.setDestination(node.get("destination").asText());
        if (node.hasNonNull("marketRate")) dto.setMarketRate(new BigDecimal(node.get("marketRate").asText()));
        if (node.hasNonNull("buyerOffer")) dto.setBuyerOffer(new BigDecimal(node.get("buyerOffer").asText()));
        if (node.hasNonNull("sellerOffer")) dto.setSellerOffer(new BigDecimal(node.get("sellerOffer").asText()));
        if (node.hasNonNull("currentOffer")) dto.setCurrentOffer(new BigDecimal(node.get("currentOffer").asText()));
        if (node.hasNonNull("lastOfferBy")) dto.setLastOfferBy(node.get("lastOfferBy").asText());
        if (node.hasNonNull("status")) dto.setStatus(node.get("status").asText());
        if (node.hasNonNull("createdAt")) dto.setCreatedAt(node.get("createdAt").asText());
        if (node.hasNonNull("updatedAt")) dto.setUpdatedAt(node.get("updatedAt").asText());

        if (node.hasNonNull("rounds") && node.get("rounds").isArray()) {
            List<NegotiationRoundDto> rounds = new ArrayList<>();
            for (JsonNode rNode : node.get("rounds")) {
                NegotiationRoundDto rDto = new NegotiationRoundDto();
                if (rNode.hasNonNull("roundNumber")) rDto.setRoundNumber(rNode.get("roundNumber").asInt());
                if (rNode.hasNonNull("offeredBy")) rDto.setOfferedBy(rNode.get("offeredBy").asText());
                if (rNode.hasNonNull("offeredByPartyId")) rDto.setOfferedByPartyId(rNode.get("offeredByPartyId").asText());
                if (rNode.hasNonNull("rate")) rDto.setRate(new BigDecimal(rNode.get("rate").asText()));
                if (rNode.hasNonNull("remarks")) rDto.setRemarks(rNode.get("remarks").asText());
                if (rNode.hasNonNull("createdAt")) rDto.setCreatedAt(rNode.get("createdAt").asText());
                rounds.add(rDto);
            }
            dto.setRounds(rounds);
        }
        return dto;
    }
}
