package com.example.Commodity_Admin_AI.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

/**
 * DTO representing a Negotiation thread retrieved from TradeX.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NegotiationDto {

    private Long id;
    private String negotiationCode;
    private String commodityName;
    private BigDecimal quantity;
    private String destination;
    private BigDecimal marketRate;
    private BigDecimal buyerOffer;
    private BigDecimal sellerOffer;
    private BigDecimal currentOffer;
    private String lastOfferBy;
    private String status;
    private List<NegotiationRoundDto> rounds;
    private String createdAt;
    private String updatedAt;

    public NegotiationDto() {
    }

    public NegotiationDto(Long id, String negotiationCode, String commodityName, BigDecimal quantity,
                          String destination, BigDecimal marketRate, BigDecimal buyerOffer,
                          BigDecimal sellerOffer, BigDecimal currentOffer, String lastOfferBy,
                          String status, List<NegotiationRoundDto> rounds, String createdAt, String updatedAt) {
        this.id = id;
        this.negotiationCode = negotiationCode;
        this.commodityName = commodityName;
        this.quantity = quantity;
        this.destination = destination;
        this.marketRate = marketRate;
        this.buyerOffer = buyerOffer;
        this.sellerOffer = sellerOffer;
        this.currentOffer = currentOffer;
        this.lastOfferBy = lastOfferBy;
        this.status = status;
        this.rounds = rounds;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNegotiationCode() {
        return negotiationCode;
    }

    public void setNegotiationCode(String negotiationCode) {
        this.negotiationCode = negotiationCode;
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

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public BigDecimal getMarketRate() {
        return marketRate;
    }

    public void setMarketRate(BigDecimal marketRate) {
        this.marketRate = marketRate;
    }

    public BigDecimal getBuyerOffer() {
        return buyerOffer;
    }

    public void setBuyerOffer(BigDecimal buyerOffer) {
        this.buyerOffer = buyerOffer;
    }

    public BigDecimal getSellerOffer() {
        return sellerOffer;
    }

    public void setSellerOffer(BigDecimal sellerOffer) {
        this.sellerOffer = sellerOffer;
    }

    public BigDecimal getCurrentOffer() {
        return currentOffer;
    }

    public void setCurrentOffer(BigDecimal currentOffer) {
        this.currentOffer = currentOffer;
    }

    public String getLastOfferBy() {
        return lastOfferBy;
    }

    public void setLastOfferBy(String lastOfferBy) {
        this.lastOfferBy = lastOfferBy;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<NegotiationRoundDto> getRounds() {
        return rounds;
    }

    public void setRounds(List<NegotiationRoundDto> rounds) {
        this.rounds = rounds;
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
