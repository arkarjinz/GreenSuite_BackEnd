package com.app.greensuitetest.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "carbon_totals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CarbonTotal {

    @Id
    private String id;

    @Field("user_id")
    private String userId;

    @Field("company_id")
    private String companyId;

    private String month;  // e.g., "07"
    private String year;   // e.g., "2025"

    private double totalFootprint;
}
