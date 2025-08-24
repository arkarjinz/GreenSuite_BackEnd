package com.app.greensuitetest.controller;

import com.app.greensuitetest.dto.UserProfileDto;
import com.app.greensuitetest.model.User;
import com.app.greensuitetest.repository.CompanyRepository;
import com.app.greensuitetest.repository.UserRepository;
import com.app.greensuitetest.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final SecurityUtil securityUtil;

    @GetMapping("/profile")
    public ResponseEntity<UserProfileDto> getUserProfile() {
        // Get current authenticated user
        User currentUser = securityUtil.getCurrentUser();

        // Create UserProfileDto from user entity
        UserProfileDto profileDto = new UserProfileDto(currentUser);

        // Set company name if available
        if (currentUser.getCompanyId() != null) {
            companyRepository.findById(currentUser.getCompanyId())
                    .ifPresent(company -> profileDto.setCompanyName(company.getName()));
        }

        return ResponseEntity.ok(profileDto);
    }

    @GetMapping("/{userId}/profile")
    public ResponseEntity<UserProfileDto> getUserProfileById(@PathVariable String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserProfileDto profileDto = new UserProfileDto(user);

        if (user.getCompanyId() != null) {
            companyRepository.findById(user.getCompanyId())
                    .ifPresent(company -> profileDto.setCompanyName(company.getName()));
        }

        return ResponseEntity.ok(profileDto);
    }
}