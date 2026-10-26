package com.example.Commodity_Admin_AI.dto;

import java.math.BigDecimal;

/**
 * Data transfer object representing a single date-rate point
 * for structured chart and rate history responses.
 */
public class ChartData {

    private String date;
    private BigDecimal rate;
    private String commodity;
    private String location;
    private String unit;
    private String priceType; // "Historical Record" or "Asking Price"

    public ChartData() {
    }

    public ChartData(String date, BigDecimal rate) {
        this.date = date;
        this.rate = rate;
    }

    public ChartData(String date, BigDecimal rate, String commodity, String location, String unit) {
        this.date = date;
        this.rate = rate;
        this.commodity = commodity;
        this.location = location;
        this.unit = unit;
        this.priceType = "Historical Record";
    }

    public ChartData(String date, BigDecimal rate, String commodity, String location, String unit, String priceType) {
        this.date = date;
        this.rate = rate;
        this.commodity = commodity;
        this.location = location;
        this.unit = unit;
        this.priceType = priceType != null && !priceType.isBlank() ? priceType : "Historical Record";
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public String getCommodity() {
        return commodity;
    }

    public void setCommodity(String commodity) {
        this.commodity = commodity;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getPriceType() {
        return priceType;
    }

    public void setPriceType(String priceType) {
        this.priceType = priceType;
    }
}
