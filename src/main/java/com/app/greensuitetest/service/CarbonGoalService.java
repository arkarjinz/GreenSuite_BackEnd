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
import java.time.LocalDateTime;
@Service
@RequiredArgsConstructor


public class CarbonGoalService {
    private final CarbonGoalRepository carbonGoalRepository;
    private final CarbonActivityRepository carbonActivityRepo;
    private final SecurityUtil securityUtil; // ✅ Inject SecurityUtil here

    //for storing data to database
    public void saveGoal(CarbonGoalRequest request) {
        String companyId = securityUtil.getCurrentUserCompanyId(); // moved here
        String userId = securityUtil.getCurrentUserId(); // ← ADD THIS LINE
        //String month = request.getSelectedMonth();
        YearMonth ym = YearMonth.parse(request.getSelectedMonth());

        //YearMonth ym = YearMonth.parse(month);
        String year = String.valueOf(ym.getYear());
        String monthValue = String.format("%02d", ym.getMonthValue());//to save month as number instead of "june"
        Optional<CarbonGoal> existing = carbonGoalRepository.findByCompanyIdAndYearAndMonth(companyId, year, monthValue);


        CarbonGoal goal = existing.orElseGet(CarbonGoal::new);
        goal.setCompanyId(companyId);
        goal.setUserId(userId); // ← ADD THIS LINE
        goal.setMonth(monthValue);
        goal.setYear(year);         // NEW
        // ✅ ADD TIMESTAMPS HERE
        if (goal.getCreatedAt() == null) {
            goal.setCreatedAt(LocalDateTime.now());
        }
        goal.setUpdatedAt(LocalDateTime.now());
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
        Map<String, Boolean> result = new HashMap<>();
        if (currentEmissions.isEmpty()) {
            System.out.println("No current month emissions data found for " + currentMonth + ". Skipping save.");
            return;
        }
        if (previousEmissions.isEmpty()) {
            System.out.println("No previous emissions data found. Skipping save.");
            return;
        }
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
       /* goal.setElectricityRemaining(Math.max(0, goal.getTargetElectricity() - electricityReduction));
        goal.setFuelRemaining(Math.max(0, goal.getTargetFuel() - fuelReduction));
        goal.setWaterRemaining(Math.max(0, goal.getTargetWater() - waterReduction));
        goal.setWasteRemaining(Math.max(0, goal.getTargetWaste() - wasteReduction));*/
        goal.setElectricityRemaining(roundTo2Decimals(Math.max(0, goal.getTargetElectricity() - electricityReduction)));
        goal.setFuelRemaining(roundTo2Decimals(Math.max(0, goal.getTargetFuel() - fuelReduction)));
        goal.setWaterRemaining(roundTo2Decimals(Math.max(0, goal.getTargetWater() - waterReduction)));
        goal.setWasteRemaining(roundTo2Decimals(Math.max(0, goal.getTargetWaste() - wasteReduction)));


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
    public List<String> getSubmittedGoalMonths(int year) {
        String companyId = securityUtil.getCurrentUserCompanyId();
        String yearStr = String.valueOf(year);
        List<CarbonGoal> goals = carbonGoalRepository.findByCompanyIdAndYear(companyId,yearStr);
        List<String> submittedMonths = goals.stream()
                .map(CarbonGoal::getMonth) // e.g., "06"
                .distinct()
                .toList();

        System.out.println("[DEBUG] Fetched goal months for companyId=" + companyId + ", year=" + year + " → " + submittedMonths);

        return submittedMonths;
        //return goals.stream()
               // .filter(goal -> String.valueOf(year).equals(goal.getYear()))
                //.map(goal -> String.format("%s-%s", goal.getYear(), goal.getMonth()))
               // .toList();
               // .map(goal -> goal.getYear() + "-" + String.format("%02d", Integer.parseInt(goal.getMonth())))
               // .distinct()
               // .toList();

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
        // ✅ NEW: Check if current month has emission data
        if (currentEmissions.isEmpty()) {
            System.out.println("No current month emissions data found for " + currentMonth + ". Skipping save.");
            return false; // EXITS WITHOUT SAVING THE GOAL
        }

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
           // System.out.println("Calculated remaining%: " +  remainingPercent);
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


       /* Map<String, Double> currentEmissions = getEmissionsByCategory(currentMonth);
        Map<String, Double> previousEmissions = getEmissionsByCategory(previousMonth);*/
        // Fetch emissions data for both months
        Map<String, Double> currentEmissions = getEmissionsByCategory(currentMonth.getYear(), currentMonth.getMonthValue());
        Map<String, Double> previousEmissions = getEmissionsByCategory(previousMonth.getYear(), previousMonth.getMonthValue());

        if (currentEmissions.isEmpty()) {
            String message = "No data found for the selected month (" + currentMonth + "). Please add emissions data for " + currentMonth + " before setting goals.";
            return new CarbonGoalResponse(message, new HashMap<>());
        }
        // If no previous month data, return a message asking user to add it
        if (previousEmissions.isEmpty()) {
            String message = "No data found for the previous month (" + previousMonth + "). Please add emissions data for the previous month to evaluate your goals.";
            return new CarbonGoalResponse(message, new HashMap<>());
        }

        // Calculate results per category
        Map<String, CarbonGoalResponse.CategoryResult> results = new HashMap<>();
       /* request.getTargetPercentByCategory().forEach((category, targetPercent) -> {
            double current = currentEmissions.getOrDefault(category, 0.0);
            double previous = previousEmissions.getOrDefault(category, 0.0);
            double reductionPercent = calculateReductionPercent(current, previous);
            double remainingPercent = Math.max(0, targetPercent - reductionPercent);

            results.put(category, new CarbonGoalResponse.CategoryResult(
                    reductionPercent >= targetPercent,
                    reductionPercent,
                    remainingPercent
            ));
        });*/

        request.getTargetPercentByCategory().forEach((category, targetPercent) -> {
            boolean dataAvailable = previousEmissions.containsKey(category) && currentEmissions.containsKey(category);

            if (!dataAvailable) {
                // No emissions data → cannot evaluate
                results.put(category, new CarbonGoalResponse.CategoryResult(
                        false,
                        0.0,
                        0.0,
                        false // ⬅️ dataAvailable
                ));
            } else {
                double current = currentEmissions.get(category);
                double previous = previousEmissions.get(category);
                double reductionPercent = calculateReductionPercent(current, previous);
                double remainingPercent = Math.max(0, targetPercent - reductionPercent);

                results.put(category, new CarbonGoalResponse.CategoryResult(
                        reductionPercent >= targetPercent,
                        reductionPercent,
                        remainingPercent,
                        true // ⬅️ dataAvailable
                ));
            }
        });

        String message="";
        return new CarbonGoalResponse(message, results);

        // Generate response message
       // String message = generateMessage(results);
      //  return new CarbonGoalResponse(message, results);
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
        double reduction=(1-(current/previous))*100;
        //return (1 - (current / previous)) * 100;
        return Math.round(reduction*100.0)/100.0;
    }
    private double roundTo2Decimals(double value) {
        return Math.round(value * 100.0) / 100.0;
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
    public List<CarbonGoal> getGoalsByCompanyAndYear(String year) {
        String companyId = securityUtil.getCurrentUserCompanyId();
        return carbonGoalRepository.findByCompanyIdAndYear(companyId, year);
    }

    public List<CarbonGoal> getAllGoals() {
        String companyId = securityUtil.getCurrentUserCompanyId();
        return carbonGoalRepository.findByCompanyId(companyId);
    }


    private String capitalize(String input) {
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }
    // Add this method to your CarbonGoalService.java class

    public CarbonGoal getGoalById(String goalId) {
        String companyId = securityUtil.getCurrentUserCompanyId();
        CarbonGoal goal = carbonGoalRepository.findById(goalId)
                .orElseThrow(() -> new RuntimeException("Goal not found with id: " + goalId));

        // Security check: ensure the goal belongs to the current user's company
        if (!goal.getCompanyId().equals(companyId)) {
            throw new RuntimeException("Access denied: Goal does not belong to your company");
        }

        return goal;
    }
    //Added by Htet Htet
    public CarbonGoalResponse getMonthlyGoal(String month, String year) {
        String companyId = securityUtil.getCurrentUserCompanyId();
        Optional<CarbonGoal> goalOpt = carbonGoalRepository.findByCompanyIdAndMonthAndYear(
                companyId, month, year);

        if (goalOpt.isEmpty()) {
            return new CarbonGoalResponse("No goal found for " + getMonthName(month) + " " + year, new HashMap<>());
        }

        CarbonGoal goal = goalOpt.get();
        Map<String, CarbonGoalResponse.CategoryResult> results = new HashMap<>();

        // Electricity
        if (goal.getTargetElectricity() != null && goal.getTargetElectricity() > 0) {
            results.put("electricity", new CarbonGoalResponse.CategoryResult(
                    goal.getElectricityGoalMet(),
                    goal.getElectricityReduction(),
                    goal.getElectricityRemaining(),
                    true
            ));
        }

        // Fuel
        if (goal.getTargetFuel() != null && goal.getTargetFuel() > 0) {
            results.put("fuel", new CarbonGoalResponse.CategoryResult(
                    goal.getFuelGoalMet(),
                    goal.getFuelReduction(),
                    goal.getFuelRemaining(),
                    true
            ));
        }

        // Water
        if (goal.getTargetWater() != null && goal.getTargetWater() > 0) {
            results.put("water", new CarbonGoalResponse.CategoryResult(
                    goal.getWaterGoalMet(),
                    goal.getWaterReduction(),
                    goal.getWaterRemaining(),
                    true
            ));
        }

        // Waste
        if (goal.getTargetWaste() != null && goal.getTargetWaste() > 0) {
            results.put("waste", new CarbonGoalResponse.CategoryResult(
                    goal.getWasteGoalMet(),
                    goal.getWasteReduction(),
                    goal.getWasteRemaining(),
                    true
            ));
        }

        return new CarbonGoalResponse(
                "Goal summary for " + getMonthName(month) + " " + year,
                results
        );
    }

    // Helper method to get month name
    private String getMonthName(String month) {
        String[] monthNames = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};
        try {
            int monthNum = Integer.parseInt(month);
            return monthNames[monthNum - 1];
        } catch (Exception e) {
            return "Month";
        }
    }
}