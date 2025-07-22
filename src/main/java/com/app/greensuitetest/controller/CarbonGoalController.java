//added by thuthu
package com.app.greensuitetest.controller;

import com.app.greensuitetest.dto.carbon.CarbonGoalRequest;
import com.app.greensuitetest.dto.carbon.CarbonGoalResponse;
import com.app.greensuitetest.service.CarbonGoalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}