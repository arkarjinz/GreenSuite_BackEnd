package com.app.greensuitetest.service;

import com.app.greensuitetest.config.EmissionProperties;
import com.app.greensuitetest.dto.carbon.*;
import com.app.greensuitetest.exception.ValidationException;
import com.app.greensuitetest.model.CarbonActivity;
import com.app.greensuitetest.repository.CarbonActivityRepository;
import com.app.greensuitetest.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CarbonCalculatorService {
    private final EmissionProperties emissions;
    private final UnitConversionService unitConverter;
    private final CarbonActivityRepository activityRepository;
    private final SecurityUtil securityUtil;

    public double calculateFootprint(CarbonInput input) {
        return switch (input.activityType()) {
            case ELECTRICITY -> calculateElectricity(input);
            case WATER -> calculateWater(input);
            case WASTE -> calculateWaste(input);
            case FUEL -> calculateFuel(input);
        };
    }

    private double calculateElectricity(CarbonInput input) {
        double factor = emissions.getFactor("electricity", input.region());
        double footprint = input.value() * factor;
        logActivity(input, footprint, "kWh");
        return footprint;
    }

    private double calculateWater(CarbonInput input) {
        double factor = emissions.getFactor("water", input.region());
        double footprint = input.value() * factor;
        logActivity(input, footprint, "m³");
        return footprint;
    }

    private double calculateWaste(CarbonInput input) {
        double factor = switch (input.disposalMethod()) {
            case RECYCLED -> emissions.getWaste().getRecycled();
            case LANDFILLED -> emissions.getWaste().getLandfilled();
            case INCINERATED -> emissions.getWaste().getIncinerated();
        };

        double footprint = input.value() * factor;
        logActivity(input, footprint, "kg");
        return footprint;
    }

    private double calculateFuel(CarbonInput input) {
        double factor = switch (input.fuelType()) {
            case GASOLINE -> emissions.getFuel().getGasoline();
            case DIESEL -> emissions.getFuel().getDiesel();
            case NATURAL_GAS -> emissions.getFuel().getNaturalGas();
        };

        double standardAmount = input.fuelType() == FuelType.NATURAL_GAS
                ? unitConverter.toCubicMeters(input.value(), input.unit())
                : unitConverter.toLiters(input.value(), input.unit());

        double footprint = standardAmount * factor;
        logActivity(input, footprint, input.unit().name().toLowerCase());
        return footprint;
    }

    private void logActivity(CarbonInput input, double footprint, String unit) {
        CarbonActivity activity = new CarbonActivity();
        activity.setCompanyId(securityUtil.getCurrentUserCompanyId());
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
}