package com.app.greensuitetest.service;

import com.app.greensuitetest.config.EmissionProperties;
import com.app.greensuitetest.dto.carbon.*;
import com.app.greensuitetest.exception.ValidationException;
import com.app.greensuitetest.model.CarbonActivity;
import com.app.greensuitetest.repository.CarbonActivityRepository;
import com.app.greensuitetest.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import com.app.greensuitetest.repository.CarbonTotalRepository;
import com.app.greensuitetest.model.CarbonTotal;
import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
public class CarbonCalculatorService {
    private final EmissionProperties emissions;
    private final UnitConversionService unitConverter;
    private final CarbonActivityRepository activityRepository;
    private final SecurityUtil securityUtil;
    private final CarbonTotalRepository carbonTotalRepository;//added by thu to store total footprint in database
    private final CarbonActivityRepository carbonActivityRepository;

    public double calculateFootprint(CarbonInput input) {
        return switch (input.activityType()) {
            case ELECTRICITY -> calculateElectricity(input);
            case WATER -> calculateWater(input);
            case WASTE -> calculateWaste(input);
            case FUEL -> calculateFuel(input);
        };


    }

    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    //for hanlding more than one activity type
    public double calculateAndStoreAll(List<CarbonInput> inputs) {
        double totalFootprint = 0.0;
        String month = null;
        String year = null;
        for (CarbonInput input : inputs) {
            System.out.println("Calculating footprint for: " + input);
            double footprint = calculateFootprint(input);
            // saveToDatabase(input, footprint);
            logActivity(input, footprint, input.unit() != null ? input.unit().name() : null);
            totalFootprint += footprint;
            // Track month/year for saving summary
            month = input.month();
            year = input.year();
            System.out.println("Footprint calculated: " + footprint);
        }
        // Save total footprint
        if (month != null && year != null) {
            saveTotalFootprint(month, year, totalFootprint);
        }
        System.out.println("Total footprint: " + totalFootprint);
        return totalFootprint;
    }

    private void saveTotalFootprint(String month, String year, double totalFootprint) {
        String userId = securityUtil.getCurrentUserId();
        String companyId = securityUtil.getCurrentUserCompanyId();

        carbonTotalRepository.findByUserIdAndMonthAndYear(userId, companyId, month, year)
                .ifPresentOrElse(existing -> {
                    existing.setTotalFootprint(totalFootprint);
                    carbonTotalRepository.save(existing);
                }, () -> {
                    CarbonTotal newTotal = new CarbonTotal();
                    newTotal.setUserId(userId);
                    newTotal.setCompanyId(companyId);
                    newTotal.setMonth(month);
                    newTotal.setYear(year);
                    newTotal.setTotalFootprint(totalFootprint);
                    carbonTotalRepository.save(newTotal);
                });

        System.out.println("Total carbon footprint saved for " + month + "/" + year + ": " + totalFootprint);
    }


    //save to database method
/*private void saveToDatabase(CarbonInput input, double footprint) {
    System.out.println("Saving to DB: " + input.activityType() + ", Footprint: " + footprint);
    CarbonActivity activity = new CarbonActivity();

    activity.setCompanyId(securityUtil.getCurrentUserCompanyId());
    activity.setUserId(input.userId());
    activity.setActivityType(input.activityType().name());
    activity.setInputValue(input.value());
    activity.setInputUnit(input.unit()!=null?input.unit().name():null);
    activity.setFootprint(footprint);
    activity.setRegion(input.region());
    activity.setFuelType(input.fuelType()!=null?input.fuelType().name():null);
    activity.setDisposalMethod(input.disposalMethod()!=null?input.disposalMethod().name():null);
    activity.setTimestamp(LocalDateTime.now());

    activityRepository.save(activity);
}*/


    private double calculateElectricity(CarbonInput input) {
        double factor = emissions.getFactor("electricity", input.region());
        System.out.println("Electricity factor for region " + input.region() + " = " + factor);

        double footprint = input.value() * factor;
        //  logActivity(input, footprint, "kWh");
        //return footprint;
        return roundToTwoDecimals(footprint);
    }

