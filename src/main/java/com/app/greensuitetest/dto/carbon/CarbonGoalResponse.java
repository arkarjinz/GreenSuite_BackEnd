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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryResult {
        private boolean isGoalMet;
        private Double reductionPercent;
        private Double remainingPercent;
    }
}