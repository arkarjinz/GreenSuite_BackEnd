//Htet Htet

package com.app.greensuitetest.validation;

import java.time.Month;

public class MonthValidator {

    public String normalizeMonth(String inputMonth) {
        try {
            // Handle numeric input (with or without leading zero)
            if (inputMonth.matches("\\d+")) {
                int monthNumber = Integer.parseInt(inputMonth);
                if (monthNumber < 1 || monthNumber > 12) {
                    throw new IllegalArgumentException("Invalid month number: " + monthNumber);
                }
                return String.format("%02d", monthNumber); // Always two digits
            }

            // Handle text month (e.g., August, AUG, aug)
            Month month = Month.valueOf(inputMonth.trim().toUpperCase());
            return String.format("%02d", month.getValue()); // Always two digits

        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid month format: " + inputMonth);
        }
    }
}






