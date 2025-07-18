package com.app.greensuitetest.service;

import com.app.greensuitetest.dto.carbon.VolumeUnit;
import org.springframework.stereotype.Service;

@Service
public class UnitConversionService {
    public double toLiters(double value, VolumeUnit unit) {
        return switch (unit) {
            case LITERS -> value;
            case GALLONS -> value * 3.78541;
            case CUBIC_METERS -> value * 1000;
        };
    }

    public double toCubicMeters(double value, VolumeUnit unit) {
        return switch (unit) {
            case CUBIC_METERS -> value;
            case LITERS -> value / 1000;
            case GALLONS -> value * 0.00378541;
        };
    }
}