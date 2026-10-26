package com.example.Commodity_Admin_AI.dto;

import java.util.Collections;
import java.util.List;

/**
 * Encapsulates paginated party results from TradeX GET /api/v1/parties,
 * including totalElements and pagination metadata.
 */
public class PartiesResultDto {

    private List<PartyDto> parties;
    private long totalElements;
    private int totalPages;
    private int pageNumber;
    private int pageSize;

    public PartiesResultDto() {
        this.parties = Collections.emptyList();
    }

    public PartiesResultDto(List<PartyDto> parties, long totalElements, int totalPages, int pageNumber, int pageSize) {
        this.parties = parties != null ? parties : Collections.emptyList();
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
    }

    public List<PartyDto> getParties() {
        return parties;
    }

    public void setParties(List<PartyDto> parties) {
        this.parties = parties != null ? parties : Collections.emptyList();
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
