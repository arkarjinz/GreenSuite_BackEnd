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


import java.time.LocalDateTime;
import java.util.List;


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

        carbonTotalRepository.findByUserIdAndMonthAndYear(userId,companyId, month, year)
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
        activity.setUserId(securityUtil.getCurrentUserId());
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


}