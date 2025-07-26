package com.app.greensuitetest.controller;

import com.app.greensuitetest.dto.carbon.CarbonInput;

import com.app.greensuitetest.model.CarbonTotal;

import com.app.greensuitetest.service.CarbonCalculatorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/api/carbon")
@RequiredArgsConstructor
public class CarbonFootPrintController {
    private final CarbonCalculatorService calculator;

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

    //Htet Htet
    @GetMapping("/company/{companyId}/years")
    public ResponseEntity<List<CarbonTotal>> getByYears(
            @PathVariable String companyId,
            @RequestParam List<String> years
    ) {
        List<CarbonTotal> data = calculator.getDataForYears(companyId, years);
        return ResponseEntity.ok(data);
    }

    @GetMapping("/company/{companyId}/month")
    public ResponseEntity<List<CarbonTotal>> getByMonth(
            @PathVariable String companyId,
            @RequestParam String year,
            @RequestParam String month
    ) {
        List<CarbonTotal> data = calculator.getDataForMonth(companyId, year, month);
        return ResponseEntity.ok(data);
    }
}