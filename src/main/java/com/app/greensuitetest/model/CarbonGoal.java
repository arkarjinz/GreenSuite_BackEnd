package com.app.greensuitetest.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import lombok.Data;
import java.time.LocalDateTime;

@Document(collection = "carbon_goals")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CarbonGoal {
    @Id
    private String id;

    private String companyId;
    private String userId; // optional, if goals are per user

    private String month; // "2024-07"
    private String year; // e.g., "2024"

    private Double targetElectricity;
    private Double targetFuel;
    private Double targetWater;
    private Double targetWaste;

    private Boolean isMet; // can be null initially

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
