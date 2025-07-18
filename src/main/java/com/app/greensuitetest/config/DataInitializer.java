package com.app.greensuitetest.config;

import com.app.greensuitetest.model.User;
import com.app.greensuitetest.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
@RequiredArgsConstructor
public class DataInitializer {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostConstruct
    public void init() {
        if (!userRepository.existsByEmail("admin@green.com")) {
            User admin = User.builder()
                    .firstName("System")
                    .lastName("Admin")
                    .userName("sysadmin")
                    .email("admin@green.com")
                    .password(passwordEncoder.encode("AdminPass123!"))
                    .globalAdmin(true)
                    .build();

            userRepository.save(admin);
        }
    }
}