package com.app.greensuitetest.validation;

import jakarta.validation.ConstraintValidator;

import java.util.regex.Pattern;

public class EmailValidator implements ConstraintValidator<ValidEmail, String> {
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[\\w-.]+@([\\w-]+\\.)+[\\w-]{2,4}$");

    @Override
    public boolean isValid(String email, jakarta.validation.ConstraintValidatorContext context) {
        if (email == null || email.isBlank()) {
            return false; // Email is required
        }
        return EMAIL_PATTERN.matcher(email).matches(); // Validate email format
    }
}
