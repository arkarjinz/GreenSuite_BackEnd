package com.app.greensuitetest.service;

import com.app.greensuitetest.dto.AuthDTO;
import com.app.greensuitetest.exception.AuthException;
import com.app.greensuitetest.exception.ValidationException;
import com.app.greensuitetest.model.User;
import com.app.greensuitetest.repository.UserRepository;
import com.app.greensuitetest.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordRecoveryService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    private static final List<String> PREDEFINED_QUESTIONS = List.of(
            "What was the name of your first pet?",
            "What city were you born in?",
            "What is your mother's maiden name?",
            "What was the name of your elementary school?",
            "What was your childhood nickname?"
    );

    @Value("${app.security.max-recovery-attempts:3}")
    private int maxRecoveryAttempts;

    @Value("${app.security.recovery-lock-minutes:15}")
    private int recoveryLockMinutes;

    public List<String> getPredefinedQuestions() {
        return PREDEFINED_QUESTIONS;
    }

    public void setSecurityQuestions(String userId, AuthDTO.SecurityQuestionsRequest request) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ValidationException("User not found"));

            if (user.isGlobalAdmin()) {
                log.warn("Global admin attempted to set security questions: {}", user.getEmail());
                throw new AuthException("Global admins cannot set security questions");
            }

            if (request.questions().size() != 3) {
                throw new ValidationException("Exactly 3 security questions required");
            }

            Map<String, String> hashedQuestions = new LinkedHashMap<>();
            request.questions().forEach((question, answer) -> {
                validateQuestion(question);
                hashedQuestions.put(question, passwordEncoder.encode(answer.toLowerCase()));
            });

            user.setSecurityQuestions(hashedQuestions);
            userRepository.save(user);
        } catch (Exception ex) {
            log.error("Failed to set security questions for user: {}", userId, ex);
            throw ex;
        }
    }

    public List<String> getSecurityQuestions(String email) {
        try {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new ValidationException("User not found"));

            if(user.isGlobalAdmin()) {
                log.warn("Global admin attempted password recovery: {}", user.getEmail());
                throw new AuthException("Global admins cannot recover password with security questions");
            }

            if (user.getSecurityQuestions().isEmpty()) {
                throw new ValidationException("No security questions set for user");
            }

            return new ArrayList<>(user.getSecurityQuestions().keySet());
        } catch (Exception ex) {
            log.error("Failed to get security questions for: {}", email, ex);
            throw ex;
        }
    }

    public String verifyAnswers(AuthDTO.VerifyAnswersRequest request) {
        try {
            User user = userRepository.findByEmail(request.email())
                    .orElseThrow(() -> new ValidationException("User not found"));

            if(user.isGlobalAdmin()) {
                log.warn("Global admin attempted verification: {}", user.getEmail());
                throw new AuthException("Global admins cannot verify security questions");
            }

            if (user.isRecoveryLocked()) {
                throw new AuthException("Account temporarily locked. Try again later.");
            }

            Map<String, String> userQuestions = user.getSecurityQuestions();
            request.answers().forEach((question, answer) -> {
                String correctAnswerHash = userQuestions.get(question);
                String providedAnswer = answer.toLowerCase().trim();

                if (correctAnswerHash == null ||
                        !passwordEncoder.matches(providedAnswer, correctAnswerHash)) {
                    handleFailedAttempt(user);
                    throw new AuthException("Incorrect answer for: " + question);
                }
            });

            user.setRecoveryAttempts(0);
            userRepository.save(user);

            return jwtUtil.generateResetToken(user.getEmail());
        } catch (Exception ex) {
            log.error("Answer verification failed for: {}", request.email(), ex);
            throw ex;
        }
    }

    public void resetPassword(AuthDTO.ResetPasswordRequest request) {
        try {
            String email = jwtUtil.getEmailFromResetToken(request.resetToken());
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new AuthException("User not found"));

            if(user.isGlobalAdmin()) {
                log.warn("Global admin attempted password reset: {}", user.getEmail());
                throw new AuthException("Password reset not available for global admins");
            }

            if (!jwtUtil.validateResetToken(request.resetToken())) {
                throw new AuthException("Invalid or expired token");
            }
            user.setPassword(passwordEncoder.encode(request.newPassword()));
            userRepository.save(user);
        } catch (AuthException ex) {
            throw new AuthException("Password reset failed: " + ex.getMessage(),
                    Map.of("resetToken", request.resetToken()));
        } catch (Exception ex) {
            throw new AuthException("Password reset failed",
                    Map.of("error", ex.getMessage()));
        }
    }

    private void validateQuestion(String question) {
        if (!PREDEFINED_QUESTIONS.contains(question)) {
            throw new ValidationException("Invalid security question: " + question);
        }
    }

    private void handleFailedAttempt(User user) {
        int attempts = user.getRecoveryAttempts() + 1;
        user.setRecoveryAttempts(attempts);

        if (attempts >= maxRecoveryAttempts) {
            user.setRecoveryLockUntil(LocalDateTime.now().plusMinutes(recoveryLockMinutes));
        }
        userRepository.save(user);
    }
}