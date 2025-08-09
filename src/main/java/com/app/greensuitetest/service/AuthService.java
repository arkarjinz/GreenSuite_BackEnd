package com.app.greensuitetest.service;

import com.app.greensuitetest.constants.ApprovalStatus;
import com.app.greensuitetest.constants.Role;
import com.app.greensuitetest.constants.SubscriptionTier;
import com.app.greensuitetest.dto.AuthDTO;
import com.app.greensuitetest.dto.UserProfileDto;
import com.app.greensuitetest.exception.AuthException;
import com.app.greensuitetest.exception.EntityNotFoundException;
import com.app.greensuitetest.exception.OperationNotAllowedException;
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
        // 1. Validate request data (including ban check)
        validateRegistrationRequest(request);

        // 2. Get or create company
        Company company = getOrCreateCompany(request);

        // 3. Create and save user
        return createAndSaveUser(request, company);
    }

    private void validateRegistrationRequest(AuthDTO.RegisterRequest request) {
        // Check if user is banned by email or username
        validateUserNotBanned(request.email(), request.userName());

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

    private void validateUserNotBanned(String email, String userName) {
        // Check for banned users by email
        Optional<User> existingUserByEmail = userRepository.findByEmail(email);
        if (existingUserByEmail.isPresent() && existingUserByEmail.get().isBanned()) {
            throw new ValidationException("This email is permanently banned from the platform",
                    Map.of("field", "email",
                            "value", email,
                            "reason", existingUserByEmail.get().getBanReason(),
                            "bannedAt", existingUserByEmail.get().getBannedAt().toString(),
                            "solution", "Contact support if you believe this is an error"));
        }

        // Check for banned users by username
        Optional<User> existingUserByUsername = userRepository.findByUserName(userName);
        if (existingUserByUsername.isPresent() && existingUserByUsername.get().isBanned()) {
            throw new ValidationException("This username is permanently banned from the platform",
                    Map.of("field", "userName",
                            "value", userName,
                            "reason", existingUserByUsername.get().getBanReason(),
                            "bannedAt", existingUserByUsername.get().getBannedAt().toString(),
                            "solution", "Contact support if you believe this is an error"));
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
            // Check if this is a re-registration after rejection
            Optional<User> existingUser = userRepository.findByEmail(request.email());

            User user;
            if (existingUser.isPresent() && existingUser.get().getApprovalStatus() == ApprovalStatus.REJECTED) {
                // Update existing rejected user for new company
                user = existingUser.get();
                user.setCompanyId(company.getId());
                user.setApprovalStatus(ApprovalStatus.PENDING);
                user.setLastActive(LocalDateTime.now());

                log.info("User {} is re-applying after rejection. Rejection count: {}",
                        user.getEmail(), user.getRejectionCount());

            } else {
                // Set approval status
                ApprovalStatus approvalStatus = request.companyRole() == Role.OWNER
                        ? ApprovalStatus.APPROVED
                        : ApprovalStatus.PENDING;

                // Build new user entity
                user = buildUserEntity(request, company, approvalStatus);
            }

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
                .lastActive(LocalDateTime.now())
                .rejectionCount(0)
                .isBanned(false)
                .aiCredits(50);  // Initialize with 50 AI credits
    
        // Add premium benefits if applicable
        if (company.getTier() == SubscriptionTier.PREMIUM) {
            builder.aiCredits(500); // Premium users get more credits
        }
    
        return builder.build();
    }
    

    private void handlePostRegistration(AuthDTO.RegisterRequest request, Company company, User user) {
        // Notify owner if this is a non-owner registration
        if (request.companyRole() != Role.OWNER) {
            notifyCompanyOwner(company, user);
        }

        // Log successful registration
        log.info("User registered: {} ({}) with company: {}. Rejection count: {}",
                user.getEmail(), user.getId(), company.getName(), user.getRejectionCount());
    }

    private void notifyCompanyOwner(Company company, User user) {
        Optional<User> owner = userRepository.findByCompanyIdAndCompanyRole(company.getId(), Role.OWNER);
        if (owner.isPresent()) {
            String message = user.getRejectionCount() > 0
                    ? String.format("User %s is re-applying after %d previous rejections",
                    user.getEmail(), user.getRejectionCount())
                    : String.format("New user %s needs approval", user.getEmail());

            log.info("Pending approval: {}", message);
            // TODO: Implement email notification with rejection history
        } else {
            log.warn("No owner found for company {} to approve user {}",
                    company.getId(), user.getId());
        }
    }

    public Map<String, Object> login(AuthDTO.LoginRequest request) {
        try {
            // Check if user is banned before authentication
            Optional<User> userOpt = userRepository.findByEmail(request.email());
            if (userOpt.isPresent() && userOpt.get().isBanned()) {
                User bannedUser = userOpt.get();
                throw new AuthException("Account is permanently banned",
                        Map.of("banned", true,
                                "banReason", bannedUser.getBanReason(),
                                "bannedAt", bannedUser.getBannedAt().toString(),
                                "solution", "Contact support if you believe this is an error"));
            }

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

            // Double-check ban status (in case of race condition)
            if (user.isBanned()) {
                throw new AuthException("Account is permanently banned",
                        Map.of("banned", true,
                                "banReason", user.getBanReason(),
                                "bannedAt", user.getBannedAt().toString(),
                                "solution", "Contact support if you believe this is an error"));
            }

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
        } catch (AuthException ex) {
            throw ex; // Re-throw known auth exceptions
        } catch (Exception ex) {
            throw new AuthException("Authentication failed",
                    Map.of("error", ex.getMessage()));
        }
    }

    private Map<String, Object> createPendingApprovalResponse(User user) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "pending");
        response.put("user", new UserProfileDto(user));

        if (user.getRejectionCount() > 0) {
            response.put("message", String.format("Account pending approval (Previously rejected %d times)",
                    user.getRejectionCount()));
            response.put("rejectionCount", user.getRejectionCount());
            response.put("remainingAttempts", user.getRemainingAttempts());

            if (user.isApproachingBan()) {
                response.put("warning", "WARNING: You have 1 rejection remaining before permanent ban");
            }
        } else {
            response.put("message", "Account pending approval from company owner");
        }

        response.put("solution", "Contact your company administrator for approval");
        return response;
    }

    // ... (rest of the methods remain the same)

    private Map<String, Object> createLoginSuccessResponse(User user, String accessToken, String refreshToken) {
        Map<String, Object> response = new HashMap<>();
        response.put("accessToken", accessToken);
        response.put("refreshToken", refreshToken);
    
        Map<String, Object> userProfile = new HashMap<>();
        userProfile.put("id", user.getId());
        userProfile.put("firstName", user.getFirstName());
        userProfile.put("lastName", user.getLastName());
        userProfile.put("userName", user.getUserName());
        userProfile.put("email", user.getEmail());
        userProfile.put("companyId", user.getCompanyId());
        userProfile.put("companyRole", user.getCompanyRole().name());
        userProfile.put("globalAdmin", user.isGlobalAdmin());
        userProfile.put("approvalStatus", user.getApprovalStatus().name());
        userProfile.put("rejectionCount", user.getRejectionCount());
        userProfile.put("isBanned", user.isBanned());
        
        // Add AI credit information
        userProfile.put("aiCredits", user.getAiCredits());
        userProfile.put("canChat", user.hasCreditsForChat());
        userProfile.put("maxPossibleChats", user.getMaxPossibleChats());
        userProfile.put("isLowOnCredits", user.isLowOnCredits());
    
        // Add company name
        if (user.getCompanyId() != null) {
            companyRepository.findById(user.getCompanyId()).ifPresent(company -> {
                userProfile.put("companyName", company.getName());
            });
        }
    
        response.put("user", userProfile);
        return response;
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

    public User reapply(AuthDTO.ReapplyRequest request) {
        // Parse and validate token
        Claims claims = jwtUtil.parseReapplicationToken(request.token());
        String userId = claims.getSubject();
        String email = claims.get("email", String.class);

        // Get user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // Validate user status
        if (user.getApprovalStatus() != ApprovalStatus.REJECTED) {
            throw new OperationNotAllowedException("Only rejected users can reapply");
        }

        if (user.isBanned()) {
            throw new ValidationException("Account is permanently banned", Map.of(
                    "banReason", user.getBanReason(),
                    "bannedAt", user.getBannedAt().toString()
            ));
        }

        // Get or create company
        Company company = getOrCreateCompany(request.companyName(), request.companyRole());

        // Validate user is not reapplying to a company that previously rejected them
        validateNotPreviouslyRejectedByCompany(user, company);

        // Update user details
        // NOTE: Allowing password change during reapply - consider if this needs additional verification
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setCompanyId(company.getId());
        user.setCompanyRole(request.companyRole());
        user.setApprovalStatus(ApprovalStatus.PENDING);
        user.setLastActive(LocalDateTime.now());

        // Save user
        User savedUser = userRepository.save(user);

        // Notify company owner
        notifyCompanyOwner(company, savedUser);

        log.info("User {} reapplied to company {}", email, company.getName());
        return savedUser;
    }

    private Company getOrCreateCompany(String companyName, Role role) {
        if (role == Role.OWNER) {
            throw new ValidationException("Owner role requires full registration",
                    Map.of("solution", "Use the regular registration endpoint for owner accounts"));
        }

        Company company = companyRepository.findByName(companyName);
        if (company == null) {
            throw new EntityNotFoundException("Company not found: " + companyName);
        }
        return company;
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

            // Check if user is banned
            if (user.isBanned()) {
                throw new AuthException("Account is permanently banned",
                        Map.of("banned", true,
                                "banReason", user.getBanReason(),
                                "bannedAt", user.getBannedAt().toString(),
                                "solution", "Contact support if you believe this is an error"));
            }

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

    /**
     * Validate that user is not reapplying to a company that previously rejected them
     */
    private void validateNotPreviouslyRejectedByCompany(User user, Company company) {
        boolean wasRejectedByThisCompany = user.getRejectionHistory().stream()
                .anyMatch(record -> record.getCompanyId().equals(company.getId()));
        
        if (wasRejectedByThisCompany) {
            long rejectionsByThisCompany = user.getRejectionHistory().stream()
                    .filter(record -> record.getCompanyId().equals(company.getId()))
                    .count();
            
            throw new ValidationException("Cannot reapply to a company that previously rejected you",
                    Map.of("companyName", company.getName(),
                           "previousRejections", rejectionsByThisCompany,
                           "solution", "Apply to a different company"));
        }
    }
}