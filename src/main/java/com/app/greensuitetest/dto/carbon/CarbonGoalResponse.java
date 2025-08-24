//added by thuthu
package com.app.greensuitetest.dto.carbon;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CarbonGoalResponse {
    private String message;
    private Map<String, CategoryResult> results;
    private boolean electricityGoalMet;
    private boolean fuelGoalMet;
    private boolean waterGoalMet;
    private boolean wasteGoalMet;

    // Constructor for backward compatibility
    public CarbonGoalResponse(String message, Map<String, CategoryResult> results) {
        this.message = message;
        this.results = results;
        // Set individual goal flags based on results
        this.electricityGoalMet = results.containsKey("electricity") ? results.get("electricity").isGoalMet() : false;
        this.fuelGoalMet = results.containsKey("fuel") ? results.get("fuel").isGoalMet() : false;
        this.waterGoalMet = results.containsKey("water") ? results.get("water").isGoalMet() : false;
        this.wasteGoalMet = results.containsKey("waste") ? results.get("waste").isGoalMet() : false;
    }
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryResult {
        private boolean isGoalMet;
        //private boolean goalMet;
        private Double reductionPercent;
        private Double remainingPercent;
        private boolean dataAvailable;


    }
    //Added by Htet Htet
    /*
    public CarbonGoalResponse(String message,
                              Map<String, CategoryResult> results,
                              boolean electricityGoalMet,
                              boolean fuelGoalMet,
                              boolean waterGoalMet,
                              boolean wasteGoalMet) {
        this.message = message;
        this.results = results;
        this.electricityGoalMet = electricityGoalMet;
        this.fuelGoalMet = fuelGoalMet;
        this.waterGoalMet = waterGoalMet;
        this.wasteGoalMet = wasteGoalMet;
    }*/
}