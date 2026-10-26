package com.example.Commodity_Admin_AI.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Data transfer object representing a registered marketplace party / business entity.
 * Directly maps to TradeX GET /api/v1/parties response content.
 */
public class PartyDto {

    private Long id;
    private String partyCode;
    private String companyName;
    private String gstin;
    private String pan;
    private String contactPerson;
    private String email;
    private String phone;
    private String alternatePhone;
    private String address;
    private String city;
    private String taluka;
    private String plantLocation;
    private String state;
    private String country;
    private String postalCode;
    private String description;
    private String website;
    private Boolean active = true;
    private Boolean isDeleted = false;
    private String status = "ACTIVE";
    private String registrationType = "ADMIN_CREATED";
    private String createdAt;
    private String updatedAt;
    private List<String> roles = new ArrayList<>();

    public PartyDto() {
    }

    public PartyDto(Long id, String partyCode, String companyName, String city, String state, List<String> roles, String status, String createdAt) {
        this.id = id;
        this.partyCode = partyCode;
        this.companyName = companyName;
        this.city = city;
        this.state = state;
        this.roles = roles != null ? roles : new ArrayList<>();
        this.status = status != null ? status : "ACTIVE";
        this.createdAt = createdAt;
        this.active = true;
        this.isDeleted = false;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPartyCode() {
        return partyCode;
    }

    public void setPartyCode(String partyCode) {
        this.partyCode = partyCode;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getGstin() {
        return gstin;
    }

    public void setGstin(String gstin) {
        this.gstin = gstin;
    }

    public String getPan() {
        return pan;
    }

    public void setPan(String pan) {
        this.pan = pan;
    }

    public String getContactPerson() {
        return contactPerson;
    }

    public void setContactPerson(String contactPerson) {
        this.contactPerson = contactPerson;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAlternatePhone() {
        return alternatePhone;
    }

    public void setAlternatePhone(String alternatePhone) {
        this.alternatePhone = alternatePhone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getTaluka() {
        return taluka;
    }

    public void setTaluka(String taluka) {
        this.taluka = taluka;
    }

    public String getPlantLocation() {
        return plantLocation;
    }

    public void setPlantLocation(String plantLocation) {
        this.plantLocation = plantLocation;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRegistrationType() {
        return registrationType;
    }

    public void setRegistrationType(String registrationType) {
        this.registrationType = registrationType;
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

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles != null ? roles : new ArrayList<>();
    }

    @Override
    public String toString() {
        return "PartyDto{" +
                "partyCode='" + partyCode + '\'' +
                ", companyName='" + companyName + '\'' +
                ", city='" + city + '\'' +
                ", state='" + state + '\'' +
                ", roles=" + roles +
                ", active=" + active +
                ", status='" + status + '\'' +
                '}';
    }
}
