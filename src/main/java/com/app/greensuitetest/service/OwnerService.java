package com.app.greensuitetest.service;

import com.app.greensuitetest.constants.ApprovalStatus;
import com.app.greensuitetest.constants.Role;
import com.app.greensuitetest.dto.UserProfileDto;
import com.app.greensuitetest.exception.EntityNotFoundException;
import com.app.greensuitetest.exception.OperationNotAllowedException;
import com.app.greensuitetest.model.Company;
import com.app.greensuitetest.model.User;
import com.app.greensuitetest.repository.CompanyRepository;
import com.app.greensuitetest.repository.UserRepository;
import com.app.greensuitetest.security.JwtUtil;
import com.app.greensuitetest.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerService {
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final SecurityUtil securityUtil;
    private final NotificationService notificationService;
    private final JwtUtil jwtUtil;

    public List<UserProfileDto> getPendingUsers() {
        String companyId = securityUtil.getCurrentUserCompanyId();
        List<User> users = userRepository.findByCompanyIdAndApprovalStatus(companyId, ApprovalStatus.PENDING);

        // Get company name
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new EntityNotFoundException("Company not found"));
        String companyName = company.getName();

        // Convert to DTOs with company name and rejection info
        return users.stream()
                .map(user -> {
                    UserProfileDto dto = new UserProfileDto(user);
                    dto.setCompanyName(companyName);

                    // Add rejection information
                    dto.setRejectionCount(user.getRejectionCount());
                    dto.setRemainingAttempts(user.getRemainingAttempts());
                    dto.setApproachingBan(user.isApproachingBan());

                    return dto;
                })
                .collect(Collectors.toList());
    }

    public UserProfileDto approveUser(String userId) {
        User currentUser = securityUtil.getCurrentUser();
        User targetUser = getUser(userId);

        validateOwnership(currentUser, targetUser);
        validateNotSelfOperation(currentUser, targetUser);
        validateNotOwner(targetUser);
        validateUserIsPending(targetUser);
        validateUserNotBanned(targetUser);

        targetUser.setApprovalStatus(ApprovalStatus.APPROVED);
        userRepository.save(targetUser);

        log.info("User {} approved by owner {} for company {}",
                targetUser.getEmail(), currentUser.getEmail(), currentUser.getCompanyId());

        return createUserProfileDto(targetUser);
    }

    public Map<String, Object> rejectUser(String userId, String reason) {
        User currentUser = securityUtil.getCurrentUser();
        User targetUser = getUser(userId);

        validateOwnership(currentUser, targetUser);
        validateNotSelfOperation(currentUser, targetUser);
        validateNotOwner(targetUser);
        validateUserIsPending(targetUser);
        validateUserNotBanned(targetUser);

        // Get company information
        Company company = companyRepository.findById(currentUser.getCompanyId())
                .orElseThrow(() -> new EntityNotFoundException("Company not found"));
        // Ensure user is pending
        if (targetUser.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new OperationNotAllowedException("User is not pending approval");
        }
        
        // Increment rejection count and update history (this method handles status changes)
        targetUser.incrementRejectionCount(
                company.getId(),
                company.getName(),
                currentUser.getEmail()
        );


        // Save user
        User savedUser = userRepository.save(targetUser);

        // Generate reapplication token
        String reapplicationToken = jwtUtil.generateReapplicationToken(savedUser);

        // Create response with rejection details
        Map<String, Object> response = new HashMap<>();
        response.put("user", createUserProfileDto(savedUser));
        response.put("rejectionCount", savedUser.getRejectionCount());
        response.put("remainingAttempts", savedUser.getRemainingAttempts());
        response.put("isBanned", savedUser.isBanned());

        // Handle different rejection scenarios
        if (savedUser.isBanned()) {
            response.put("message", "User has been permanently banned after 5 rejections");
            response.put("banDetails", Map.of(
                    "bannedAt", savedUser.getBannedAt(),
                    "banReason", savedUser.getBanReason()
            ));

            log.warn("User {} has been banned after 5 rejections. Last rejection by {} for company {}",
                    savedUser.getEmail(), currentUser.getEmail(), company.getName());

            // Send ban notification to user
            notificationService.sendBanNotification(savedUser);

        } else if (savedUser.isApproachingBan()) {
            response.put("message", "User rejected. WARNING: This is their 4th rejection - one more will result in a permanent ban");
            response.put("warning", "User will be banned on next rejection");

            log.warn("User {} rejected for the 4th time by {} for company {}. Next rejection will result in ban",
                    savedUser.getEmail(), currentUser.getEmail(), company.getName());

            // Send warning notification to user
            notificationService.sendWarningNotification(savedUser);

        } else {
            response.put("message", String.format("User rejected. They have %d attempts remaining",
                    savedUser.getRemainingAttempts()));

            log.info("User {} rejected by {} for company {}. Rejection count: {}",
                    savedUser.getEmail(), currentUser.getEmail(), company.getName(), savedUser.getRejectionCount());
            // Send regular rejection notification
            notificationService.sendRejectionNotification(savedUser, company, reason);
        }

        // Add token to response
        response.put("reapplicationToken", reapplicationToken);
        response.put("reapplicationUrl", "/api/auth/reapply");

        return response;
    }

    public List<Map<String, Object>> getUserRejectionHistory(String userId) {
        User currentUser = securityUtil.getCurrentUser();
        User targetUser = getUser(userId);

        validateOwnership(currentUser, targetUser);

        return targetUser.getRejectionHistory().stream()
                .map(record -> {
                    Map<String, Object> historyMap = new HashMap<>();
                    historyMap.put("companyName", record.getCompanyName());
                    historyMap.put("rejectedAt", record.getRejectedAt());
                    historyMap.put("rejectionNumber", record.getRejectionNumber());
                    historyMap.put("rejectedBy", record.getRejectedBy());
                    return historyMap;
                })
                .collect(Collectors.toList());
    }

    public Map<String, Object> getCompanyStats() {
        String companyId = securityUtil.getCurrentUserCompanyId();

        List<User> pendingUsers = userRepository.findByCompanyIdAndApprovalStatus(companyId, ApprovalStatus.PENDING);
        List<User> approvedUsers = userRepository.findByCompanyIdAndApprovalStatus(companyId, ApprovalStatus.APPROVED);
        List<User> rejectedUsers = userRepository.findRejectedUsersByCompany(companyId);

        long reapplicants = pendingUsers.stream()
                .filter(user -> user.getRejectionCount() > 0)
                .count();

        long usersApproachingBan = pendingUsers.stream()
                .filter(User::isApproachingBan)
                .count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("pendingUsers", pendingUsers.size());
        stats.put("approvedUsers", approvedUsers.size());
        stats.put("rejectedUsers", rejectedUsers.size());
        stats.put("reapplicants", reapplicants);
        stats.put("usersApproachingBan", usersApproachingBan);

        return stats;
    }

   


    private UserProfileDto createUserProfileDto(User user) {
        UserProfileDto dto = new UserProfileDto(user);
        // Add company name only if company exists
        if (user.getCompanyId() != null) {
            Company company = companyRepository.findById(user.getCompanyId())
                    .orElseThrow(() -> new EntityNotFoundException("Company not found"));
            dto.setCompanyName(company.getName());
        } else {
            dto.setCompanyName("N/A");  // Or leave null
        }

        // Add rejection information
        dto.setRejectionCount(user.getRejectionCount());
        dto.setRemainingAttempts(user.getRemainingAttempts());
        dto.setApproachingBan(user.isApproachingBan());
        dto.setBanned(user.isBanned());

        return dto;
    }

    private User getUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }

    private void validateOwnership(User currentUser, User targetUser) {
        if (!currentUser.getCompanyId().equals(targetUser.getCompanyId())) {
            throw new OperationNotAllowedException("User doesn't belong to your company");
        }
    }

    private void validateNotSelfOperation(User currentUser, User targetUser) {
        if (currentUser.getId().equals(targetUser.getId())) {
            throw new OperationNotAllowedException("Cannot perform this operation on yourself");
        }
    }

    private void validateNotOwner(User targetUser) {
        if (targetUser.getCompanyRole() == Role.OWNER) {
            throw new OperationNotAllowedException("Cannot modify other owners");
        }
    }

    private void validateUserNotBanned(User targetUser) {
        if (targetUser.isBanned()) {
            throw new OperationNotAllowedException("Cannot perform operations on banned users");
        }
    }

    private void validateUserIsPending(User user) {
        if (user.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new OperationNotAllowedException("Operation only allowed on pending users");
        }
    }
}