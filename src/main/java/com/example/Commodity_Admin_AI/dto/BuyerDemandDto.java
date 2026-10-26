package com.example.Commodity_Admin_AI.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Data Transfer Object representing an approved buyer demand record
 * received from the Marketplace Backend REST API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BuyerDemandDto {

    private Long id;
    private String demandNumber;
    private String buyerName;
    private Long commodityId;
    private String commodityName;
    private Double quantity;
    private Double fulfilledQuantity;
    private Double targetRate;
    private String unit;
    private String location;
    private String destination;
    private String destinationZone;
    private String maskedLabel;
    private String requiredDate;
    private String validTill;
    private String qualityGrade;
    private String status;
    private String createdBy;
    private String createdAt;

    public BuyerDemandDto() {
    }

    public BuyerDemandDto(Long id, String buyerName, Long commodityId, String commodityName,
                          Double quantity, String unit, String location, String requiredDate,
                          String status, String createdAt) {
        this.id = id;
        this.buyerName = buyerName;
        this.commodityId = commodityId;
        this.commodityName = commodityName;
        this.quantity = quantity;
        this.unit = unit;
        this.location = location;
        this.requiredDate = requiredDate;
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

    public Long getCommodityId() {
        return commodityId;
    }

    public void setCommodityId(Long commodityId) {
        this.commodityId = commodityId;
    }

    public String getCommodityName() {
        return commodityName;
    }

    public void setCommodityName(String commodityName) {
        this.commodityName = commodityName;
    }

    public Double getQuantity() {
        return quantity;
    }

    public void setQuantity(Double quantity) {
        this.quantity = quantity;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getLocation() {
        return location != null ? location : destination;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getRequiredDate() {
        return requiredDate;
    }

    public void setRequiredDate(String requiredDate) {
        this.requiredDate = requiredDate;
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

    public String getDemandNumber() {
        return demandNumber;
    }

    public void setDemandNumber(String demandNumber) {
        this.demandNumber = demandNumber;
    }

    public Double getTargetRate() {
        return targetRate;
    }

    public void setTargetRate(Double targetRate) {
        this.targetRate = targetRate;
    }

    public Double getFulfilledQuantity() {
        return fulfilledQuantity;
    }

    public void setFulfilledQuantity(Double fulfilledQuantity) {
        this.fulfilledQuantity = fulfilledQuantity;
    }

    public String getDestination() {
        return destination != null ? destination : location;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("targetPrice")
    public Double getTargetPrice() {
        return targetRate;
    }

    public void setTargetPrice(Double targetPrice) {
        this.targetRate = targetPrice;
    }

    public String getDestinationZone() {
        return destinationZone;
    }

    public void setDestinationZone(String destinationZone) {
        this.destinationZone = destinationZone;
    }

    public String getMaskedLabel() {
        return maskedLabel;
    }

    public void setMaskedLabel(String maskedLabel) {
        this.maskedLabel = maskedLabel;
    }

    public String getValidTill() {
        return validTill;
    }

    public void setValidTill(String validTill) {
        this.validTill = validTill;
    }

    public String getQualityGrade() {
        return qualityGrade;
    }

    public void setQualityGrade(String qualityGrade) {
        this.qualityGrade = qualityGrade;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    @Override
    public String toString() {
        return "BuyerDemandDto{" +
                "id=" + id +
                ", buyerName='" + buyerName + '\'' +
                ", commodityName='" + commodityName + '\'' +
                ", quantity=" + quantity +
                ", unit='" + unit + '\'' +
                ", location='" + location + '\'' +
                ", requiredDate='" + requiredDate + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
