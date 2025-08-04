package com.app.greensuitetest.controller;

import com.app.greensuitetest.dto.AuthDTO;
import com.app.greensuitetest.dto.UserProfileDto;
import com.app.greensuitetest.model.Company;
import com.app.greensuitetest.model.User;
import com.app.greensuitetest.repository.CompanyRepository;
import com.app.greensuitetest.repository.UserRepository;
import com.app.greensuitetest.service.AdminService;
import com.app.greensuitetest.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final SecurityUtil securityUtil;
    private final PasswordEncoder passwordEncoder;

    // ============== EXISTING ADMIN FUNCTIONALITY ==============

    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deleteUser(@PathVariable String userId) {
        userRepository.deleteById(userId);
        return ResponseEntity.ok(Map.of("status", "User deleted"));
    }

    @GetMapping("/companies")
    public ResponseEntity<List<Company>> getAllCompanies() {
        return ResponseEntity.ok(companyRepository.findAll());
    }

    @DeleteMapping("/companies/{companyId}")
    public ResponseEntity<?> deleteCompany(@PathVariable String companyId) {
        userRepository.deleteByCompanyId(companyId);
        companyRepository.deleteById(companyId);
        return ResponseEntity.ok(Map.of("status", "Company deleted"));
    }

    @PostMapping("/promote-to-admin/{userId}")
    public ResponseEntity<?> promoteToAdmin(@PathVariable String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setGlobalAdmin(true);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("status", "User promoted to global admin"));
    }

    @PostMapping("/reset-admin-password")
    public ResponseEntity<?> resetAdminPassword(
            @Valid @RequestBody AuthDTO.ResetAdminRequest request) {

        User currentAdmin = securityUtil.getCurrentUser();
        if (!currentAdmin.isGlobalAdmin()) {
            return ResponseEntity.status(403).body(Map.of("error", "Requires global admin privileges"));
        }

        User targetAdmin = userRepository.findById(request.userId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!targetAdmin.isGlobalAdmin()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Target user is not a global admin"));
        }
        targetAdmin.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(targetAdmin);

        return ResponseEntity.ok(Map.of("status", "Password reset successful"));
    }

    @PostMapping("/create-admin")
    public ResponseEntity<?> createGlobalAdmin(
            @Valid @RequestBody AuthDTO.CreateAdminRequest request) {

        User currentAdmin = securityUtil.getCurrentUser();
        if (!currentAdmin.isGlobalAdmin()) {
            return ResponseEntity.status(403).body(Map.of("error", "Requires global admin privileges"));
        }

        if (userRepository.existsByEmail(request.email())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email already exists"));
        }

        User admin = User.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .userName(request.userName())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .globalAdmin(true)
                .build();

        userRepository.save(admin);
        return ResponseEntity.ok(Map.of("status", "Global admin created", "id", admin.getId()));
    }

    // ============== NEW BAN MANAGEMENT FUNCTIONALITY ==============

    @GetMapping("/banned-users")  // FIXED: Added missing @GetMapping annotation
    public ResponseEntity<List<UserProfileDto>> getAllBannedUsers() {
        return ResponseEntity.ok(adminService.getAllBannedUsers());
    }

    @GetMapping("/users-approaching-ban")
    public ResponseEntity<List<UserProfileDto>> getUsersApproachingBan() {
        return ResponseEntity.ok(adminService.getUsersApproachingBan());
    }

    @GetMapping("/ban-statistics")
    public ResponseEntity<Map<String, Object>> getBanStatistics() {
        return ResponseEntity.ok(adminService.getBanStatistics());
    }

    @PostMapping("/unban-user/{userId}")
    public ResponseEntity<UserProfileDto> unbanUser(
            @PathVariable String userId,
            @RequestBody Map<String, String> request) {

        String reason = request.getOrDefault("reason", "No reason provided");
        return ResponseEntity.ok(adminService.unbanUser(userId, reason));
    }

    @PostMapping("/ban-user/{userId}")
    public ResponseEntity<UserProfileDto> manualBanUser(
            @PathVariable String userId,
            @RequestBody Map<String, String> request) {

        String reason = request.getOrDefault("reason", "Manual ban by admin");
        return ResponseEntity.ok(adminService.manualBanUser(userId, reason));
    }

    @GetMapping("/user/{userId}/rejection-details")
    public ResponseEntity<Map<String, Object>> getUserRejectionDetails(@PathVariable String userId) {
        return ResponseEntity.ok(adminService.getUserRejectionDetails(userId));
    }
}