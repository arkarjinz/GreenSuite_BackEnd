//added by thuthu
/*package com.app.greensuitetest.service;

import com.app.greensuitetest.dto.carbon.CarbonGoalRequest;
import com.app.greensuitetest.dto.carbon.CarbonGoalResponse;
import com.app.greensuitetest.model.CarbonActivity;
import com.app.greensuitetest.repository.CarbonActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CarbonGoalService {
    private final CarbonActivityRepository carbonActivityRepo;

    public CarbonGoalResponse checkGoals(CarbonGoalRequest request) {
        YearMonth currentMonth = YearMonth.parse(request.getSelectedMonth());
        YearMonth previousMonth = currentMonth.minusMonths(1);

        // Fetch emissions data for both months
        Map<String, Double> currentEmissions = getEmissionsByCategory(currentMonth);
        Map<String, Double> previousEmissions = getEmissionsByCategory(previousMonth);

        // Calculate results per category
        Map<String, CarbonGoalResponse.CategoryResult> results = new HashMap<>();
        request.getTargetPercentByCategory().forEach((category, targetPercent) -> {
            double current = currentEmissions.getOrDefault(category, 0.0);
            double previous = previousEmissions.getOrDefault(category, 0.0);
            double reductionPercent = calculateReductionPercent(current, previous);
            double remainingPercent = Math.max(0, targetPercent - reductionPercent);

            results.put(category, new CarbonGoalResponse.CategoryResult(
                    reductionPercent >= targetPercent,
                    reductionPercent,
                    remainingPercent
            ));
        });

        // Generate response message
        String message = generateMessage(results);
        return new CarbonGoalResponse(message, results);
    }

    private Map<String, Double> getEmissionsByCategory(YearMonth month) {
        // Fetch emissions data from database (grouped by category)
        return carbonActivityRepo.findEmissionsByCategoryAndMonth(
                month.getYear(),
                month.getMonthValue()
        );
    }

    private double calculateReductionPercent(double current, double previous) {
        if (previous == 0) return 0.0; // Avoid division by zero
        return (1 - (current / previous)) * 100;
    }

    private String generateMessage(Map<String, CarbonGoalResponse.CategoryResult> results) {
        // Customize based on your needs
        return "Goal analysis completed.";
    }
}*/