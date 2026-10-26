package com.example.Commodity_Admin_AI.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/**
 * DTO representing a Bid record retrieved from the TradeX backend /api/ai/bids endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BidDto {

    private Long id;
    private String bidNumber;
    private String commodityName;
    private String maskedBidderId;
    private BigDecimal marketRate;
    private BigDecimal bidRate;
    private BigDecimal counterRate;
    private BigDecimal quantity;
    private String destination;
    private String bidType;
    private String remarks;
    private String counterRemarks;
    private String status;
    private String createdAt;
    private String updatedAt;

    public BidDto() {
    }

    public BidDto(Long id, String bidNumber, String commodityName, String maskedBidderId,
                  BigDecimal marketRate, BigDecimal bidRate, BigDecimal counterRate,
                  BigDecimal quantity, String destination, String bidType,
                  String remarks, String counterRemarks, String status,
                  String createdAt, String updatedAt) {
        this.id = id;
        this.bidNumber = bidNumber;
        this.commodityName = commodityName;
        this.maskedBidderId = maskedBidderId;
        this.marketRate = marketRate;
        this.bidRate = bidRate;
        this.counterRate = counterRate;
        this.quantity = quantity;
        this.destination = destination;
        this.bidType = bidType;
        this.remarks = remarks;
        this.counterRemarks = counterRemarks;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBidNumber() {
        return bidNumber;
    }

    public void setBidNumber(String bidNumber) {
        this.bidNumber = bidNumber;
    }

    public String getCommodityName() {
        return commodityName;
    }

    public void setCommodityName(String commodityName) {
        this.commodityName = commodityName;
    }

    public String getMaskedBidderId() {
        return maskedBidderId;
    }

    public void setMaskedBidderId(String maskedBidderId) {
        this.maskedBidderId = maskedBidderId;
    }

    public BigDecimal getMarketRate() {
        return marketRate;
    }

    public void setMarketRate(BigDecimal marketRate) {
        this.marketRate = marketRate;
    }

    public BigDecimal getBidRate() {
        return bidRate;
    }

    public void setBidRate(BigDecimal bidRate) {
        this.bidRate = bidRate;
    }

    public BigDecimal getCounterRate() {
        return counterRate;
    }

    public void setCounterRate(BigDecimal counterRate) {
        this.counterRate = counterRate;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getBidType() {
        return bidType;
    }

    public void setBidType(String bidType) {
        this.bidType = bidType;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public String getCounterRemarks() {
        return counterRemarks;
    }

    public void setCounterRemarks(String counterRemarks) {
        this.counterRemarks = counterRemarks;
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

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
