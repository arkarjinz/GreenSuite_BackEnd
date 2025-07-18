package com.app.greensuitetest.service;

import com.app.greensuitetest.constants.ApprovalStatus;
import com.app.greensuitetest.constants.Role;
import com.app.greensuitetest.constants.SubscriptionTier;
import com.app.greensuitetest.dto.AuthDTO;
import com.app.greensuitetest.dto.UserProfileDto;
import com.app.greensuitetest.exception.AuthException;
import com.app.greensuitetest.exception.ValidationException;
import com.app.greensuitetest.model.Company;
import com.app.greensuitetest.model.User;
import com.app.greensuitetest.repository.CompanyRepository;
import com.app.greensuitetest.repository.UserRepository;
import com.app.greensuitetest.security.JwtUtil;
import com.app.greensuitetest.util.SecurityUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final AuthenticationManager authManager;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final Map<SubscriptionTier, Set<String>> featureMapping;
    private final SecurityUtil securityUtil;

    public User register(AuthDTO.RegisterRequest request) {
        // 1. Validate request data
        validateRegistrationRequest(request);

        // 2. Get or create company
        Company company = getOrCreateCompany(request);

        // 3. Create and save user
        return createAndSaveUser(request, company);
    }

    private void validateRegistrationRequest(AuthDTO.RegisterRequest request) {
        // Email validation
        if (userRepository.existsByEmail(request.email())) {
            throw new ValidationException("Email already registered",
                    Map.of("field", "email",
                            "value", request.email(),
                            "solution", "Use a different email or try logging in"));
        }

        // Username validation
        if (userRepository.existsByUserName(request.userName())) {
            throw new ValidationException("Username already taken",
                    Map.of("field", "userName",
                            "value", request.userName(),
                            "solution", "Choose a different username"));
        }

        // Company name validation for owners
        if (request.companyRole() == Role.OWNER) {
               if (companyRepository.existsByName(request.companyName())) {
            throw new ValidationException("Company name already exists",
                    Map.of("field", "companyName",
                            "value", request.companyName(),
                            "solution", "Choose a different company name"));
        }

        if (request.companyName() == null || request.companyName().isBlank()) {
            throw new ValidationException("Company name is required for owners",
                    Map.of("field", "companyName",
                            "value", "",
                            "solution", "Choose a different company name"));
        }


        // New validation for OWNER-specific fields
            if (request.companyAddress() == null || request.companyAddress().isBlank()) {
                throw new ValidationException("Company address is required for owners",
                        Map.of("field", "companyAddress",
                                "solution", "Provide a valid company address"));
            }
            if (request.industry() == null || request.industry().isBlank()) {
                throw new ValidationException("Industry is required for owners",
                        Map.of("field", "industry",
                                "solution", "Provide a valid industry"));
            }
        }


    }

    private Company getOrCreateCompany(AuthDTO.RegisterRequest request) {
        if (request.companyRole() == Role.OWNER) {
            return createNewCompany(request);
        } else {
            return findExistingCompany(request);
        }
    }

    private Company createNewCompany(AuthDTO.RegisterRequest request) {
        try {
            Company company = new Company();
            company.setName(request.companyName());
            company.setAddress(request.companyAddress());
            company.setIndustry(request.industry());
            company.setTier(SubscriptionTier.FREE);
            company.setEnabledFeatures(new EnumMap<>(featureMapping));
            return companyRepository.save(company);
        } catch (DuplicateKeyException ex) {
            if (companyRepository.existsByName(request.companyName())) {
                throw new ValidationException("Company name already exists",
                        Map.of("field", "companyName",
                                "value", request.companyName(),
                                "solution", "Choose a different company name"));
            }
            throw new ValidationException("Failed to create company",
                    extractDuplicateDetails(ex));
        }
    }

    private Company findExistingCompany(AuthDTO.RegisterRequest request) {
        if (request.companyName() == null || request.companyName().isBlank()) {
            throw new ValidationException("Company selection is required",
                    Map.of("field", "companyId",
                            "solution", "Please select a company"));
        }

        return companyRepository.findByName(request.companyName());
    }

    private User createAndSaveUser(AuthDTO.RegisterRequest request, Company company) {
        try {
            // Set approval status
            ApprovalStatus approvalStatus = request.companyRole() == Role.OWNER
                    ? ApprovalStatus.APPROVED
                    : ApprovalStatus.PENDING;

            // Build user entity
            User user = buildUserEntity(request, company, approvalStatus);

            // Save user
            User savedUser = userRepository.save(user);

            // Handle post-registration notifications
            handlePostRegistration(request, company, savedUser);

            return savedUser;
        } catch (DuplicateKeyException ex) {
            return handleUserSaveException(ex, request);
        }
    }

    private User buildUserEntity(AuthDTO.RegisterRequest request, Company company, ApprovalStatus approvalStatus) {
        User.UserBuilder builder = User.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .userName(request.userName())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .companyId(company.getId())
                .companyRole(request.companyRole())
                .approvalStatus(approvalStatus)
                .createdAt(LocalDateTime.now())
                .lastActive(LocalDateTime.now());

        // Add premium benefits if applicable
        if (company.getTier() == SubscriptionTier.PREMIUM) {
            builder.aiCredits(500);
        }

        return builder.build();
    }

    private void handlePostRegistration(AuthDTO.RegisterRequest request, Company company, User user) {
        // Notify owner if this is a non-owner registration
        if (request.companyRole() != Role.OWNER) {
            notifyCompanyOwner(company, user);
        }

        // Log successful registration
        log.info("User registered: {} ({}) with company: {}",
                user.getEmail(), user.getId(), company.getName());
    }

    private void notifyCompanyOwner(Company company, User user) {
        Optional<User> owner = userRepository.findByCompanyIdAndCompanyRole(company.getId(), Role.OWNER);
        if (owner.isPresent()) {
            log.info("Pending approval: User {} needs approval from owner {}",
                    user.getEmail(), owner.get().getEmail());
            // TODO: Implement email notification
        } else {
            log.warn("No owner found for company {} to approve user {}",
                    company.getId(), user.getId());
        }
    }

    private User handleUserSaveException(DuplicateKeyException ex, AuthDTO.RegisterRequest request) {
        // Check which unique constraint was violated
        Map<String, Object> errorDetails = extractDuplicateDetails(ex);

        if (errorDetails.containsKey("field")) {
            String field = (String) errorDetails.get("field");
            String solution = (String) errorDetails.getOrDefault("solution", "Please provide a unique value");

            if ("email".equals(field)) {
                throw new ValidationException("Email already registered",
                        Map.of("field", "email",
                                "value", request.email(),
                                "solution", solution));
            }
            else if ("userName".equals(field)) {
                throw new ValidationException("Username already taken",
                        Map.of("field", "userName",
                                "value", request.userName(),
                                "solution", solution));
            }
        }

        // Generic duplicate error
        throw new ValidationException("Duplicate value detected", errorDetails);
    }

    private Map<String, Object> extractDuplicateDetails(DuplicateKeyException ex) {
        Map<String, Object> details = new HashMap<>();
        String errorMessage = ex.getMessage().toLowerCase();

        if (errorMessage.contains("email")) {
            details.put("field", "email");
            details.put("solution", "Email must be unique");
        }
        else if (errorMessage.contains("user_name") || errorMessage.contains("username")) {
            details.put("field", "userName");
            details.put("solution", "Username must be unique");
        }
        else if (errorMessage.contains("company.name") || errorMessage.contains("name_1")) {
            details.put("field", "companyName");
            details.put("solution", "Company name must be unique");
        }
        else {
            details.put("error", "DuplicateKeyException");
            details.put("message", "Duplicate value detected in database");
        }

        return details;
    }

    public Map<String, Object> login(AuthDTO.LoginRequest request) {
        try {
            // Authenticate credentials
            Authentication authentication = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.email(),
                            request.password()
                    )
            );

            // Get user from database
            User user = userRepository.findByEmail(request.email())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            // Check approval status
            if (user.getApprovalStatus() != ApprovalStatus.APPROVED) {
                return createPendingApprovalResponse(user);
            }

            // Update user engagement metrics
            updateUserEngagement(user);

            // Generate tokens
            String accessToken = jwtUtil.generateAccessToken(user, authentication.getAuthorities());
            String refreshToken = jwtUtil.generateRefreshToken(user);

            // Save refresh token
            user.setRefreshToken(refreshToken);
            userRepository.save(user);

            // Return successful response
            return createLoginSuccessResponse(user, accessToken, refreshToken);
        } catch (UsernameNotFoundException ex) {
            throw new AuthException("Invalid credentials",
                    Map.of("field", "email",
                            "value", request.email(),
                            "solution", "Check your credentials or register if new"));
        } catch (Exception ex) {
            throw new AuthException("Authentication failed",
                    Map.of("error", ex.getMessage()));
        }
    }

    private Map<String, Object> createPendingApprovalResponse(User user) {
        return Map.of(
                "status", "pending",
                "message", "Account pending approval from company owner",
                "user", new UserProfileDto(user),
                "solution", "Contact your company administrator for approval"
        );
    }

    private Map<String, Object> createLoginSuccessResponse(User user, String accessToken, String refreshToken) {
        return Map.of(
                "status", "success",
                "message", "Login successful",
                "accessToken", accessToken,
                "refreshToken", refreshToken,
                "user", new UserProfileDto(user)
        );
    }

    private void updateUserEngagement(User user) {
        LocalDateTime now = LocalDateTime.now();
        user.setLastActive(now);

        // Update streak if last active was more than 1 day ago
        if (user.getLastActive() == null ||
                user.getLastActive().isBefore(now.minusDays(1))) {
            user.setStreakCount(user.getStreakCount() + 1);
        }

        // Award badge for 7-day streak
        if (user.getStreakCount() >= 7 && !user.getBadges().contains("7-day-streak")) {
            user.getBadges().add("7-day-streak");
            log.info("Awarded 7-day streak badge to user: {}", user.getEmail());
        }
    }

    public Map<String, String> refreshToken(AuthDTO.RefreshRequest request) {
        try {
            String refreshToken = request.refreshToken();
            Claims claims = jwtUtil.parseToken(refreshToken);

            // Validate token type
            String tokenType = claims.get("token_type", String.class);
            if (!"refresh".equals(tokenType)) {
                throw new AuthException("Invalid token type",
                        Map.of("providedType", tokenType,
                                "expectedType", "refresh"));
            }

            // Get user by email
            String email = claims.getSubject();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new AuthException("User not found",
                            Map.of("email", email)));

            // Validate refresh token
            if (user.getRefreshToken() == null || !user.getRefreshToken().equals(refreshToken)) {
                throw new AuthException("Invalid refresh token",
                        Map.of("solution", "Please login again to get new tokens"));
            }

            // Generate new tokens
            String newAccessToken = jwtUtil.generateAccessToken(user, user.getAuthorities());
            String newRefreshToken = jwtUtil.generateRefreshToken(user);

            // Update refresh token
            user.setRefreshToken(newRefreshToken);
            userRepository.save(user);

            return Map.of(
                    "accessToken", newAccessToken,
                    "refreshToken", newRefreshToken
            );
        } catch (AuthException ex) {
            throw ex; // Re-throw known exceptions
        } catch (Exception ex) {
            log.error("Token refresh failed: {}", ex.getMessage());
            throw new AuthException("Token refresh failed",
                    Map.of("error", ex.getMessage(),
                            "solution", "Try logging in again"));
        }
    }
}