package com.example.Commodity_Admin_AI.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Data Transfer Object representing approved marketplace rate data
 * received over HTTP/JSON from the Marketplace Backend project.
 *
 * Supports both standard field names ("commodity", "date") and backend API
 * field names ("commodityName", "rateDate") and safely ignores auxiliary fields ("id", "commodityId", "updatedAt").
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketplaceRateDto {

    private Long id;
    private Long commodityId;

    @NotBlank(message = "Commodity name is required")
    @JsonProperty("commodity")
    @JsonAlias({"commodityName", "name"})
    private String commodity;

    @NotBlank(message = "Location is required")
    @JsonProperty("location")
    private String location;

    @NotNull(message = "Rate is required")
    @Positive(message = "Rate must be positive")
    @JsonProperty("rate")
    private BigDecimal rate;

    @NotBlank(message = "Unit is required")
    @JsonProperty("unit")
    private String unit;

    @JsonProperty("currency")
    private String currency = "INR";

    @NotBlank(message = "Date is required")
    @JsonProperty("date")
    @JsonAlias({"rateDate"})
    private String date;

    private String updatedAt;

    @JsonProperty("priceType")
    private String priceType = "Market Rate";

    public MarketplaceRateDto() {
    }

    public MarketplaceRateDto(String commodity, String location, BigDecimal rate, String unit, String date) {
        this.commodity = commodity;
        this.location = location;
        this.rate = rate;
        this.unit = unit;
        this.currency = "INR";
        this.date = date;
    }

    public MarketplaceRateDto(String commodity, String location, BigDecimal rate, String unit, String currency, String date) {
        this.commodity = commodity;
        this.location = location;
        this.rate = rate;
        this.unit = unit;
        this.currency = currency != null && !currency.isBlank() ? currency : "INR";
        this.date = date;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCommodityId() {
        return commodityId;
    }

    public void setCommodityId(Long commodityId) {
        this.commodityId = commodityId;
    }

    public String getCommodity() {
        return commodity;
    }

    public void setCommodity(String commodity) {
        this.commodity = commodity;
    }

    public String getCommodityName() {
        return commodity;
    }

    public void setCommodityName(String commodityName) {
        this.commodity = commodityName;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getCurrency() {
        return currency != null && !currency.isBlank() ? currency : "INR";
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getRateDate() {
        return date;
    }

    public void setRateDate(String rateDate) {
        this.date = rateDate;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getPriceType() {
        return priceType != null && !priceType.isBlank() ? priceType : "Market Rate";
    }

    public void setPriceType(String priceType) {
        this.priceType = priceType;
    }
}
