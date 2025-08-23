// CarbonInput.java
package com.app.greensuitetest.dto.carbon;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CarbonInput(
        @NotNull(message = "Activity type is required")
        ActivityType activityType,

        @Positive(message = "Value must be positive")
        double value,
        @NotNull String year,

        @NotNull String month,       // Add this field
        String region,
       // String companyId,     // ❌ ADD THIS FIELD!
        String userId,//added to save who updated
        FuelType fuelType,       // Required for FUEL
        DisposalMethod disposalMethod, // Required for WASTE
        VolumeUnit unit          // Required for FUEL
) {
    public CarbonInput {
        validateInput(activityType, fuelType, disposalMethod, unit);
        validateYear(year);
    }

    private void validateInput(
            ActivityType activityType,
            FuelType fuelType,
            DisposalMethod disposalMethod,
            VolumeUnit unit
    ) {
        if (activityType == null) {
            throw new IllegalArgumentException("Activity type is required");
        }

        switch (activityType) {
            case FUEL:
                validateRequired(fuelType, "Fuel type");
                validateRequired(unit, "Unit");
                break;
            case WASTE:
                validateRequired(disposalMethod, "Disposal method");
                break;
            case ELECTRICITY:
            case WATER:
                // No additional requirements
                break;
            default:
                throw new IllegalArgumentException("Unsupported activity type: " + activityType);
        }
    }

    private void validateRequired(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required for " + activityType + " activity");
        }
    }
    private void validateYear(String year) {
        if (year == null || !year.matches("\\d{4}")) {
            throw new IllegalArgumentException("Year must be a 4-digit string, e.g. '2025'");
        }
    }
}