package com.example.Commodity_Admin_AI.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Data Transfer Object representing an active commodity supply record
 * received from the TradeX Marketplace Backend GET /api/ai/supplies endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SupplyDto {

    private Long id;
    private String supplyNumber;
    private Long partyId;
    private String partyName;
    private String commodityName;
    private String parentCommodityName;
    private String qualityName;
    private String qualityGrade;
    private Double totalQuantity;
    private Double availableQuantity;
    private Double lockedQuantity;
    private String unit = "Quintal";
    private Double askingRate;
    private String location;
    private String originZone;
    private Long originManufacturerId;
    private String originManufacturerName;
    private String maskedLabel;
    private String labSpecs;
    private String validTill;
    private String status;
    private String createdBy;
    private String createdAt;

    public SupplyDto() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSupplyNumber() {
        return supplyNumber;
    }

    public void setSupplyNumber(String supplyNumber) {
        this.supplyNumber = supplyNumber;
    }

    public Long getPartyId() {
        return partyId;
    }

    public void setPartyId(Long partyId) {
        this.partyId = partyId;
    }

    public String getPartyName() {
        return partyName;
    }

    public void setPartyName(String partyName) {
        this.partyName = partyName;
    }

    public String getCommodityName() {
        return commodityName;
    }

    public void setCommodityName(String commodityName) {
        this.commodityName = commodityName;
    }

    public String getParentCommodityName() {
        return parentCommodityName;
    }

    public void setParentCommodityName(String parentCommodityName) {
        this.parentCommodityName = parentCommodityName;
    }

    public String getQualityName() {
        return qualityName;
    }

    public void setQualityName(String qualityName) {
        this.qualityName = qualityName;
    }

    public String getQualityGrade() {
        return qualityGrade;
    }

    public void setQualityGrade(String qualityGrade) {
        this.qualityGrade = qualityGrade;
    }

    public Double getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(Double totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public Double getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(Double availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    public Double getLockedQuantity() {
        return lockedQuantity;
    }

    public void setLockedQuantity(Double lockedQuantity) {
        this.lockedQuantity = lockedQuantity;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = (unit != null && !unit.isBlank()) ? unit.trim() : "Quintal";
    }

    public Double getAskingRate() {
        return askingRate;
    }

    public void setAskingRate(Double askingRate) {
        this.askingRate = askingRate;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getOriginZone() {
        return originZone;
    }

    public void setOriginZone(String originZone) {
        this.originZone = originZone;
    }

    public Long getOriginManufacturerId() {
        return originManufacturerId;
    }

    public void setOriginManufacturerId(Long originManufacturerId) {
        this.originManufacturerId = originManufacturerId;
    }

    public String getOriginManufacturerName() {
        return originManufacturerName;
    }

    public void setOriginManufacturerName(String originManufacturerName) {
        this.originManufacturerName = originManufacturerName;
    }

    public String getMaskedLabel() {
        return maskedLabel;
    }

    public void setMaskedLabel(String maskedLabel) {
        this.maskedLabel = maskedLabel;
    }

    public String getLabSpecs() {
        return labSpecs;
    }

    public void setLabSpecs(String labSpecs) {
        this.labSpecs = labSpecs;
    }

    public String getValidTill() {
        return validTill;
    }

    public void setValidTill(String validTill) {
        this.validTill = validTill;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
