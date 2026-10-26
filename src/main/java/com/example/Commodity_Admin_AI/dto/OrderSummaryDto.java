package com.example.Commodity_Admin_AI.dto;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Summary DTO aggregating order metrics across filtered or total synthetic order datasets.
 * Includes total count, total value, total quantity, status breakdown, and commodity breakdown.
 */
public class OrderSummaryDto {

    private int totalOrders;
    private BigDecimal totalValue = BigDecimal.ZERO;
    private BigDecimal totalQuantity = BigDecimal.ZERO;
    private Map<String, Long> statusBreakdown = new LinkedHashMap<>();
    private Map<String, Long> commodityBreakdown = new LinkedHashMap<>();
    private List<OrderDto> orders = Collections.emptyList();

    public OrderSummaryDto() {
    }

    public OrderSummaryDto(List<OrderDto> orders) {
        if (orders != null) {
            this.orders = orders;
            this.totalOrders = orders.size();
            BigDecimal sumVal = BigDecimal.ZERO;
            BigDecimal sumQty = BigDecimal.ZERO;
            for (OrderDto o : orders) {
                if (o.getTotalAmount() != null) {
                    sumVal = sumVal.add(o.getTotalAmount());
                }
                if (o.getQuantity() != null) {
                    sumQty = sumQty.add(o.getQuantity());
                }
                String status = o.getStatus() != null ? o.getStatus() : "UNKNOWN";
                statusBreakdown.put(status, statusBreakdown.getOrDefault(status, 0L) + 1L);

                String comm = o.getCommodityName() != null ? o.getCommodityName() : "UNKNOWN";
                commodityBreakdown.put(comm, commodityBreakdown.getOrDefault(comm, 0L) + 1L);
            }
            this.totalValue = sumVal.setScale(2, BigDecimal.ROUND_HALF_UP);
            this.totalQuantity = sumQty.setScale(2, BigDecimal.ROUND_HALF_UP);
        }
    }

    public int getTotalOrders() {
        return totalOrders;
    }

    public void setTotalOrders(int totalOrders) {
        this.totalOrders = totalOrders;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(BigDecimal totalValue) {
        this.totalValue = totalValue;
    }

    public BigDecimal getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(BigDecimal totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public Map<String, Long> getStatusBreakdown() {
        return statusBreakdown;
    }

    public void setStatusBreakdown(Map<String, Long> statusBreakdown) {
        this.statusBreakdown = statusBreakdown;
    }

    public Map<String, Long> getCommodityBreakdown() {
        return commodityBreakdown;
    }

    public void setCommodityBreakdown(Map<String, Long> commodityBreakdown) {
        this.commodityBreakdown = commodityBreakdown;
    }

    public List<OrderDto> getOrders() {
        return orders;
    }

    public void setOrders(List<OrderDto> orders) {
        this.orders = orders;
    }
}