    private double calculateWater(CarbonInput input) {
        double factor = emissions.getFactor("water", input.region());
        double footprint = input.value() * factor;
        // logActivity(input, footprint, "m³");
        // return footprint;
        return roundToTwoDecimals(footprint);
    }

    private double calculateWaste(CarbonInput input) {
        /*double factor = switch (input.disposalMethod()) {
            case RECYCLED -> emissions.getWaste().getRecycled();
            case LANDFILLED -> emissions.getWaste().getLandfilled();
            case INCINERATED -> emissions.getWaste().getIncinerated();
        };*/
        double factor = switch (input.disposalMethod()) {
            case RECYCLED -> emissions.getFactor("waste.recycled", input.region());
            case LANDFILLED -> emissions.getFactor("waste.landfilled", input.region());
            case INCINERATED -> emissions.getFactor("waste.incinerated", input.region());
        };
        System.out.println("Factor used: " + emissions.getFactor("waste.recycled", input.region()));
        System.out.println("Factor used: " + emissions.getFactor("waste.landfilled", input.region()));
        System.out.println("Factor used: " + emissions.getFactor("waste.incinerated", input.region()));
        double footprint = input.value() * factor;
        //logActivity(input, footprint, "kg");
        //return footprint;
        return roundToTwoDecimals(footprint);
    }

    private double calculateFuel(CarbonInput input) {
        /*double factor = switch (input.fuelType()) {
            case GASOLINE -> emissions.getFuel().getGasoline();
            case DIESEL -> emissions.getFuel().getDiesel();
            case NATURAL_GAS -> emissions.getFuel().getNaturalGas();
        };*/
        //added by thu modified since they are giving 0
        double factor = switch (input.fuelType()) {
            case GASOLINE -> emissions.getFactor("fuel.gasoline", input.region());
            case DIESEL -> emissions.getFactor("fuel.diesel", input.region());
            case NATURALGAS -> emissions.getFactor("fuel.natural-gas", input.region());
        };
        System.out.println("Factor used: " + emissions.getFactor("fuel.gasoline", input.region()));
        System.out.println("Factor used: " + emissions.getFactor("fuel.diesel", input.region()));
        System.out.println("Factor used: " + emissions.getFactor("fuel.natural-gas", input.region()));

        double standardAmount = input.fuelType() == FuelType.NATURALGAS
                ? unitConverter.toCubicMeters(input.value(), input.unit())
                : unitConverter.toLiters(input.value(), input.unit());

        double footprint = standardAmount * factor;
        //  logActivity(input, footprint, input.unit().name().toLowerCase());
        //return footprint;
        return roundToTwoDecimals(footprint);
    }

    private void logActivity(CarbonInput input, double footprint, String unit) {
        CarbonActivity activity = new CarbonActivity();
        activity.setCompanyId(securityUtil.getCurrentUserCompanyId());
        //activity.setUserId(securityUtil.getCurrentUserId());
        activity.setUserId(input.userId()); // ✅ Use the userId from the input
        activity.setMonth(input.month()); // expects String like "07"
        activity.setYear(input.year());   // expects String like "2025"


        activity.setActivityType(input.activityType().name());
        activity.setInputValue(input.value());
        activity.setInputUnit(unit);
        activity.setFootprint(footprint);
        activity.setRegion(input.region());
        activity.setTimestamp(LocalDateTime.now());

        if (input.activityType() == ActivityType.FUEL) {
            activity.setFuelType(input.fuelType().name());
        } else if (input.activityType() == ActivityType.WASTE) {
            activity.setDisposalMethod(input.disposalMethod().name());
        }

        activityRepository.save(activity);
    }

    public List<CarbonActivity> getCompanyHistory() {
        String companyId = securityUtil.getCurrentUserCompanyId();
        return activityRepository.findByCompanyId(companyId);
    }

    public List<String> getSubmittedMonths(int year) {
        String companyId = securityUtil.getCurrentUserCompanyId();
        List<CarbonActivity> activities = carbonActivityRepository.findByCompanyIdAndYearReturnMonths(companyId, String.valueOf(year));
        return activities.stream()
                .map(CarbonActivity::getMonth)
                .distinct()
                .toList();
    }

