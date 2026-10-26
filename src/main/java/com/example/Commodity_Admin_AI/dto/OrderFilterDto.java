package com.example.Commodity_Admin_AI.dto;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Filter specification extracted from natural language queries for querying
 * the Marketplace Backend Orders API.
 */
public class OrderFilterDto {

    private String startDate;
    private String endDate;
    private String buyerName;
    private String commodityName;
    private String marketLocation;
    private String deliveryLocation;
    private String status;
    private BigDecimal minQuantity;
    private BigDecimal maxQuantity;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Integer page;
    private Integer size;

    private boolean totalValueCalculation;
    private boolean clarificationNeeded;
    private String clarificationPrompt;

    public OrderFilterDto() {
    }

    public Map<String, Object> toQueryParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        if (startDate != null && !startDate.isBlank()) {
            params.put("startDate", startDate.trim());
        }
        if (endDate != null && !endDate.isBlank()) {
            params.put("endDate", endDate.trim());
        }
        if (buyerName != null && !buyerName.isBlank()) {
            params.put("buyerName", buyerName.trim());
        }
        if (commodityName != null && !commodityName.isBlank()) {
            params.put("commodity", commodityName.trim());
        }
        if (marketLocation != null && !marketLocation.isBlank()) {
            params.put("marketLocation", marketLocation.trim());
        }
        if (deliveryLocation != null && !deliveryLocation.isBlank()) {
            params.put("deliveryLocation", deliveryLocation.trim());
        }
        if (status != null && !status.isBlank()) {
            params.put("status", status.trim().toUpperCase());
        }
        if (minQuantity != null) {
            params.put("minQuantity", minQuantity);
        }
        if (maxQuantity != null) {
            params.put("maxQuantity", maxQuantity);
        }
        if (minPrice != null) {
            params.put("minPrice", minPrice);
        }
        if (maxPrice != null) {
            params.put("maxPrice", maxPrice);
        }
        if (page != null) {
            params.put("page", page);
        }
        if (size != null) {
            params.put("size", size);
        }
        return params;
    }

    public boolean hasAnyFilter() {
        return (startDate != null && !startDate.isBlank())
                || (endDate != null && !endDate.isBlank())
                || (buyerName != null && !buyerName.isBlank())
                || (commodityName != null && !commodityName.isBlank())
                || (marketLocation != null && !marketLocation.isBlank())
                || (deliveryLocation != null && !deliveryLocation.isBlank())
                || (status != null && !status.isBlank())
                || minQuantity != null
                || maxQuantity != null
                || minPrice != null
                || maxPrice != null;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public void setBuyerName(String buyerName) {
        this.buyerName = buyerName;
    }

    public String getCommodityName() {
        return commodityName;
    }

    public void setCommodityName(String commodityName) {
        this.commodityName = commodityName;
    }

    public String getMarketLocation() {
        return marketLocation;
    }

    public void setMarketLocation(String marketLocation) {
        this.marketLocation = marketLocation;
    }

    public String getDeliveryLocation() {
        return deliveryLocation;
    }

    public void setDeliveryLocation(String deliveryLocation) {
        this.deliveryLocation = deliveryLocation;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getMinQuantity() {
        return minQuantity;
    }

    public void setMinQuantity(BigDecimal minQuantity) {
        this.minQuantity = minQuantity;
    }

    public BigDecimal getMaxQuantity() {
        return maxQuantity;
    }

    public void setMaxQuantity(BigDecimal maxQuantity) {
        this.maxQuantity = maxQuantity;
    }

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(BigDecimal minPrice) {
        this.minPrice = minPrice;
    }

    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(BigDecimal maxPrice) {
        this.maxPrice = maxPrice;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public boolean isTotalValueCalculation() {
        return totalValueCalculation;
    }

    public void setTotalValueCalculation(boolean totalValueCalculation) {
        this.totalValueCalculation = totalValueCalculation;
    }

    public boolean isClarificationNeeded() {
        return clarificationNeeded;
    }

    public void setClarificationNeeded(boolean clarificationNeeded) {
        this.clarificationNeeded = clarificationNeeded;
    }

    public String getClarificationPrompt() {
        return clarificationPrompt;
    }

    public void setClarificationPrompt(String clarificationPrompt) {
        this.clarificationPrompt = clarificationPrompt;
    }

    @Override
    public String toString() {
        return "OrderFilterDto{" +
                "startDate='" + startDate + '\'' +
                ", endDate='" + endDate + '\'' +
                ", buyerName='" + buyerName + '\'' +
                ", commodityName='" + commodityName + '\'' +
                ", marketLocation='" + marketLocation + '\'' +
                ", deliveryLocation='" + deliveryLocation + '\'' +
                ", status='" + status + '\'' +
                ", minQuantity=" + minQuantity +
                ", maxQuantity=" + maxQuantity +
                ", minPrice=" + minPrice +
                ", maxPrice=" + maxPrice +
                ", page=" + page +
                ", size=" + size +
                ", totalValueCalculation=" + totalValueCalculation +
                ", clarificationNeeded=" + clarificationNeeded +
                '}';
    }
}
