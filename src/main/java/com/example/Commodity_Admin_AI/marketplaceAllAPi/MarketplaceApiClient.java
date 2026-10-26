package com.example.Commodity_Admin_AI.marketplaceAllAPi;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * REST API client for interacting with the Tradex backend /api/ai endpoints.
 *
 * Exposes real Tradex marketplace resources (commodities, supplies, demands, bids,
 * negotiations, deals, contracts) while preserving legacy method signatures for existing
 * callers, explicitly signalling when legacy demo endpoints have no Tradex equivalent.
 */
@Service
public class MarketplaceApiClient {

    private final RestClient restClient;
    private final String baseUrl;

    @Autowired
    public MarketplaceApiClient(
            @Value("${marketplace.backend.base-url:http://localhost:8080}") String baseUrl) {
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim() : "http://localhost:8080";

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));

        this.restClient = RestClient.builder()
                .baseUrl(this.baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public MarketplaceApiClient(RestClient restClient, String baseUrl) {
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim() : "http://localhost:8080";
        this.restClient = restClient;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Tradex Endpoint 1: GET /api/ai/commodities
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Retrieves commodities from GET /api/ai/commodities using default pagination (page=0, size=20).
     */
    public String getCommodities() {
        return getCommodities(0, 20);
    }

    /**
     * Retrieves paginated commodities from GET /api/ai/commodities.
     *
     * @param page page number (0-indexed)
     * @param size page size
     * @return JSON response wrapped in Tradex ApiResponse<Page<CommodityDTO>>
     */
    public String getCommodities(int page, int size) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/ai/commodities")
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build())
                .retrieve()
                .body(String.class);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Tradex Endpoint 2: GET /api/ai/supplies
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Retrieves available supplies from GET /api/ai/supplies.
     */
    public String getSupplies() {
        return getSupplies(null, null, null);
    }

    /**
     * Retrieves available supplies filtered by commodity ID.
     *
     * @param commodityId optional commodity ID filter
     */
    public String getSupplies(Long commodityId) {
        return getSupplies(commodityId, null, null);
    }

    /**
     * Retrieves supplies with optional commodity filter, party ID, and admin context.
     *
     * @param commodityId optional commodity ID filter
     * @param partyId optional party ID for X-Party-Id header
     * @param adminId optional admin identifier for X-Admin-Id header (bypasses double-blind masking)
     * @return JSON response wrapped in Tradex ApiResponse<List<SupplyDTO>>
     */
    public String getSupplies(Long commodityId, Long partyId, String adminId) {
        return restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/api/ai/supplies");
                    if (commodityId != null) {
                        uriBuilder.queryParam("commodityId", commodityId);
                    }
                    return uriBuilder.build();
                })
                .headers(headers -> {
                    if (partyId != null) {
                        headers.set("X-Party-Id", String.valueOf(partyId));
                    }
                    if (adminId != null && !adminId.isBlank()) {
                        headers.set("X-Admin-Id", adminId.trim());
                    }
                })
                .retrieve()
                .body(String.class);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Tradex Endpoint 3: GET /api/ai/demands
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Retrieves active demands from GET /api/ai/demands.
     */
    public String getDemands() {
        return getDemands(null, null, null);
    }

    /**
     * Retrieves active demands filtered by commodity ID.
     *
     * @param commodityId optional commodity ID filter
     */
    public String getDemands(Long commodityId) {
        return getDemands(commodityId, null, null);
    }

    /**
     * Retrieves active demands with optional commodity filter, party ID, and admin context.
     *
     * @param commodityId optional commodity ID filter
     * @param partyId optional party ID for X-Party-Id header
     * @param adminId optional admin identifier for X-Admin-Id header (bypasses double-blind masking)
     * @return JSON response wrapped in Tradex ApiResponse<List<DemandDTO>>
     */
    public String getDemands(Long commodityId, Long partyId, String adminId) {
        return restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/api/ai/demands");
                    if (commodityId != null) {
                        uriBuilder.queryParam("commodityId", commodityId);
                    }
                    return uriBuilder.build();
                })
                .headers(headers -> {
                    if (partyId != null) {
                        headers.set("X-Party-Id", String.valueOf(partyId));
                    }
                    if (adminId != null && !adminId.isBlank()) {
                        headers.set("X-Admin-Id", adminId.trim());
                    }
                })
                .retrieve()
                .body(String.class);
    }

    /**
     * Backward-compatible mapping: routes legacy getBuyerDemands() to Tradex GET /api/ai/demands.
     */
    public String getBuyerDemands() {
        return getDemands(null, null, null);
    }

    /**
     * Backward-compatible mapping for commodity ID filter.
     */
    public String getBuyerDemands(Long commodityId) {
        return getDemands(commodityId, null, null);
    }

    /**
     * Preserved legacy method signature.
     * Note: Tradex GET /api/ai/demands only accepts a numeric commodityId query param,
     * not free-text commodity/location or openOnly booleans. Calls GET /api/ai/demands.
     */
    public String getBuyerDemands(String commodity, String location, Boolean openOnly) {
        return getDemands(null, null, null);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Tradex Endpoint 4: GET /api/ai/bids
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Retrieves bids from GET /api/ai/bids with default pagination (page=0, size=20, myBidsOnly=false).
     */
    public String getBids() {
        return getBids(0, 20, false, null);
    }

    /**
     * Retrieves paginated bids from GET /api/ai/bids.
     *
     * @param page page number (0-indexed)
     * @param size page size
     * @param myBidsOnly if true, scopes exclusively to the caller's bids
     * @param partyId optional party ID for X-Party-Id header
     * @return JSON response wrapped in Tradex ApiResponse<Page<BidDTO>>
     */
    public String getBids(int page, int size, boolean myBidsOnly, Long partyId) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/ai/bids")
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .queryParam("myBidsOnly", myBidsOnly)
                        .build())
                .headers(headers -> {
                    if (partyId != null) {
                        headers.set("X-Party-Id", String.valueOf(partyId));
                    }
                })
                .retrieve()
                .body(String.class);
    }

    /**
     * Retrieves bids history for a specific date from GET /api/v1/bids/history/by-date?date=YYYY-MM-DD.
     *
     * @param date date string in YYYY-MM-DD format
     * @return JSON response wrapped in Tradex ApiResponse<List<BidDTO>>
     */
    public String getBidsByDate(String date) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/bids/history/by-date")
                        .queryParam("date", date)
                        .build())
                .retrieve()
                .body(String.class);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Tradex Endpoint 5: GET /api/ai/negotiations
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Retrieves active negotiations from GET /api/ai/negotiations.
     */
    public String getNegotiations() {
        return getNegotiations(null, null);
    }

    /**
     * Retrieves active negotiations for a specific party.
     *
     * @param partyId caller's party ID for X-Party-Id header
     */
    public String getNegotiations(Long partyId) {
        return getNegotiations(partyId, null);
    }

    /**
     * Retrieves active negotiations with party ID and Bearer authorization token.
     * Tradex requires PreAuthorize authority CAN_BUY or CAN_SELL.
     *
     * @param partyId caller's party ID for X-Party-Id header
     * @param authToken optional JWT or Bearer authorization token
     * @return JSON response wrapped in Tradex ApiResponse<List<NegotiationDTO>>
     */
    public String getNegotiations(Long partyId, String authToken) {
        return restClient.get()
                .uri("/api/ai/negotiations")
                .headers(headers -> {
                    if (partyId != null) {
                        headers.set("X-Party-Id", String.valueOf(partyId));
                    }
                    if (authToken != null && !authToken.isBlank()) {
                        headers.set("Authorization", authToken.startsWith("Bearer ") ? authToken : "Bearer " + authToken);
                    }
                })
                .retrieve()
                .body(String.class);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Tradex Endpoint 6: GET /api/ai/deals
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Retrieves deals from GET /api/ai/deals with default role BUYER.
     */
    public String getDeals() {
        return getDeals("BUYER", null, null);
    }

    /**
     * Retrieves deals from GET /api/ai/deals for a given role (BUYER or SELLER).
     *
     * @param role party role ("BUYER" or "SELLER")
     */
    public String getDeals(String role) {
        return getDeals(role, null, null);
    }

    /**
     * Retrieves deals with role, party ID, and authorization token.
     * Tradex requires PreAuthorize authority CAN_BUY or CAN_SELL.
     *
     * @param role party role ("BUYER" or "SELLER")
     * @param partyId caller's party ID for X-Party-Id header
     * @param authToken optional JWT or Bearer authorization token
     * @return JSON response wrapped in Tradex ApiResponse<List<DealDTO>>
     */
    public String getDeals(String role, Long partyId, String authToken) {
        String normalizedRole = (role != null && !role.isBlank()) ? role.trim().toUpperCase() : "BUYER";
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/ai/deals")
                        .queryParam("role", normalizedRole)
                        .build())
                .headers(headers -> {
                    if (partyId != null) {
                        headers.set("X-Party-Id", String.valueOf(partyId));
                    }
                    if (authToken != null && !authToken.isBlank()) {
                        headers.set("Authorization", authToken.startsWith("Bearer ") ? authToken : "Bearer " + authToken);
                    }
                })
                .retrieve()
                .body(String.class);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Tradex Endpoint 7: GET /api/ai/contracts/{id}
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Retrieves a contract by ID from GET /api/ai/contracts/{id}.
     *
     * @param id contract ID
     */
    public String getContractById(Long id) {
        return getContractById(id, null, null);
    }

    /**
     * Retrieves a contract by ID with party ID and authorization token.
     * Non-admin callers can only view contracts where they are buyer or seller.
     *
     * @param id contract ID
     * @param partyId caller's party ID for X-Party-Id header
     * @param authToken optional JWT or Bearer authorization token
     * @return JSON response wrapped in Tradex ApiResponse<ContractDTO>
     */
    public String getContractById(Long id, Long partyId, String authToken) {
        return restClient.get()
                .uri("/api/ai/contracts/{id}", id)
                .headers(headers -> {
                    if (partyId != null) {
                        headers.set("X-Party-Id", String.valueOf(partyId));
                    }
                    if (authToken != null && !authToken.isBlank()) {
                        headers.set("Authorization", authToken.startsWith("Bearer ") ? authToken : "Bearer " + authToken);
                    }
                })
                .retrieve()
                .body(String.class);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Tradex Endpoint: GET /api/v1/parties
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Retrieves paginated parties from GET /api/v1/parties using default pagination.
     *
     * @return JSON response wrapped in Tradex ApiResponse<Page<PartyDTO>>
     */
    public String getParties() {
        return getParties(0, 50);
    }

    /**
     * Retrieves paginated parties from GET /api/v1/parties.
     *
     * @param page page number (0-indexed)
     * @param size page size
     * @return JSON response wrapped in Tradex ApiResponse<Page<PartyDTO>>
     */
    public String getParties(int page, int size) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/parties")
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build())
                .retrieve()
                .body(String.class);
    }

    /**
     * Retrieves a party by ID from GET /api/v1/parties/{id}.
     *
     * @param id party ID
     * @return JSON response wrapped in Tradex ApiResponse<PartyDTO>
     */
    public String getPartyById(Long id) {
        return restClient.get()
                .uri("/api/v1/parties/{id}", id)
                .retrieve()
                .body(String.class);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Preserved Legacy Demo Signatures (No Tradex Equivalent)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Legacy demo method: GET /api/ai/market-rates.
     * Tradex AiMarketplaceController exposes supplies, demands, bids, deals, and contracts,
     * but has NO direct equivalent market-rate ticker endpoint.
     */
    public String getMarketRates() {
        throw new UnsupportedOperationException(
                "No direct equivalent endpoint exists in Tradex backend. " +
                "Tradex AiMarketplaceController exposes supplies, demands, bids, deals, and contracts, but no /api/ai/market-rates endpoint.");
    }

    /**
     * Legacy demo method: GET /api/ai/rate-history.
     * Tradex AiMarketplaceController has NO historical rate records or time-series endpoint.
     */
    public String getRateHistory() {
        return getRateHistory(null, null);
    }

    /**
     * Legacy demo method: GET /api/ai/rate-history with commodity and location filters.
     * Tradex AiMarketplaceController has NO historical rate records or time-series endpoint.
     */
    public String getRateHistory(String commodity, String location) {
        throw new UnsupportedOperationException(
                "No direct equivalent endpoint exists in Tradex backend. " +
                "Tradex AiMarketplaceController does not expose historical rate records or time-series data.");
    }

    /**
     * Legacy demo method: GET /api/ai/notifications.
     * Tradex AiMarketplaceController has NO notifications endpoint.
     */
    public String getNotifications() {
        throw new UnsupportedOperationException(
                "Unsupported endpoint in Tradex backend. " +
                "Tradex AiMarketplaceController does not expose /api/ai/notifications.");
    }

    /**
     * Legacy demo method: GET /api/ai/whatsapp-logs.
     * Tradex AiMarketplaceController has NO WhatsApp logs endpoint.
     */
    public String getWhatsAppLogs() {
        throw new UnsupportedOperationException(
                "Unsupported endpoint in Tradex backend. " +
                "Tradex AiMarketplaceController does not expose /api/ai/whatsapp-logs.");
    }

    /**
     * Legacy demo method: GET /api/ai/orders.
     * Tradex backend does not maintain generic customer orders; it models trading activity
     * as Deals (GET /api/ai/deals) and Contracts (GET /api/ai/contracts/{id}).
     */
    public String getOrders() {
        return getOrders(null);
    }

    /**
     * Legacy demo method: GET /api/ai/orders with query parameters.
     * Tradex backend does not maintain generic customer orders; it models trading activity
     * as Deals (GET /api/ai/deals) and Contracts (GET /api/ai/contracts/{id}).
     */
    public String getOrders(Map<String, Object> queryParams) {
        throw new UnsupportedOperationException(
                "No direct equivalent endpoint exists in Tradex backend. " +
                "Tradex uses Deals (/api/ai/deals) and Contracts (/api/ai/contracts/{id}) instead of standalone customer orders. " +
                "Direct mapping to orders is prohibited without explicit business logic equivalence confirmation.");
    }

    /**
     * Legacy demo method: GET /api/ai/orders/{id}.
     * Tradex backend does not maintain generic customer orders; it models trading activity
     * as Deals (GET /api/ai/deals) and Contracts (GET /api/ai/contracts/{id}).
     */
    public String getOrderById(Long id) {
        throw new UnsupportedOperationException(
                "No direct equivalent endpoint exists in Tradex backend. " +
                "Tradex uses Deals (/api/ai/deals) and Contracts (/api/ai/contracts/{id}) instead of standalone customer orders. " +
                "Direct mapping to orders is prohibited without explicit business logic equivalence confirmation.");
    }
}