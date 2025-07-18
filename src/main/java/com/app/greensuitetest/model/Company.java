package com.app.greensuitetest.model;

import com.app.greensuitetest.constants.SubscriptionTier;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.*;

@Document(collection = "companies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Company {
    @Id
    private String id;

    @Indexed(unique = true)
    private String name;
    private String address;
    private String industry;

    private SubscriptionTier tier = SubscriptionTier.FREE;

    @Field("enabled_features")
    private Map<SubscriptionTier, Set<String>> enabledFeatures = new EnumMap<>(SubscriptionTier.class);

    @Field("created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}