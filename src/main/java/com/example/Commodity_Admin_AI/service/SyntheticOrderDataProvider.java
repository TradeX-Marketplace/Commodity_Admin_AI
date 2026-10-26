package com.example.Commodity_Admin_AI.service;

import com.example.Commodity_Admin_AI.dto.OrderDto;
import com.example.Commodity_Admin_AI.dto.OrderFilterDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Provider of realistic synthetic customer order test data for demonstration,
 * comprehensive filter testing, and chatbot verification in Commodity_Admin_AI.
 *
 * Used when the external Tradex backend models commercial transactions as Deals/Contracts
 * and does not expose a customer-facing Orders API endpoint (/api/ai/orders).
 */
@Component
public class SyntheticOrderDataProvider {

    private static final Logger log = LoggerFactory.getLogger(SyntheticOrderDataProvider.class);
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Kolkata");

    /**
     * Generates a realistic set of synthetic orders dynamically aligned with the current date.
     *
     * @return list of synthetic OrderDto records
     */
    public List<OrderDto> getAllSyntheticOrders() {
        LocalDate today = LocalDate.now(DEFAULT_ZONE);
        LocalDate yesterday = today.minusDays(1);

        List<OrderDto> orders = new ArrayList<>();

        // Today's orders (2 orders for Vishal Patil)
        orders.add(new OrderDto(
                101L,
                "Vishal Patil",
                "+91-9876543210",
                "Soybean",
                new BigDecimal("100.00"),
                "Quintal",
                new BigDecimal("4500.00"),
                new BigDecimal("450000.00"),
                "Sangli",
                "Kolhapur",
                "CONFIRMED",
                today.toString() + "T09:30:00"
        ));

        orders.add(new OrderDto(
                102L,
                "Vishal Patil",
                "+91-9876543210",
                "Soybean",
                new BigDecimal("50.00"),
                "Quintal",
                new BigDecimal("4500.00"),
                new BigDecimal("225000.00"),
                "Sangli",
                "Kolhapur",
                "COMPLETED",
                today.toString() + "T14:15:00"
        ));

        // Yesterday's orders
        orders.add(new OrderDto(
                103L,
                "K.K. Canvassing Agent",
                "+91-9822012345",
                "Soybean",
                new BigDecimal("200.00"),
                "Quintal",
                new BigDecimal("4400.00"),
                new BigDecimal("880000.00"),
                "Pune",
                "Pune",
                "CONFIRMED",
                yesterday.toString() + "T11:00:00"
        ));

        orders.add(new OrderDto(
                104L,
                "Ramesh Sharma",
                "+91-9811098765",
                "Wheat",
                new BigDecimal("150.00"),
                "Quintal",
                new BigDecimal("2850.00"),
                new BigDecimal("427500.00"),
                "Pune",
                "Mumbai",
                "COMPLETED",
                yesterday.toString() + "T16:30:00"
        ));

        // 15 October 2026 orders
        orders.add(new OrderDto(
                105L,
                "Amit Deshmukh",
                "+91-9890123456",
                "Cotton",
                new BigDecimal("80.00"),
                "Quintal",
                new BigDecimal("6250.00"),
                new BigDecimal("500000.00"),
                "Nagpur",
                "Nagpur",
                "CONFIRMED",
                "2026-10-15T10:00:00"
        ));

        // 14 October 2026 orders (Historical test window)
        orders.add(new OrderDto(
                106L,
                "Pooja Kulkarni",
                "+91-9823456789",
                "Maize",
                new BigDecimal("120.00"),
                "Quintal",
                new BigDecimal("2235.00"),
                new BigDecimal("268200.00"),
                "Sangli",
                "Satara",
                "COMPLETED",
                "2026-10-14T11:30:00"
        ));

        orders.add(new OrderDto(
                107L,
                "Vishal Patil",
                "+91-9876543210",
                "Soybean",
                new BigDecimal("75.00"),
                "Quintal",
                new BigDecimal("4285.00"),
                new BigDecimal("321375.00"),
                "Sangli",
                "Kolhapur",
                "COMPLETED",
                "2026-10-14T15:45:00"
        ));

        orders.add(new OrderDto(
                108L,
                "Suresh Gupta",
                "+91-9850123789",
                "Wheat",
                new BigDecimal("90.00"),
                "Quintal",
                new BigDecimal("2825.00"),
                new BigDecimal("254250.00"),
                "Pune",
                "Solapur",
                "PENDING",
                "2026-10-14T09:00:00"
        ));

        // Earlier October orders
        orders.add(new OrderDto(
                109L,
                "K.K. Canvassing Agent",
                "+91-9822012345",
                "Cotton",
                new BigDecimal("60.00"),
                "Quintal",
                new BigDecimal("6230.00"),
                new BigDecimal("373800.00"),
                "Nagpur",
                "Pune",
                "CANCELLED",
                "2026-10-12T14:00:00"
        ));

        orders.add(new OrderDto(
                110L,
                "Ramesh Sharma",
                "+91-9811098765",
                "Soybean",
                new BigDecimal("110.00"),
                "Quintal",
                new BigDecimal("4260.00"),
                new BigDecimal("468600.00"),
                "Sangli",
                "Mumbai",
                "COMPLETED",
                "2026-10-09T10:30:00"
        ));

        return orders;
    }

