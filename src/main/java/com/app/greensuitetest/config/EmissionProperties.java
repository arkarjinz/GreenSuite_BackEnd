package com.app.greensuitetest.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "emission")
@Getter
@Setter
public class EmissionProperties {
    private Map<String, Double> defaultFactors = new HashMap<>();
    private Map<String, Map<String, Double>> regionFactors = new HashMap<>();
    private Waste waste = new Waste();
    private Fuel fuel = new Fuel();

    @Getter
    @Setter
    public static class Waste {
        private double recycled;
        private double landfilled;
        private double incinerated;
    }

    @Getter
    @Setter
    public static class Fuel {
        private double gasoline;
        private double diesel;
        private double naturalGas;
    }

   /* public double getFactor(String key, String region) {
        if (region != null && regionFactors.containsKey(region)){
            Map<String, Double> regional = regionFactors.get(region);
            if (regional.containsKey(key)) {
                return regional.get(key);
            }
        }
        return defaultFactors.getOrDefault(key, 0.0);
    }*/
    //added by thu modified getting region
   public double getFactor(String key, String region) {
       if (region != null) {
           String normalizedRegion = region.toLowerCase();
           if (regionFactors.containsKey(normalizedRegion)) {
               Map<String, Double> regional = regionFactors.get(normalizedRegion);
               if (regional.containsKey(key)) {
                   return regional.get(key);
               }
           }
       }
       return defaultFactors.getOrDefault(key, 0.0);
   }

}