    // Get existing resource data for a specific month/year/region for editing

    public Map<String, Object> getResourceDataForMonth(String month, String year, String region) {
        String companyId = securityUtil.getCurrentUserCompanyId();
        String userId = securityUtil.getCurrentUserId();

        System.out.println("Fetching resource data for company: " + companyId + ", month: " + month + ", year: " + year + ", region: " + region);

        // Get all activities for this company/user for the specified month/year/region
        List<CarbonActivity> activities = activityRepository.findByCompanyIdAndUserIdAndMonthAndYearAndRegion(
                companyId,  month, year, region
        );

        Map<String, Object> resourceData = new HashMap<>();

        // Set default values
        resourceData.put("month", month);
        resourceData.put("year", year);
        resourceData.put("region", region);
        resourceData.put("companyId", companyId);
        resourceData.put("userId", userId);

        // Process each activity and populate the resource data
        for (CarbonActivity activity : activities) {
            ActivityType activityType = ActivityType.valueOf(activity.getActivityType());

            switch (activityType) {
                case ELECTRICITY:
                    resourceData.put("electricity", activity.getInputValue());
                    break;

                case WATER:
                    resourceData.put("water", activity.getInputValue());
                    break;

                case FUEL:
                    resourceData.put("fuel", activity.getInputValue());
                    if (activity.getFuelType() != null) {
                        // Convert from uppercase enum to lowercase for frontend
                        resourceData.put("fuelType", activity.getFuelType().toLowerCase().replace("naturalgas", "naturalGas"));
                    }
                    if (activity.getInputUnit() != null) {
                        resourceData.put("unit", activity.getInputUnit());
                    }
                    break;

                case WASTE:
                    resourceData.put("waste", activity.getInputValue());
                    if (activity.getDisposalMethod() != null) {
                        resourceData.put("disposalMethod", activity.getDisposalMethod().toLowerCase());
                    }
                    break;
            }
        }

        System.out.println("Retrieved resource data: " + resourceData);
        return resourceData;
    }

