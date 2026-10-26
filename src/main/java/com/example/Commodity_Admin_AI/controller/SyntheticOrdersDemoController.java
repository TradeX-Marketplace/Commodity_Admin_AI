package com.example.Commodity_Admin_AI.controller;

import com.example.Commodity_Admin_AI.dto.OrderDto;
import com.example.Commodity_Admin_AI.dto.OrderFilterDto;
import com.example.Commodity_Admin_AI.dto.OrderSummaryDto;
import com.example.Commodity_Admin_AI.service.MarketplaceDataService;
import com.example.Commodity_Admin_AI.service.SyntheticOrderDataProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Isolated demo and test REST controller for Orders in Commodity_Admin_AI.
 * Exposes endpoints for inspecting synthetic orders, filtering, individual order lookup,
 * and aggregated summary calculation (total count, value, quantity, status breakdown).
 *
 * Adheres strictly to architectural boundaries:
 * - Operates entirely within Commodity_Admin_AI.
 * - Does not modify or invoke external write APIs.
 * - Respects privacy policies (never returns sensitive secrets).
 */
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"})
@RestController
@RequestMapping({"/api/admin/orders", "/api/demo/orders"})
public class SyntheticOrdersDemoController {

    private final SyntheticOrderDataProvider syntheticOrderDataProvider;
    private final MarketplaceDataService marketplaceDataService;

    @Autowired
    public SyntheticOrdersDemoController(
            SyntheticOrderDataProvider syntheticOrderDataProvider,
            @Autowired(required = false) MarketplaceDataService marketplaceDataService) {
        this.syntheticOrderDataProvider = syntheticOrderDataProvider;
        this.marketplaceDataService = marketplaceDataService;
    }

    /**
     * Lists orders matching optional filter parameters.
     */
    @GetMapping
    public ResponseEntity<List<OrderDto>> getOrders(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String buyerName,
            @RequestParam(required = false) String commodityName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String marketLocation,
            @RequestParam(required = false) String deliveryLocation,
            @RequestParam(required = false) BigDecimal minQuantity,
            @RequestParam(required = false) BigDecimal maxQuantity,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice) {

        OrderFilterDto filter = new OrderFilterDto();
        filter.setStartDate(startDate);
        filter.setEndDate(endDate);
        filter.setBuyerName(buyerName);
        filter.setCommodityName(commodityName);
        filter.setStatus(status);
        filter.setMarketLocation(marketLocation);
        filter.setDeliveryLocation(deliveryLocation);
        filter.setMinQuantity(minQuantity);
        filter.setMaxQuantity(maxQuantity);
        filter.setMinPrice(minPrice);
        filter.setMaxPrice(maxPrice);

        List<OrderDto> results;
        if (marketplaceDataService != null && marketplaceDataService.isSyntheticOrdersEnabled()) {
            results = marketplaceDataService.fetchOrders(filter);
        } else {
            results = syntheticOrderDataProvider.filterOrders(filter);
        }
        return ResponseEntity.ok(results);
    }

    /**
     * Fetches details of a single order by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderDto> getOrderById(@PathVariable Long id) {
        OrderDto order;
        if (marketplaceDataService != null && marketplaceDataService.isSyntheticOrdersEnabled()) {
            order = marketplaceDataService.fetchOrderById(id);
        } else {
            order = syntheticOrderDataProvider.getOrderById(id);
        }
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(order);
    }

    /**
     * Computes real-time aggregated metrics (total count, value, quantity, status breakdown)
     * across the filtered set of orders.
     */
    @GetMapping("/summary")
    public ResponseEntity<OrderSummaryDto> getOrderSummary(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String buyerName,
            @RequestParam(required = false) String commodityName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String marketLocation,
            @RequestParam(required = false) String deliveryLocation,
            @RequestParam(required = false) BigDecimal minQuantity,
            @RequestParam(required = false) BigDecimal maxQuantity,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice) {

        OrderFilterDto filter = new OrderFilterDto();
        filter.setStartDate(startDate);
        filter.setEndDate(endDate);
        filter.setBuyerName(buyerName);
        filter.setCommodityName(commodityName);
        filter.setStatus(status);
        filter.setMarketLocation(marketLocation);
        filter.setDeliveryLocation(deliveryLocation);
        filter.setMinQuantity(minQuantity);
        filter.setMaxQuantity(maxQuantity);
        filter.setMinPrice(minPrice);
        filter.setMaxPrice(maxPrice);

        List<OrderDto> filtered = (marketplaceDataService != null && marketplaceDataService.isSyntheticOrdersEnabled())
                ? marketplaceDataService.fetchOrders(filter)
                : syntheticOrderDataProvider.filterOrders(filter);

        return ResponseEntity.ok(new OrderSummaryDto(filtered));
    }
}
