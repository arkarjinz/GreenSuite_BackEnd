package com.app.greensuitetest.controller;

import com.app.greensuitetest.dto.ApiResponse;
import com.app.greensuitetest.dto.AuthDTO;
import com.app.greensuitetest.model.User;
import com.app.greensuitetest.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse> register(@Valid @RequestBody AuthDTO.RegisterRequest request) {
        User user = authService.register(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Registered successfully",
                Map.of(
                        "id", user.getId(),
                        "userName", user.getUserName(),
                        "email", user.getEmail(),
                        "globalAdmin", user.isGlobalAdmin()
                )
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
}