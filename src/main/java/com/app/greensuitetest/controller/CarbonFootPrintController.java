package com.app.greensuitetest.controller;

import com.app.greensuitetest.dto.carbon.CarbonInput;
import com.app.greensuitetest.model.CarbonActivity;//htet
import com.app.greensuitetest.model.CarbonTotal;//htet
import com.app.greensuitetest.service.CarbonCalculatorService;
import com.app.greensuitetest.validation.MonthValidator;//htet
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/api/carbon")
@RequiredArgsConstructor
public class CarbonFootPrintController {
    private final CarbonCalculatorService calculator;
    private final MonthValidator monthValidator = new MonthValidator();//htet
   /* @PostMapping("/calculate")
    public ResponseEntity<Double> calculateFootprint(@Valid @RequestBody CarbonInput input) {
        return ResponseEntity.ok(calculator.calculateFootprint(input));
    }*/
    //added by thu to accept more than one activity type
    @PostMapping("/calculate")
    public ResponseEntity<Double> calculateFootprint(@Valid @RequestBody List<@Valid CarbonInput> inputs) {
        double totalFootprint = calculator.calculateAndStoreAll(inputs);
        return ResponseEntity.ok(totalFootprint);
    }


    @GetMapping("/history")
    public ResponseEntity<?> getCalculationHistory() {
        return ResponseEntity.ok(calculator.getCompanyHistory());
    }
    @GetMapping("/submitted-months")
    public ResponseEntity<?> getSubmittedMonths(
            @RequestParam int year
           ) {

        try {
            System.out.println("Fetching submitted months for year: " + year);
            List<String> submittedMonths = calculator.getSubmittedMonths(year);
            System.out.println("Submitted months: " + submittedMonths);
            return ResponseEntity.ok(submittedMonths);
        } catch (Exception e) {
          //  e.printStackTrace();
            return ResponseEntity.status(500).body("Internal server error: " + e.getMessage());
        }
    }
    // NEW: Get existing resource data for a specific month/year/region
    @GetMapping("/resource-data")
    public ResponseEntity<?> getResourceDataForMonth(
            @RequestParam String month,
            @RequestParam String year,
            @RequestParam String region) {
        try {
            System.out.println("Fetching resource data for: " + month + "/" + year + " in " + region);
            Map<String, Object> resourceData = calculator.getResourceDataForMonth(month, year, region);
            return ResponseEntity.ok(resourceData);
        } catch (Exception e) {
            System.err.println("Error fetching resource data: " + e.getMessage());
            return ResponseEntity.status(500).body("Failed to fetch resource data: " + e.getMessage());
        }
    }
    // NEW: Update existing carbon footprint data
    @PutMapping("/update")
    public ResponseEntity<?> updateFootprint(
            @Valid @RequestBody List<@Valid CarbonInput> inputs,
            @RequestParam String month,
            @RequestParam String year,
            @RequestParam String region) {
        try {
            System.out.println("Updating footprint for: " + month + "/" + year + " in " + region);
            Map<String, Object> result = calculator.updateFootprintData(inputs, month, year, region);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            System.err.println("Error updating footprint: " + e.getMessage());
            return ResponseEntity.status(500).body("Failed to update footprint: " + e.getMessage());
        }
    }

    //to show calculated result
   /* @GetMapping("/chart-data")
    public ResponseEntity<?> getChartData(
            @RequestParam String month,
            @RequestParam String year,
            @RequestParam String region) {
        try {
            Map<String, Object> chartData = calculator.getChartData(month, year, region);
            return ResponseEntity.ok(chartData);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error fetching chart data: " + e.getMessage());
        }
    }*/
    //to show calculated result
    @GetMapping("/chart-data")
    public ResponseEntity<?> getChartData(
            @RequestParam String month,
            @RequestParam String year,
            @RequestParam String region) {
        try {
            Map<String, Object> chartData = calculator.getChartData(month, year, region);
            return ResponseEntity.ok(chartData);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error fetching chart data: " + e.getMessage());
        }
    }
    //Htet Htet
    @GetMapping("/company/{companyId}/years")
    public ResponseEntity<List<CarbonTotal>> getByYears(
            @PathVariable String companyId,
            @RequestParam List<String> years
    ) {
        List<CarbonTotal> data = calculator.getDataForYears(companyId, years);
        return ResponseEntity.ok(data);
    }

    @GetMapping("/company/{companyId}/breakdown")
    public ResponseEntity<List<CarbonActivity>> getByMonth(
            @PathVariable String companyId,
            @RequestParam String year,
            @RequestParam String month
    ) {
        String formattedMonth = monthValidator.normalizeMonth(month); // This is the normalized version like "JULY"
        List<CarbonActivity> data = calculator.getDataForMonth(companyId, year, formattedMonth);
        return ResponseEntity.ok(data);
    }

}