    /**
     * Filters synthetic orders according to the extracted OrderFilterDto.
     *
     * @param filter filter specification
     * @return filtered list of matching OrderDto records
     */
    public List<OrderDto> filterOrders(OrderFilterDto filter) {
        List<OrderDto> all = getAllSyntheticOrders();
        if (filter == null || !filter.hasAnyFilter()) {
            return all;
        }

        return all.stream().filter(order -> {
            // 1. Date Range Filter
            if (filter.getStartDate() != null && !filter.getStartDate().isBlank()) {
                String orderDate = extractDate(order.getCreatedAt());
                if (orderDate.compareTo(filter.getStartDate().trim()) < 0) {
                    return false;
                }
            }
            if (filter.getEndDate() != null && !filter.getEndDate().isBlank()) {
                String orderDate = extractDate(order.getCreatedAt());
                if (orderDate.compareTo(filter.getEndDate().trim()) > 0) {
                    return false;
                }
            }

            // 2. Buyer Name Filter
            if (filter.getBuyerName() != null && !filter.getBuyerName().isBlank()) {
                String requestedBuyer = filter.getBuyerName().trim().toLowerCase(Locale.ROOT);
                String actualBuyer = order.getBuyerName() != null ? order.getBuyerName().toLowerCase(Locale.ROOT) : "";
                if (!actualBuyer.contains(requestedBuyer) && !requestedBuyer.contains(actualBuyer)) {
                    return false;
                }
            }

            // 3. Commodity Name Filter
            if (filter.getCommodityName() != null && !filter.getCommodityName().isBlank()) {
                String requestedComm = filter.getCommodityName().trim().toLowerCase(Locale.ROOT);
                String actualComm = order.getCommodityName() != null ? order.getCommodityName().toLowerCase(Locale.ROOT) : "";
                if (!actualComm.equalsIgnoreCase(requestedComm)) {
                    return false;
                }
            }

            // 4. Market Location Filter
            if (filter.getMarketLocation() != null && !filter.getMarketLocation().isBlank()) {
                String requestedLoc = filter.getMarketLocation().trim().toLowerCase(Locale.ROOT);
                String actualLoc = order.getMarketLocation() != null ? order.getMarketLocation().toLowerCase(Locale.ROOT) : "";
                if (!actualLoc.equalsIgnoreCase(requestedLoc)) {
                    return false;
                }
            }

            // 5. Delivery Location Filter
            if (filter.getDeliveryLocation() != null && !filter.getDeliveryLocation().isBlank()) {
                String requestedLoc = filter.getDeliveryLocation().trim().toLowerCase(Locale.ROOT);
                String actualLoc = order.getDeliveryLocation() != null ? order.getDeliveryLocation().toLowerCase(Locale.ROOT) : "";
                if (!actualLoc.equalsIgnoreCase(requestedLoc)) {
                    return false;
                }
            }

            // 6. Status Filter
            if (filter.getStatus() != null && !filter.getStatus().isBlank()) {
                String requestedStatus = filter.getStatus().trim().toUpperCase(Locale.ROOT);
                String actualStatus = order.getStatus() != null ? order.getStatus().toUpperCase(Locale.ROOT) : "";
                if (!actualStatus.equals(requestedStatus)) {
                    return false;
                }
            }

            // 7. Quantity bounds
            if (filter.getMinQuantity() != null && order.getQuantity() != null) {
                if (order.getQuantity().compareTo(filter.getMinQuantity()) < 0) {
                    return false;
                }
            }
            if (filter.getMaxQuantity() != null && order.getQuantity() != null) {
                if (order.getQuantity().compareTo(filter.getMaxQuantity()) > 0) {
                    return false;
                }
            }

            // 8. Price bounds
            if (filter.getMinPrice() != null && order.getPricePerUnit() != null) {
                if (order.getPricePerUnit().compareTo(filter.getMinPrice()) < 0) {
                    return false;
                }
            }
            if (filter.getMaxPrice() != null && order.getPricePerUnit() != null) {
                if (order.getPricePerUnit().compareTo(filter.getMaxPrice()) > 0) {
                    return false;
                }
            }

            return true;
        }).collect(Collectors.toList());
    }

    /**
     * Looks up an order by ID from the synthetic dataset.
     *
     * @param id order ID
     * @return OrderDto if found, or null
     */
    public OrderDto getOrderById(Long id) {
        if (id == null) return null;
        for (OrderDto o : getAllSyntheticOrders()) {
            if (id.equals(o.getId())) {
                return o;
            }
        }
        return null;
    }

    private String extractDate(String createdAt) {
        if (createdAt == null || createdAt.isBlank()) {
            return "";
        }
        if (createdAt.contains("T")) {
            return createdAt.substring(0, createdAt.indexOf('T'));
        }
        if (createdAt.length() >= 10) {
            return createdAt.substring(0, 10);
        }
        return createdAt.trim();
    }
}
