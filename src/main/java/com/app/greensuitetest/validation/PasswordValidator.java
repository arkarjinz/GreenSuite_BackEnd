package com.app.greensuitetest.validation;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Set;

public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {
    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password", "123456", "qwerty", "admin", "welcome"
    );
    private static final String SPECIAL_CHARS = "!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?";

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null || password.length() < 10) {
            return false;
        }

        if (COMMON_PASSWORDS.contains(password.toLowerCase())) {
            return false;
        }

        return meetsCharacterRequirements(password);
    }

    private boolean meetsCharacterRequirements(String password) {
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else if (SPECIAL_CHARS.indexOf(c) >= 0) hasSpecial = true;
        }

        return hasUpper && hasLower && hasDigit && hasSpecial;
    }
}