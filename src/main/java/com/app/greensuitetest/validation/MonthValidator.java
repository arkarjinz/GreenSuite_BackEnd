//Htet Htet

package com.app.greensuitetest.validation;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;

public class MonthValidator {

    public String normalizeMonth(String inputMonth) {
        try {
            // Handle numeric input: "07" => 7 => JULY
            if (inputMonth.matches("\\d+")) {
                int monthNumber = Integer.parseInt(inputMonth);
                return Month.of(monthNumber).name(); // returns "JULY"
            }

            // Handle string input: "july", "Jul", "JuLy" => JULY
            return Month.valueOf(inputMonth.trim().toUpperCase()).name();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid month format: " + inputMonth);
        }
    }
}




