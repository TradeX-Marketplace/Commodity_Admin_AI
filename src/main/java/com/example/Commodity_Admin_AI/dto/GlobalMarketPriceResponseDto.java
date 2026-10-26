package com.example.Commodity_Admin_AI.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.List;

/**
 * Clean response DTO for the global market mandi prices API.
 * Encapsulates status, pagination details, query filters applied, and records list.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GlobalMarketPriceResponseDto {

    private String status;
    private String message;
    private long total;
    private int count;
    private int limit;
    private int offset;
    private String commodity;
    private String state;
    private String district;
    private String market;
    private List<GlobalMarketPriceRecordDto> records;

    public GlobalMarketPriceResponseDto() {
        this.records = Collections.emptyList();
    }

    public static GlobalMarketPriceResponseDto success(List<GlobalMarketPriceRecordDto> records,
                                                       long total, int count, int limit, int offset,
                                                       String commodity, String state, String district, String market) {
        GlobalMarketPriceResponseDto dto = new GlobalMarketPriceResponseDto();
        dto.setStatus("SUCCESS");
        dto.setMessage("Global market prices retrieved successfully.");
        dto.setTotal(total);
        dto.setCount(count);
        dto.setLimit(limit);
        dto.setOffset(offset);
        dto.setCommodity(commodity);
        dto.setState(state);
        dto.setDistrict(district);
        dto.setMarket(market);
        dto.setRecords(records != null ? records : Collections.emptyList());
        return dto;
    }

    public static GlobalMarketPriceResponseDto empty(String message, int limit, int offset,
                                                     String commodity, String state, String district, String market) {
        GlobalMarketPriceResponseDto dto = new GlobalMarketPriceResponseDto();
        dto.setStatus("EMPTY");
        dto.setMessage(message != null ? message : "No mandi records found matching the requested filters.");
        dto.setTotal(0);
        dto.setCount(0);
        dto.setLimit(limit);
        dto.setOffset(offset);
        dto.setCommodity(commodity);
        dto.setState(state);
        dto.setDistrict(district);
        dto.setMarket(market);
        dto.setRecords(Collections.emptyList());
        return dto;
    }

    public static GlobalMarketPriceResponseDto configError(String message) {
        GlobalMarketPriceResponseDto dto = new GlobalMarketPriceResponseDto();
        dto.setStatus("CONFIG_ERROR");
        dto.setMessage(message);
        dto.setRecords(Collections.emptyList());
        return dto;
    }

    public static GlobalMarketPriceResponseDto apiError(String message) {
        GlobalMarketPriceResponseDto dto = new GlobalMarketPriceResponseDto();
        dto.setStatus("API_ERROR");
        dto.setMessage(message);
        dto.setRecords(Collections.emptyList());
        return dto;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public String getCommodity() {
        return commodity;
    }

    public void setCommodity(String commodity) {
        this.commodity = commodity;
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

    public List<GlobalMarketPriceRecordDto> getRecords() {
        return records;
    }

    public void setRecords(List<GlobalMarketPriceRecordDto> records) {
        this.records = records;
    }
}
