package com.app.greensuitetest.controller;

import com.app.greensuitetest.dto.AuthDTO;
import com.app.greensuitetest.service.PasswordRecoveryService;
import com.app.greensuitetest.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/recovery")
@RequiredArgsConstructor
public class PasswordRecoveryController {
    private final PasswordRecoveryService recoveryService;
    private final SecurityUtil securityUtil;

    @GetMapping("/predefined-questions")
    public ResponseEntity<?> getPredefinedQuestions() {
        List<String> questions = recoveryService.getPredefinedQuestions();
        return ResponseEntity.ok(Map.of("questions", questions));
    }

    @PostMapping("/set-questions")
    public ResponseEntity<?> setSecurityQuestions(
            @RequestBody AuthDTO.SecurityQuestionsRequest request) {
        String userId = securityUtil.getCurrentUser().getId();
        recoveryService.setSecurityQuestions(userId, request);
        return ResponseEntity.ok(Map.of("status", "questions_set"));
    }

    @GetMapping("/forgot-password/{email}")
    public ResponseEntity<?> getSecurityQuestions(@PathVariable String email) {
        List<String> questions = recoveryService.getSecurityQuestions(email);
        return ResponseEntity.ok(Map.of("questions", questions));
    }

    @PostMapping("/verify-answers")
    public ResponseEntity<?> verifyAnswers(@RequestBody AuthDTO.VerifyAnswersRequest request) {
        String token = recoveryService.verifyAnswers(request);
        return ResponseEntity.ok(Map.of("status", "verified", "resetToken", token));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(
            @Valid @RequestBody AuthDTO.ResetPasswordRequest request) {
        recoveryService.resetPassword(request);
        return ResponseEntity.ok(Map.of("status", "password_reset"));
    }
}