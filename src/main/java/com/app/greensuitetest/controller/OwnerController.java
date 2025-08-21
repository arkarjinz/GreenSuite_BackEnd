package com.app.greensuitetest.controller;

import com.app.greensuitetest.dto.UserProfileDto;
import com.app.greensuitetest.service.OwnerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/owner")
@PreAuthorize("hasRole('OWNER')")
@RequiredArgsConstructor
public class OwnerController {
    private final OwnerService ownerService;

    @GetMapping("/pending-users")
    public ResponseEntity<List<UserProfileDto>> getPendingUsers() {
        return ResponseEntity.ok(ownerService.getPendingUsers());
    }

    @PostMapping("/approve-user/{userId}")
    public ResponseEntity<UserProfileDto> approveUser(@PathVariable String userId) {
        return ResponseEntity.ok(ownerService.approveUser(userId));
    }

    @PostMapping("/reject-user/{userId}")
    public ResponseEntity<Map<String, Object>> rejectUser(
            @PathVariable String userId,
            @RequestParam(value = "reason", required = false, defaultValue = "No reason provided") String reason) {

        return ResponseEntity.ok(ownerService.rejectUser(userId, reason));
    }

    @GetMapping("/user/{userId}/rejection-history")
    public ResponseEntity<List<Map<String, Object>>> getUserRejectionHistory(@PathVariable String userId) {
        return ResponseEntity.ok(ownerService.getUserRejectionHistory(userId));
    }

    @GetMapping("/company-stats")
    public ResponseEntity<Map<String, Object>> getCompanyStats() {
        // This could include stats about pending users, rejection rates, etc.
        return ResponseEntity.ok(ownerService.getCompanyStats());
    }
}