package com.example.Commodity_Admin_AI.controller;

import com.example.Commodity_Admin_AI.dto.OrderDto;
import com.example.Commodity_Admin_AI.dto.OrderSummaryDto;
import com.example.Commodity_Admin_AI.service.MarketplaceDataService;
import com.example.Commodity_Admin_AI.service.SyntheticOrderDataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SyntheticOrdersDemoControllerTest {

    private SyntheticOrderDataProvider provider;
    private SyntheticOrdersDemoController controller;

    @BeforeEach
    void setUp() {
        provider = new SyntheticOrderDataProvider();
        controller = new SyntheticOrdersDemoController(provider, null);
    }

    @Test
    @DisplayName("GET /api/admin/orders returns all 10 synthetic orders when no filter is provided")
    void testGetOrders_NoFilter() {
        ResponseEntity<List<OrderDto>> response = controller.getOrders(
                null, null, null, null, null, null, null, null, null, null, null);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(10, response.getBody().size());
    }

    @Test
    @DisplayName("GET /api/admin/orders with commodity filter returns matching subset")
    void testGetOrders_CommodityFilter() {
        ResponseEntity<List<OrderDto>> response = controller.getOrders(
                null, null, null, "Wheat", null, null, null, null, null, null, null);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertTrue(response.getBody().stream().allMatch(o -> "Wheat".equalsIgnoreCase(o.getCommodityName())));
    }

    @Test
    @DisplayName("GET /api/admin/orders/{id} returns order or 404")
    void testGetOrderById() {
        ResponseEntity<OrderDto> found = controller.getOrderById(101L);
        assertEquals(200, found.getStatusCode().value());
        assertNotNull(found.getBody());
        assertEquals("Vishal Patil", found.getBody().getBuyerName());

        ResponseEntity<OrderDto> notFound = controller.getOrderById(9999L);
        assertEquals(404, notFound.getStatusCode().value());
    }

    @Test
    @DisplayName("GET /api/admin/orders/summary calculates global totals across all orders")
    void testGetOrderSummary_Global() {
        ResponseEntity<OrderSummaryDto> response = controller.getOrderSummary(
                null, null, null, null, null, null, null, null, null, null, null);

        assertEquals(200, response.getStatusCode().value());
        OrderSummaryDto summary = response.getBody();
        assertNotNull(summary);
        assertEquals(10, summary.getTotalOrders());
        assertTrue(summary.getTotalValue().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(summary.getTotalQuantity().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(summary.getStatusBreakdown().containsKey("CONFIRMED"));
        assertTrue(summary.getStatusBreakdown().containsKey("COMPLETED"));
        assertTrue(summary.getCommodityBreakdown().containsKey("Soybean"));
        assertTrue(summary.getCommodityBreakdown().containsKey("Wheat"));
    }

    @Test
    @DisplayName("GET /api/admin/orders/summary correctly recalculates totals after buyer filtering")
    void testGetOrderSummary_RecalculatedForBuyer() {
        ResponseEntity<OrderSummaryDto> response = controller.getOrderSummary(
                null, null, "Vishal Patil", null, null, null, null, null, null, null, null);

        assertEquals(200, response.getStatusCode().value());
        OrderSummaryDto summary = response.getBody();
        assertNotNull(summary);
        assertEquals(3, summary.getTotalOrders());
        assertEquals(new BigDecimal("996375.00"), summary.getTotalValue());
        assertEquals(new BigDecimal("225.00"), summary.getTotalQuantity());
    }
}