    /**
     * Update existing carbon footprint data for a specific month/year/region
     */
    /*public Map<String, Object> updateFootprintData(List<CarbonInput> inputs, String month, String year, String region) {
        String companyId = securityUtil.getCurrentUserCompanyId();
        String userId = securityUtil.getCurrentUserId();
        if (companyId == null || userId == null) {
            throw new IllegalStateException("User not authenticated");
        }
        System.out.println("Updating footprint data for company: " + companyId + ", month: " + month + ", year: " + year + ", region: " + region);

        // Delete existing activities for this month/year/region combination
        List<CarbonActivity> existingActivities = activityRepository.findByCompanyIdAndUserIdAndMonthAndYearAndRegion(
                companyId, userId, month, year, region
        );

        if (!existingActivities.isEmpty()) {
            activityRepository.deleteAll(existingActivities);
            System.out.println("Deleted " + existingActivities.size() + " existing activities");
        }

        // Calculate and store new activities
        double totalFootprint = calculateAndStoreAll(inputs);

        // Prepare response
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Footprint data updated successfully");
        response.put("totalFootprint", totalFootprint);
        response.put("updatedRecords", inputs.size());
        response.put("month", month);
        response.put("year", year);
        response.put("region", region);

        System.out.println("Update completed. New total footprint: " + totalFootprint);
        return response;
    }*/
    /*@Transactional
    public Map<String, Object> updateFootprintData(List<CarbonInput> inputs, String month, String year, String region) {
        // 1. Auth & validation (keep existing)
        String companyId = securityUtil.getCurrentUserCompanyId();
        String userId = securityUtil.getCurrentUserId();
        if (companyId == null || userId == null) {
            throw new IllegalStateException("User not authenticated");
        }

        // 2. Delete existing records (keep existing)
        List<CarbonActivity> existingActivities = activityRepository.findByCompanyIdAndUserIdAndMonthAndYearAndRegion(
                companyId, userId, month, year, region
        );
        if (!existingActivities.isEmpty()) {
            activityRepository.deleteAll(existingActivities);
        }

        // 3. CALCULATE ONLY (modified)
        double totalFootprint = 0.0;
        List<CarbonActivity> newActivities = new ArrayList<>();

        for (CarbonInput input : inputs) {
            // Use calculateFootprint() instead of calculateAndStoreAll()
            double footprint = calculateFootprint(input);
            totalFootprint += footprint;

            // Manually create activity WITHOUT auto-saving
            CarbonActivity activity = new CarbonActivity();
            activity.setCompanyId(companyId);
            activity.setUserId(userId);
            activity.setMonth(input.month());
            activity.setYear(input.year());
            activity.setActivityType(input.activityType().name());
            activity.setInputValue(input.value());
            activity.setInputUnit(input.unit() != null ? input.unit().name() : null);
            activity.setFootprint(footprint);
            activity.setRegion(input.region());
            activity.setTimestamp(LocalDateTime.now());

            if (input.activityType() == ActivityType.FUEL) {
                activity.setFuelType(input.fuelType().name());
            } else if (input.activityType() == ActivityType.WASTE) {
                activity.setDisposalMethod(input.disposalMethod().name());
            }

            newActivities.add(activity);
        }

        // 4. BATCH SAVE (new)
        activityRepository.saveAll(newActivities);

        // 5. Update total (keep existing)
        saveTotalFootprint(month, year, totalFootprint);

        // Return response (keep existing)
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("totalFootprint", totalFootprint);
        response.put("updatedRecords", inputs.size());
        return response;
    }


     */
    @Transactional
    public Map<String, Object> updateFootprintData(List<CarbonInput> inputs, String month, String year, String region) {
        // 1. Auth & delete old records (keep this part exactly as-is)
        String companyId = securityUtil.getCurrentUserCompanyId();
        String userId = securityUtil.getCurrentUserId();
        List<CarbonActivity> existingActivities = activityRepository.findByCompanyIdAndUserIdAndMonthAndYearAndRegion(
                companyId, month, year, region
        );
        if (!existingActivities.isEmpty()) {
            activityRepository.deleteAll(existingActivities);
        }

        // 2. Use calculateAndStoreAll BUT prevent auto-save
        double totalFootprint = 0.0;
        List<CarbonActivity> activitiesToSave = new ArrayList<>();

        for (CarbonInput input : inputs) {
            double footprint = calculateFootprint(input); // Pure calculation
            totalFootprint += footprint;

            // Manually build activities (bypass logActivity)
            CarbonActivity activity = new CarbonActivity();
            activity.setCompanyId(companyId);
            activity.setUserId(userId);
            activity.setMonth(input.month());
            activity.setYear(year);
            activity.setActivityType(input.activityType().name());
            activity.setInputValue(input.value());  // Make sure this is set!
            activity.setFootprint(footprint);       // Make sure this is set!
            activity.setRegion(region);
            activity.setTimestamp(LocalDateTime.now());
            // ... set all other fields ...
            // Handle activity-specific fields
            if (input.activityType() == ActivityType.FUEL && input.fuelType() != null) {
                activity.setFuelType(input.fuelType().name());
                activity.setInputUnit(input.unit() != null ? input.unit().name() : "LITERS");
            }
            else if (input.activityType() == ActivityType.WASTE && input.disposalMethod() != null) {
                activity.setDisposalMethod(input.disposalMethod().name());
                activity.setInputUnit("kg"); // Default unit for waste
            }
            else {
                // Set default units for electricity and water
                activity.setInputUnit(
                        input.activityType() == ActivityType.ELECTRICITY ? "kWh" : "LITERS"
                );
            }
            activitiesToSave.add(activity);
        }

        // 3. Single save operation
        activityRepository.saveAll(activitiesToSave);

        // 4. Update total (unchanged)
        saveTotalFootprint(month, year, totalFootprint);

        // Return response (unchanged)
        return Map.of(
                "success", true,
                "totalFootprint", totalFootprint,
                "updatedRecords", inputs.size()
        );
    }
    //Htet Htet
    public List<CarbonTotal> getDataForYears(String companyId, List<String> years) {
        return carbonTotalRepository.findByCompanyIdAndYearIn(companyId, years);
    }


