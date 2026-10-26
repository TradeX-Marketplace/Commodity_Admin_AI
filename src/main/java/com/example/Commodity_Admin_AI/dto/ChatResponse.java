package com.example.Commodity_Admin_AI.dto;

import java.util.List;

/**
 * Response payload returned to the Admin by the chatbot.
 *
 * The response can contain:
 * 1. Normal text message
 * 2. Language
 * 3. Optional display type
 * 4. Optional chart data
 */
public class ChatResponse {

    private Long sessionId;
    private String message;
    private String language;

    // Example: RATE, CHART, RATE_AND_CHART, DEMAND
    private String displayType;

    // Data used by the frontend to display a chart
    private List<ChartData> chartData;

    // Data used by the frontend for dynamic commodity buttons
    private List<String> commodities;

    // Structured buyer demands for DEMAND queries
    private List<BuyerDemandDto> buyerDemands;

    // Structured orders for ORDERS queries
    private List<OrderDto> orders;

    // Structured parties for PARTIES queries
    private List<PartyDto> parties;

    // Structured supplies for SUPPLIES queries
    private List<SupplyDto> supplies;

    // Structured bids for BIDS queries
    private List<BidDto> bids;

    // Structured negotiations for NEGOTIATIONS queries
    private List<NegotiationDto> negotiations;

    // Filter metadata for client-side preservation
    private String buyerName;
    private String startDate;
    private String endDate;

    public ChatResponse() {
    }

    public ChatResponse(Long sessionId, String message) {
        this.sessionId = sessionId;
        this.message = message;
    }

    public ChatResponse(Long sessionId, String message, String language) {
        this.sessionId = sessionId;
        this.message = message;
        this.language = language;
    }

    public ChatResponse(
            Long sessionId,
            String message,
            String language,
            String displayType,
            List<ChartData> chartData) {

        this.sessionId = sessionId;
        this.message = message;
        this.language = language;
        this.displayType = displayType;
        this.chartData = chartData;
    }

    public ChatResponse(
            Long sessionId,
            String message,
            String language,
            String displayType,
            List<ChartData> chartData,
            List<String> commodities,
            List<BuyerDemandDto> buyerDemands) {

        this.sessionId = sessionId;
        this.message = message;
        this.language = language;
        this.displayType = displayType;
        this.chartData = chartData;
        this.commodities = commodities;
        this.buyerDemands = buyerDemands;
    }

    public ChatResponse(
            Long sessionId,
            String message,
            String language,
            String displayType,
            List<ChartData> chartData,
            List<String> commodities,
            List<BuyerDemandDto> buyerDemands,
            List<OrderDto> orders) {

        this.sessionId = sessionId;
        this.message = message;
        this.language = language;
        this.displayType = displayType;
        this.chartData = chartData;
        this.commodities = commodities;
        this.buyerDemands = buyerDemands;
        this.orders = orders;
    }

    public ChatResponse(
            Long sessionId,
            String message,
            String language,
            String displayType,
            List<ChartData> chartData,
            List<String> commodities,
            List<BuyerDemandDto> buyerDemands,
            List<OrderDto> orders,
            List<PartyDto> parties) {

        this.sessionId = sessionId;
        this.message = message;
        this.language = language;
        this.displayType = displayType;
        this.chartData = chartData;
        this.commodities = commodities;
        this.buyerDemands = buyerDemands;
        this.orders = orders;
        this.parties = parties;
    }



    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getDisplayType() {
        return displayType;
    }

    public void setDisplayType(String displayType) {
        this.displayType = displayType;
    }

    public List<ChartData> getChartData() {
        return chartData;
    }

    public void setChartData(List<ChartData> chartData) {
        this.chartData = chartData;
    }

    public List<String> getCommodities() {
        return commodities;
    }

    public void setCommodities(List<String> commodities) {
        this.commodities = commodities;
    }

    public List<BuyerDemandDto> getBuyerDemands() {
        return buyerDemands;
    }

    public void setBuyerDemands(List<BuyerDemandDto> buyerDemands) {
        this.buyerDemands = buyerDemands;
    }

    public List<OrderDto> getOrders() {
        return orders;
    }

    public void setOrders(List<OrderDto> orders) {
        this.orders = orders;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public void setBuyerName(String buyerName) {
        this.buyerName = buyerName;
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

    public List<PartyDto> getParties() {
        return parties;
    }

    public void setParties(List<PartyDto> parties) {
        this.parties = parties;
    }

    public List<SupplyDto> getSupplies() {
        return supplies;
    }

    public void setSupplies(List<SupplyDto> supplies) {
        this.supplies = supplies;
    }

    public List<BidDto> getBids() {
        return bids;
    }

    public void setBids(List<BidDto> bids) {
        this.bids = bids;
    }

    public List<NegotiationDto> getNegotiations() {
        return negotiations;
    }

    public void setNegotiations(List<NegotiationDto> negotiations) {
        this.negotiations = negotiations;
    }
}