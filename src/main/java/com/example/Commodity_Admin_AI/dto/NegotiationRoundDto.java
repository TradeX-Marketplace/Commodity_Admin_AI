package com.example.Commodity_Admin_AI.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/**
 * DTO representing an individual round in a multi-round negotiation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NegotiationRoundDto {

    private Integer roundNumber;
    private String offeredBy;
    private String offeredByPartyId;
    private BigDecimal rate;
    private String remarks;
    private String createdAt;

    public NegotiationRoundDto() {
    }

    public NegotiationRoundDto(Integer roundNumber, String offeredBy, String offeredByPartyId,
                               BigDecimal rate, String remarks, String createdAt) {
        this.roundNumber = roundNumber;
        this.offeredBy = offeredBy;
        this.offeredByPartyId = offeredByPartyId;
        this.rate = rate;
        this.remarks = remarks;
        this.createdAt = createdAt;
    }

    public Integer getRoundNumber() {
        return roundNumber;
    }

    public void setRoundNumber(Integer roundNumber) {
        this.roundNumber = roundNumber;
    }

    public String getOfferedBy() {
        return offeredBy;
    }

    public void setOfferedBy(String offeredBy) {
        this.offeredBy = offeredBy;
    }

    public String getOfferedByPartyId() {
        return offeredByPartyId;
    }

    public void setOfferedByPartyId(String offeredByPartyId) {
        this.offeredByPartyId = offeredByPartyId;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