    public List<CarbonActivity> getDataForMonth(String companyId, String year, String month) {
        return activityRepository.findByCompanyIdAndYearAndMonth(companyId, year, month);
    }
    //to show result for calculated footprint
    /*public Map<String, Object> getChartData(String month, String year, String region) {
        String companyId = securityUtil.getCurrentUserCompanyId();
        String userId = securityUtil.getCurrentUserId();

        // Get all activities for this month/year/region
        List<CarbonActivity> activities = activityRepository.findByCompanyIdAndUserIdAndMonthAndYearAndRegion(
                companyId, month, year, region
        );

        // Calculate totals by category
        double electricityTotal = 0;
        double waterTotal = 0;
        double fuelTotal = 0;
        double wasteTotal = 0;

        for (CarbonActivity activity : activities) {
            switch (ActivityType.valueOf(activity.getActivityType())) {
                case ELECTRICITY -> electricityTotal += activity.getFootprint();
                case WATER -> waterTotal += activity.getFootprint();
                case FUEL -> fuelTotal += activity.getFootprint();
                case WASTE -> wasteTotal += activity.getFootprint();
            }
        }

        double totalFootprint = electricityTotal + waterTotal + fuelTotal + wasteTotal;

        // Prepare data for charts
        Map<String, Object> chartData = new HashMap<>();

        // Pie chart data
        List<Map<String, Object>> pieData = List.of(
                Map.of("name", "Electricity", "value", electricityTotal),
                Map.of("name", "Water", "value", waterTotal),
                Map.of("name", "Fuel", "value", fuelTotal),
                Map.of("name", "Waste", "value", wasteTotal)
        );

        // Bar chart data
        List<Map<String, Object>> barData = List.of(
                Map.of("category", "Electricity", "value", electricityTotal),
                Map.of("category", "Water", "value", waterTotal),
                Map.of("category", "Fuel", "value", fuelTotal),
                Map.of("category", "Waste", "value", wasteTotal)
        );

        chartData.put("pieData", pieData);
        chartData.put("barData", barData);
        chartData.put("totalFootprint", totalFootprint);
        chartData.put("month", month);
        chartData.put("year", year);
        chartData.put("region", region);

        return chartData;
    }*/
    //to show result for calculated footprint
    public Map<String, Object> getChartData(String month, String year, String region) {
        String companyId = securityUtil.getCurrentUserCompanyId();
        String userId = securityUtil.getCurrentUserId();

        // Get all activities for this month/year/region
        List<CarbonActivity> activities = activityRepository.findByCompanyIdAndUserIdAndMonthAndYearAndRegion(
                companyId, month, year, region
        );

        // Calculate totals by category
        double electricityTotal = 0;
        double waterTotal = 0;
        double fuelTotal = 0;
        double wasteTotal = 0;

        for (CarbonActivity activity : activities) {
            switch (ActivityType.valueOf(activity.getActivityType())) {
                case ELECTRICITY -> electricityTotal += activity.getFootprint();
                case WATER -> waterTotal += activity.getFootprint();
                case FUEL -> fuelTotal += activity.getFootprint();
                case WASTE -> wasteTotal += activity.getFootprint();
            }
        }

        double totalFootprint = electricityTotal + waterTotal + fuelTotal + wasteTotal;

        // Prepare data for charts
        Map<String, Object> chartData = new HashMap<>();

        // Pie chart data
        List<Map<String, Object>> pieData = List.of(
                Map.of("name", "Electricity", "value", electricityTotal),
                Map.of("name", "Water", "value", waterTotal),
                Map.of("name", "Fuel", "value", fuelTotal),
                Map.of("name", "Waste", "value", wasteTotal)
        );

        // Bar chart data
        List<Map<String, Object>> barData = List.of(
                Map.of("category", "Electricity", "value", electricityTotal),
                Map.of("category", "Water", "value", waterTotal),
                Map.of("category", "Fuel", "value", fuelTotal),
                Map.of("category", "Waste", "value", wasteTotal)
        );

        chartData.put("pieData", pieData);
        chartData.put("barData", barData);
        chartData.put("totalFootprint", totalFootprint);
        chartData.put("month", month);
        chartData.put("year", year);
        chartData.put("region", region);

        return chartData;
    }


}