package com.app.greensuitetest.dto;

import com.app.greensuitetest.constants.Role;
import com.app.greensuitetest.validation.ValidEmail;
import com.app.greensuitetest.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record AuthDTO() {
    public record LoginRequest(
            @ValidEmail
            @NotBlank(message = "Email is required")
            String email,

            @NotBlank(message = "Password is required")
            String password
    ) {}

    public record RegisterRequest(
            @NotBlank(message = "First name is required")
            String firstName,

            @NotBlank(message = "Last name is required")
            String lastName,

            @NotBlank(message = "Username is required")
            String userName,

            @ValidEmail
            @NotBlank(message = "Email is required")
            String email,

            @ValidPassword
            @NotBlank(message = "Password is required")
            String password,

            @NotNull(message = "Company role is required")
            Role companyRole,

            // For non-owners
            //String companyId,

            @NotBlank(message = "Company name is required")
            String companyName,

            String companyAddress,
            String industry
    ) {}

    public record RefreshRequest(String refreshToken) {}

    public record ResetAdminRequest(
            @NotBlank(message = "User ID is required")
            String userId,

            @ValidPassword
            @NotBlank(message = "New password is required")
            String newPassword
    ) {}
    public record SecurityQuestionsRequest(Map<String, String> questions) {}

    public record VerifyAnswersRequest(String email, Map<String, String> answers) {}

    public record ResetPasswordRequest(
            @NotBlank(message = "Reset token is required")
            String resetToken,

            @ValidPassword
            @NotBlank(message = "New password is required")
            String newPassword
    ) {}
    public record CreateAdminRequest(
            @NotBlank(message = "First name is required")
            String firstName,

            @NotBlank(message = "Last name is required")
            String lastName,

            @NotBlank(message = "Username is required")
            String userName,

            @ValidEmail
            @NotBlank(message = "Email is required")
            String email,

            @ValidPassword
            @NotBlank(message = "Password is required")
            String password
    ) {}

    public record AddUserRequest(
            String email,
            Role role,
            String firstName,
            String lastName
    ) {}
}