package com.app.greensuitetest.controller;

import com.app.greensuitetest.dto.ApiResponse;
import com.app.greensuitetest.dto.AuthDTO;
import com.app.greensuitetest.model.User;
import com.app.greensuitetest.repository.CompanyRepository;
import com.app.greensuitetest.repository.UserRepository;
import com.app.greensuitetest.security.JwtUtil;
import com.app.greensuitetest.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final CompanyRepository companyRepository;
    private final JwtUtil jwtUtil;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse> register(@Valid @RequestBody AuthDTO.RegisterRequest request) {
        User user = authService.register(request);

        // Generate tokens
        String accessToken = jwtUtil.generateAccessToken(user, user.getAuthorities());
        String refreshToken = jwtUtil.generateRefreshToken(user);

        // Create response with full user profile and tokens
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("id", user.getId());
        responseData.put("firstName", user.getFirstName());
        responseData.put("lastName", user.getLastName());
        responseData.put("userName", user.getUserName());
        responseData.put("email", user.getEmail());
        responseData.put("companyId", user.getCompanyId());
        responseData.put("companyRole", user.getCompanyRole());
        responseData.put("globalAdmin", user.isGlobalAdmin());
        responseData.put("approvalStatus", user.getApprovalStatus());

        // Add company name if available
        if (user.getCompanyId() != null) {
            companyRepository.findById(user.getCompanyId()).ifPresent(company -> {
                responseData.put("companyName", company.getName());
            });
        }

        Map<String, Object> authResponse = new HashMap<>();
        authResponse.put("accessToken", accessToken);
        authResponse.put("refreshToken", refreshToken);
        authResponse.put("user", responseData);

        return ResponseEntity.ok(ApiResponse.success(
                "Registered successfully",
                authResponse
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse> login(@Valid @RequestBody AuthDTO.LoginRequest request) {
        Map<String, Object> response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody AuthDTO.RefreshRequest request) {
        Map<String, String> tokens = authService.refreshToken(request);
        return ResponseEntity.ok(tokens);
    }

    @PostMapping("/reapply")
    public ResponseEntity<ApiResponse> reapply(@Valid @RequestBody AuthDTO.ReapplyRequest request) {
        User user = authService.reapply(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Reapplication submitted successfully",
                new AuthDTO.ReapplyResponse(
                        "pending",
                        "Your application is pending approval",
                        user.getId(),
                        LocalDateTime.now()
                )
        ));
    }
}