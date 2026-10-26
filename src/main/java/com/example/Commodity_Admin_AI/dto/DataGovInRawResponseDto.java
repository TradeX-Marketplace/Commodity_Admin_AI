package com.example.Commodity_Admin_AI.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * Raw deserialization target for responses from https://api.data.gov.in/resource/{id}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataGovInRawResponseDto {

    private String status;
    private String title;
    private Object total;
    private Integer count;
    private Object limit;
    private Object offset;
    private List<RawRecord> records;

    public DataGovInRawResponseDto() {
        this.records = Collections.emptyList();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Object getTotal() {
        return total;
    }

    public void setTotal(Object total) {
        this.total = total;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public Object getLimit() {
        return limit;
    }

    public void setLimit(Object limit) {
        this.limit = limit;
    }

    public Object getOffset() {
        return offset;
    }

    public void setOffset(Object offset) {
        this.offset = offset;
    }

    public List<RawRecord> getRecords() {
        return records != null ? records : Collections.emptyList();
    }

    public void setRecords(List<RawRecord> records) {
        this.records = records;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RawRecord {
        private String state;
        private String district;
        private String market;
        private String commodity;
        private String variety;
        private String grade;

        @JsonProperty("arrival_date")
        private String arrivalDate;

        @JsonProperty("min_price")
        private String minPrice;

        @JsonProperty("max_price")
        private String maxPrice;

        @JsonProperty("modal_price")
        private String modalPrice;

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

        public String getMinPrice() {
            return minPrice;
        }

        public void setMinPrice(String minPrice) {
            this.minPrice = minPrice;
        }

        public String getMaxPrice() {
            return maxPrice;
        }

        public void setMaxPrice(String maxPrice) {
            this.maxPrice = maxPrice;
        }

        public String getModalPrice() {
            return modalPrice;
        }

        public void setModalPrice(String modalPrice) {
            this.modalPrice = modalPrice;
        }
    }
}
