package com.example.Commodity_Admin_AI.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data Transfer Object representing an authoritative customer order record
 * received from the Marketplace Backend Orders REST API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderDto {

    private Long id;
    private String buyerName;
    private String buyerPhone;
    private String commodityName;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal pricePerUnit;
    private BigDecimal totalAmount;
    private String marketLocation;
    private String deliveryLocation;
    private String status;
    private String createdAt;

    public OrderDto() {
    }

    public OrderDto(Long id, String buyerName, String buyerPhone, String commodityName,
                    BigDecimal quantity, String unit, BigDecimal pricePerUnit, BigDecimal totalAmount,
                    String marketLocation, String deliveryLocation, String status, String createdAt) {
        this.id = id;
        this.buyerName = buyerName;
        this.buyerPhone = buyerPhone;
        this.commodityName = commodityName;
        this.quantity = quantity;
        this.unit = unit;
        this.pricePerUnit = pricePerUnit;
        this.totalAmount = totalAmount;
        this.marketLocation = marketLocation;
        this.deliveryLocation = deliveryLocation;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public void setBuyerName(String buyerName) {
        this.buyerName = buyerName;
    }

    public String getBuyerPhone() {
        return buyerPhone;
    }

    public void setBuyerPhone(String buyerPhone) {
        this.buyerPhone = buyerPhone;
    }

    public String getCommodityName() {
        return commodityName;
    }

    public void setCommodityName(String commodityName) {
        this.commodityName = commodityName;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getPricePerUnit() {
        return pricePerUnit;
    }

    public void setPricePerUnit(BigDecimal pricePerUnit) {
        this.pricePerUnit = pricePerUnit;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
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

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "OrderDto{" +
                "id=" + id +
                ", buyerName='" + buyerName + '\'' +
                ", commodityName='" + commodityName + '\'' +
                ", quantity=" + quantity +
                ", unit='" + unit + '\'' +
                ", pricePerUnit=" + pricePerUnit +
                ", totalAmount=" + totalAmount +
                ", marketLocation='" + marketLocation + '\'' +
                ", deliveryLocation='" + deliveryLocation + '\'' +
                ", status='" + status + '\'' +
                ", createdAt='" + createdAt + '\'' +
                '}';
    }
}
