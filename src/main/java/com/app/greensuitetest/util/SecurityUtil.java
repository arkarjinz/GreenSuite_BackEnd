package com.app.greensuitetest.util;

import com.app.greensuitetest.model.User;
import com.app.greensuitetest.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityUtil {
    private final UserRepository userRepository;

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }

        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public String getCurrentUserCompanyId() {
        User currentUser = getCurrentUser();
        if (currentUser.getCompanyId() == null) {
            throw new RuntimeException("User is not associated with a company");
        }
        return currentUser.getCompanyId();
    }
    public String getCurrentUserId() {
        return getCurrentUser().getId(); // ✅ This line gets the user's ID
    }
}