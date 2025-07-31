//added by thuthu
package com.app.greensuitetest.service;
import com.app.greensuitetest.util.SecurityUtil;
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
    private final SecurityUtil securityUtil; // ✅ Inject SecurityUtil here

    //for storing data to database
    public void saveGoal(CarbonGoalRequest request) {
        String companyId = securityUtil.getCurrentUserCompanyId(); // moved here
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
        // Get emissions for current and previous months
        YearMonth currentMonth = ym;
        YearMonth previousMonth = currentMonth.minusMonths(1);

        Map<String, Double> currentEmissions = getEmissionsByCategory(currentMonth.getYear(), currentMonth.getMonthValue());
        Map<String, Double> previousEmissions = getEmissionsByCategory(previousMonth.getYear(), previousMonth.getMonthValue());

        // Calculate actual reductions and remaining reductions
        double electricityReduction = calculateReductionPercent(
                currentEmissions.getOrDefault("electricity", 0.0),
                previousEmissions.getOrDefault("electricity", 0.0));
        double fuelReduction = calculateReductionPercent(
                currentEmissions.getOrDefault("fuel", 0.0),
                previousEmissions.getOrDefault("fuel", 0.0));
        double waterReduction = calculateReductionPercent(
                currentEmissions.getOrDefault("water", 0.0),
                previousEmissions.getOrDefault("water", 0.0));
        double wasteReduction = calculateReductionPercent(
                currentEmissions.getOrDefault("waste", 0.0),
                previousEmissions.getOrDefault("waste", 0.0));
        goal.setElectricityRemaining(Math.max(0, goal.getTargetElectricity() - electricityReduction));
        goal.setFuelRemaining(Math.max(0, goal.getTargetFuel() - fuelReduction));
        goal.setWaterRemaining(Math.max(0, goal.getTargetWater() - waterReduction));
        goal.setWasteRemaining(Math.max(0, goal.getTargetWaste() - wasteReduction));

        goal.setElectricityReduction(electricityReduction);
        goal.setFuelReduction(fuelReduction);
        goal.setWaterReduction(waterReduction);
        goal.setWasteReduction(wasteReduction);
        Map<String, Boolean> individualGoalMetMap = checkCategoryGoalStatus(request);
        goal.setElectricityGoalMet(individualGoalMetMap.get("electricity"));
        goal.setFuelGoalMet(individualGoalMetMap.get("fuel"));
        goal.setWaterGoalMet(individualGoalMetMap.get("water"));
        goal.setWasteGoalMet(individualGoalMetMap.get("waste"));




        // 🟨 Optional: Automatically determine if the goal is met now

        boolean isGoalMet = checkIfGoalIsMet(request); // You'll define this method below
        goal.setIsMet(isGoalMet); // ✅ Save the result
        carbonGoalRepository.save(goal);
    }

    private Map<String, Boolean> checkCategoryGoalStatus(CarbonGoalRequest request) {
        YearMonth currentMonth = YearMonth.parse(request.getSelectedMonth());
        YearMonth previousMonth = currentMonth.minusMonths(1);

        Map<String, Double> currentEmissions = getEmissionsByCategory(currentMonth.getYear(), currentMonth.getMonthValue());
        Map<String, Double> previousEmissions = getEmissionsByCategory(previousMonth.getYear(), previousMonth.getMonthValue());

        Map<String, Boolean> result = new HashMap<>();

        for (String category : request.getTargetPercentByCategory().keySet()) {
            double target = request.getTargetPercentByCategory().getOrDefault(category, 0.0);
            if (target <= 0.0 || !previousEmissions.containsKey(category) || !currentEmissions.containsKey(category)) {
                result.put(category, null); // Not evaluable
                continue;
            }
            double reduction = calculateReductionPercent(currentEmissions.get(category), previousEmissions.get(category));
            result.put(category, reduction >= target);
        }

        return result;
    }

    //to check whether the goal is met or not and save in database
   /* private boolean checkIfGoalIsMet(CarbonGoalRequest request) {
        YearMonth currentMonth = YearMonth.parse(request.getSelectedMonth());
        YearMonth previousMonth = currentMonth.minusMonths(1);

        Map<String, Double> currentEmissions = getEmissionsByCategory(currentMonth);
        Map<String, Double> previousEmissions = getEmissionsByCategory(previousMonth);

        for (Map.Entry<String, Double> entry : previousEmissions.entrySet()) {
            String category = entry.getKey();
            double target = request.getTargetPercentByCategory().getOrDefault(category, 0.0);
            double current = currentEmissions.getOrDefault(category, 0.0);
            double previous = entry.getValue();
            double reductionPercent = calculateReductionPercent(current, previous);

            if (reductionPercent < target) {
                return false; // Not met in this category
            }
        }
        return true; // All targets met
    }*/

    private boolean checkIfGoalIsMet(CarbonGoalRequest request) {
        YearMonth currentMonth = YearMonth.parse(request.getSelectedMonth());
        YearMonth previousMonth = currentMonth.minusMonths(1);

        /*Map<String, Double> currentEmissions = getEmissionsByCategory(currentMonth);
        Map<String, Double> previousEmissions = getEmissionsByCategory(previousMonth);*/
        Map<String, Double> currentEmissions = getEmissionsByCategory(currentMonth.getYear(), currentMonth.getMonthValue());
        Map<String, Double> previousEmissions = getEmissionsByCategory(previousMonth.getYear(), previousMonth.getMonthValue());

        System.out.println("Current month: " + currentMonth);
        System.out.println("Previous month: " + previousMonth);

        System.out.println("Current emissions: " + currentEmissions);
        System.out.println("Previous emissions: " + previousEmissions);
        // If no previous data at all, assume goal is not yet evaluable
        if (previousEmissions.isEmpty()) {
            System.out.println("No previous emissions data found.");
            return false; // or return true if you prefer to treat missing data as "not failed"
        }

        // Check each category only if it exists in target and both emissions
        for (Map.Entry<String, Double> targetEntry : request.getTargetPercentByCategory().entrySet()) {
            String category = targetEntry.getKey();
            double target = targetEntry.getValue();
            // ✅ Skip categories with 0% target — user doesn't care about this category
            if (target <= 0.0) {
                continue;
            }
            System.out.println("Checking category: " + category);
            System.out.println("Target reduction %: " + target);
            if (!previousEmissions.containsKey(category) || !currentEmissions.containsKey(category)) {
                continue; // Skip this category as we can't compare
            }

            double current = currentEmissions.get(category);
            double previous = previousEmissions.get(category);
            double reductionPercent = calculateReductionPercent(current, previous);
            System.out.println("Previous emission: " + previous);
            System.out.println("Current emission: " + current);
            System.out.println("Calculated reduction %: " + reductionPercent);
            if (reductionPercent < target) {
                return false; // Goal not met in this valid category
            } else {
                System.out.println("Goal met for category: " + category);
            }
        }
        System.out.println("All checked goals met.");
        return true; // All valid categories met their targets
    }


    public CarbonGoalResponse checkGoals(CarbonGoalRequest request) {
        YearMonth currentMonth = YearMonth.parse(request.getSelectedMonth());
        YearMonth previousMonth = currentMonth.minusMonths(1);

        // Fetch emissions data for both months
       /* Map<String, Double> currentEmissions = getEmissionsByCategory(currentMonth);
        Map<String, Double> previousEmissions = getEmissionsByCategory(previousMonth);*/
        Map<String, Double> currentEmissions = getEmissionsByCategory(currentMonth.getYear(), currentMonth.getMonthValue());
        Map<String, Double> previousEmissions = getEmissionsByCategory(previousMonth.getYear(), previousMonth.getMonthValue());


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

    /* private Map<String, Double> getEmissionsByCategory(YearMonth month) {
         // convert YearMonth to String year and month matching your model
         String year = String.valueOf(month.getYear());
         // Format month to always have 2 digits, e.g. "07"
         String monthStr = String.format("%02d", month.getMonthValue());
         String companyId = securityUtil.getCurrentUserCompanyId();
         List<CarbonActivity> activities = carbonActivityRepo.findByYearAndMonth(companyId,year, monthStr);

         Map<String, Double> emissionsByCategory = new HashMap<>();
         for (CarbonActivity activity : activities) {
             String category = activity.getActivityType(); // should match "electricity", "fuel", etc.
             double emission = activity.getFootprint();   // use 'footprint' as carbon emission value
             emissionsByCategory.put(category,
                     emissionsByCategory.getOrDefault(category, 0.0) + emission);
         }
         return emissionsByCategory;
     }*/
    private Map<String, Double> getEmissionsByCategory(int year, int month) {
        String paddedMonth = String.format("%02d", month); // e.g., "04"
        String companyId = securityUtil.getCurrentUserCompanyId();

        List<CarbonActivity> emissions = carbonActivityRepo.findByCompanyIdAndYearAndMonth(companyId, String.valueOf(year), paddedMonth);

        Map<String, Double> totals = new HashMap<>();
        for (CarbonActivity emission : emissions) {
            String category = emission.getActivityType().toLowerCase(); // normalize to match request
            double value = emission.getFootprint(); // Use `footprint`

            totals.put(category, totals.getOrDefault(category, 0.0) + value);
        }

        return totals;
    }


    private double calculateReductionPercent(double current, double previous) {
        if (previous == 0) return 0.0; // Avoid division by zero
        return (1 - (current / previous)) * 100;
    }

    /*private String generateMessage(Map<String, CarbonGoalResponse.CategoryResult> results) {
        // Customize based on your needs
       // return "Goal analysis completed.";

}8/

     */
    //added to generate user friendly message for now (no ui yet)
    private String generateMessage(Map<String, CarbonGoalResponse.CategoryResult> results) {
        StringBuilder message = new StringBuilder("Goal status:\n");
        for (Map.Entry<String, CarbonGoalResponse.CategoryResult> entry : results.entrySet()) {
            String category = entry.getKey();
            boolean met = entry.getValue().isGoalMet();
            message.append(String.format("- %s goal %s\n", capitalize(category), met ? "met ✅" : "not met ❌"));
        }
        return message.toString();
    }

    private String capitalize(String input) {
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }
}