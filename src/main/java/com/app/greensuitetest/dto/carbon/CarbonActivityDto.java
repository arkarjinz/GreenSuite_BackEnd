package com.app.greensuitetest.dto.carbon;

import java.time.LocalDateTime;

import com.app.greensuitetest.model.CarbonActivity; // import your entity class

public class CarbonActivityDto {
    private String id;
    private String userId;
    private String companyId;
    private String month;
    private int year;
    private String activityType;
    private double value;
    private String unit;
    private double footprint;
    private String region;
    private String fuelType;
    private String disposalMethod;
    private LocalDateTime submittedAt;

    public static CarbonActivityDto fromEntity(CarbonActivity entity) {
        CarbonActivityDto dto = new CarbonActivityDto();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUserId());
        dto.setCompanyId(entity.getCompanyId());
        dto.setMonth(entity.getMonth());
        //dto.setYear(entity.getYear());
        // parse year safely, fallback to 0 if invalid
        try {
            dto.setYear(Integer.parseInt(entity.getYear()));
        } catch (NumberFormatException e) {
            dto.setYear(0);
        }
        dto.setActivityType(entity.getActivityType());
        dto.setValue(entity.getInputValue());
        dto.setUnit(entity.getInputUnit());
        dto.setFootprint(entity.getFootprint());
        dto.setRegion(entity.getRegion());
        dto.setFuelType(entity.getFuelType());
        dto.setDisposalMethod(entity.getDisposalMethod());
        dto.setSubmittedAt(entity.getTimestamp());
        return dto;
    }

    // Getters and Setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getCompanyId() { return companyId; }
    public void setCompanyId(String companyId) { this.companyId = companyId; }

    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public String getActivityType() { return activityType; }
    public void setActivityType(String activityType) { this.activityType = activityType; }

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public double getFootprint() { return footprint; }
    public void setFootprint(double footprint) { this.footprint = footprint; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getFuelType() { return fuelType; }
    public void setFuelType(String fuelType) { this.fuelType = fuelType; }

    public String getDisposalMethod() { return disposalMethod; }
    public void setDisposalMethod(String disposalMethod) { this.disposalMethod = disposalMethod; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
}
