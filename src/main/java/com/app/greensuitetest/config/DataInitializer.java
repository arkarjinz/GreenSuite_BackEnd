package com.app.greensuitetest.config;

import com.app.greensuitetest.model.User;
import com.app.greensuitetest.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostConstruct
    public void init() {
        try {
            log.info("Initializing default data...");
            
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
                log.info("Default admin user created successfully");
            } else {
                log.info("Default admin user already exists");
            }
        } catch (Exception e) {
            log.warn("Failed to initialize default data - MongoDB may not be available: {}", e.getMessage());
            log.warn("Application will continue to start, but some features may not work until MongoDB is available");
            log.warn("Please ensure MongoDB is running on localhost:27017 or update spring.data.mongodb.uri in application.properties");
        }
    }
}