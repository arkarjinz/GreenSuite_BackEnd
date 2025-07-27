//added by thuthu
package com.app.greensuitetest.service;

import com.app.greensuitetest.dto.carbon.CarbonGoalRequest;
import com.app.greensuitetest.dto.carbon.CarbonGoalResponse;
import com.app.greensuitetest.model.CarbonActivity;
import com.app.greensuitetest.model.CarbonGoal;
import com.app.greensuitetest.repository.CarbonActivityRepository;
import com.app.greensuitetest.repository.CarbonGoalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor


public class CarbonGoalService {
    private final CarbonGoalRepository carbonGoalRepository;
    private final CarbonActivityRepository carbonActivityRepo;
    //for storing data to database
    public void saveGoal(CarbonGoalRequest request, String companyId) {
        String month = request.getSelectedMonth();
        YearMonth ym = YearMonth.parse(month);
        String year = String.valueOf(ym.getYear());
        String monthValue = String.format("%02d", ym.getMonthValue());//to save month as number instead of "june"
        Optional<CarbonGoal> existing = carbonGoalRepository.findByCompanyIdAndYearAndMonth(companyId, year, monthValue);


        CarbonGoal goal = existing.orElseGet(CarbonGoal::new);
        goal.setCompanyId(companyId);
        goal.setMonth(month);
        goal.setYear(year);         // NEW
        // If you store target values individually
        goal.setTargetElectricity(request.getTargetPercentByCategory().get("electricity"));
        goal.setTargetFuel(request.getTargetPercentByCategory().get("fuel"));
        goal.setTargetWater(request.getTargetPercentByCategory().get("water"));
        goal.setTargetWaste(request.getTargetPercentByCategory().get("waste"));

        carbonGoalRepository.save(goal);
    }
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
        // convert YearMonth to String year and month matching your model
        String year = String.valueOf(month.getYear());
        // Format month to always have 2 digits, e.g. "07"
        String monthStr = String.format("%02d", month.getMonthValue());

        List<CarbonActivity> activities = carbonActivityRepo.findByYearAndMonth(year, monthStr);

        Map<String, Double> emissionsByCategory = new HashMap<>();
        for (CarbonActivity activity : activities) {
            String category = activity.getActivityType(); // should match "electricity", "fuel", etc.
            double emission = activity.getFootprint();   // use 'footprint' as carbon emission value
            emissionsByCategory.put(category,
                    emissionsByCategory.getOrDefault(category, 0.0) + emission);
        }
        return emissionsByCategory;
    }




    private double calculateReductionPercent(double current, double previous) {
        if (previous == 0) return 0.0; // Avoid division by zero
        return (1 - (current / previous)) * 100;
    }

    private String generateMessage(Map<String, CarbonGoalResponse.CategoryResult> results) {
        // Customize based on your needs
        return "Goal analysis completed.";
    }
}