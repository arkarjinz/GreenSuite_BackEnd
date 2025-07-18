package com.app.greensuitetest.config;

import com.app.greensuitetest.constants.SubscriptionTier;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Configuration
public class FeatureConfig {

    @Bean
    @ConfigurationProperties(prefix = "features")
    public FeatureProperties featureProperties() {
        return new FeatureProperties();
    }

    @Bean
    public Map<SubscriptionTier, Set<String>> featureMapping(FeatureProperties properties) {
        Map<SubscriptionTier, Set<String>> features = new EnumMap<>(SubscriptionTier.class);
        features.put(SubscriptionTier.FREE, new HashSet<>(properties.getFree()));
        features.put(SubscriptionTier.PREMIUM, new HashSet<>(properties.getPremium()));
        features.put(SubscriptionTier.ENTERPRISE, new HashSet<>(properties.getEnterprise()));
        return features;
    }

    @Getter
    @Setter
    public static class FeatureProperties {
        private Set<String> free = new HashSet<>();
        private Set<String> premium = new HashSet<>();
        private Set<String> enterprise = new HashSet<>();
    }
}