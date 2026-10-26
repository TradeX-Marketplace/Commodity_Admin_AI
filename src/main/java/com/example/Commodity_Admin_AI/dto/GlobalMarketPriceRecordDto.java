package com.example.Commodity_Admin_AI.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * DTO representing an individual mandi price record fetched from the official
 * data.gov.in (AGMARKNET) external market price dataset.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GlobalMarketPriceRecordDto {

    private String state;
    private String district;
    private String market;
    private String commodity;
    private String variety;
    private String grade;
    private String arrivalDate;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private BigDecimal modalPrice;
    private String priceUnit;
    private String source;

    public GlobalMarketPriceRecordDto() {
        this.priceUnit = "₹ per Quintal";
        this.source = "data.gov.in (AGMARKNET)";
    }

    public GlobalMarketPriceRecordDto(String state, String district, String market, String commodity,
                                      String variety, String grade, String arrivalDate,
                                      BigDecimal minPrice, BigDecimal maxPrice, BigDecimal modalPrice) {
        this.state = state;
        this.district = district;
        this.market = market;
        this.commodity = commodity;
        this.variety = variety;
        this.grade = grade;
        this.arrivalDate = arrivalDate;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.modalPrice = modalPrice;
        this.priceUnit = "₹ per Quintal";
        this.source = "data.gov.in (AGMARKNET)";
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getMarket() {
        return market;
    }

    public void setMarket(String market) {
        this.market = market;
    }

    public String getCommodity() {
        return commodity;
    }

    public void setCommodity(String commodity) {
        this.commodity = commodity;
    }

    public String getVariety() {
        return variety;
    }

    public void setVariety(String variety) {
        this.variety = variety;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public String getArrivalDate() {
        return arrivalDate;
    }

    public void setArrivalDate(String arrivalDate) {
        this.arrivalDate = arrivalDate;
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

    public BigDecimal getModalPrice() {
        return modalPrice;
    }

    public void setModalPrice(BigDecimal modalPrice) {
        this.modalPrice = modalPrice;
    }

    public String getPriceUnit() {
        return priceUnit;
    }

    public void setPriceUnit(String priceUnit) {
        this.priceUnit = priceUnit;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
