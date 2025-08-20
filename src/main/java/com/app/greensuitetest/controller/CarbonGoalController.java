//added by thuthu
package com.app.greensuitetest.controller;

import com.app.greensuitetest.dto.carbon.CarbonGoalRequest;
import com.app.greensuitetest.dto.carbon.CarbonGoalResponse;
import com.app.greensuitetest.service.CarbonGoalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.app.greensuitetest.model.CarbonGoal;

@RestController
@RequestMapping("/api/carbon/goals")
@RequiredArgsConstructor
public class CarbonGoalController {
    private final CarbonGoalService goalService;

    @PostMapping("/check")
    public ResponseEntity<CarbonGoalResponse> checkGoals(
            @Valid @RequestBody CarbonGoalRequest request
    ) {
        return ResponseEntity.ok(goalService.checkGoals(request));
    }


    @PostMapping("/save")
    public ResponseEntity<Void> saveGoal(@Valid @RequestBody CarbonGoalRequest request) {
        goalService.saveGoal(request); // no companyId passed manually
        return ResponseEntity.ok().build();
    }

    @GetMapping("/submittedMonths")
    public ResponseEntity<List<String>> getSubmittedMonths(@RequestParam int year) {
        System.out.println("[DEBUG] Received request for submitted months, year = " + year);
        List<String> submittedMonths = goalService.getSubmittedGoalMonths(year);
        System.out.println("[DEBUG] Returning months: " + submittedMonths);
        return ResponseEntity.ok(submittedMonths);
    }
    @GetMapping
    public ResponseEntity<List<CarbonGoal>> getGoals(
            @RequestParam(required = false) String year
    ) {
        List<CarbonGoal> goals;
        if (year != null) {
            goals = goalService.getGoalsByCompanyAndYear(year);
        } else {
            goals = goalService.getAllGoals();
        }
        return ResponseEntity.ok(goals);
    }
    // NEW: Get a single goal by ID
    @GetMapping("/{goalId}")
    public ResponseEntity<CarbonGoal> getGoalById(@PathVariable String goalId) {
        CarbonGoal goal = goalService.getGoalById(goalId);
        return ResponseEntity.ok(goal);
    }

}