package com.app.greensuitetest.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Document(collection = "carbon_activities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CarbonActivity {
    @Id
    private String id;

    @Field("company_id")
    private String companyId;
    @Field("user_id")//added by thu thu win to know who input the data except from the company name
    private String userId;
    private String month; // e.g., "07"
    private String year;  // e.g., "2025"


    private String activityType;
    private double inputValue;
    private String inputUnit;
    private double footprint;
    private String region;
    private String fuelType;
    private String disposalMethod;
    private LocalDateTime timestamp;
}