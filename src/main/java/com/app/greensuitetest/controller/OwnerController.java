// OwnerController.java
package com.app.greensuitetest.controller;

import com.app.greensuitetest.dto.ApiResponse;
import com.app.greensuitetest.model.User;
import com.app.greensuitetest.service.OwnerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/owner")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class OwnerController {
    private final OwnerService ownerService;

    @GetMapping("/pending-users")
    public ResponseEntity<ApiResponse> getPendingUsers() {
        List<User> users = ownerService.getPendingUsers();
        return ResponseEntity.ok(ApiResponse.success(
                "Pending users retrieved successfully",
                users
        ));
    }
    
    @PostMapping("/approve-user/{userId}")
    public ResponseEntity<ApiResponse> approveUser(@PathVariable String userId) {
        User user = ownerService.approveUser(userId);
        return ResponseEntity.ok(ApiResponse.success(
                "User approved successfully",
                Map.of("userId", user.getId(), "email", user.getEmail())
        ));
    }

    @PostMapping("/reject-user/{userId}")
    public ResponseEntity<ApiResponse> rejectUser(@PathVariable String userId) {
        User user = ownerService.rejectUser(userId);
        return ResponseEntity.ok(ApiResponse.success(
                "User rejected successfully",
                Map.of("userId", user.getId(), "email", user.getEmail())
        ));
    }

    @DeleteMapping("/remove-user/{userId}")
    public ResponseEntity<ApiResponse> removeUser(@PathVariable String userId) {
        User user = ownerService.removeUser(userId);
        return ResponseEntity.ok(ApiResponse.success(
                "User removed from company successfully",
                Map.of("userId", user.getId(), "email", user.getEmail())
        ));
    }
}