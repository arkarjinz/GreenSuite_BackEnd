package com.app.greensuitetest.service;

import com.app.greensuitetest.dto.UserProfileDto;
import com.app.greensuitetest.exception.EntityNotFoundException;
import com.app.greensuitetest.exception.OperationNotAllowedException;
import com.app.greensuitetest.model.User;
import com.app.greensuitetest.repository.UserRepository;
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
public class AdminService {
    private final UserRepository userRepository;
    private final SecurityUtil securityUtil;
    private final NotificationService notificationService;

    public List<UserProfileDto> getAllBannedUsers() {
        validateAdminAccess();

        List<User> bannedUsers = userRepository.findAllBannedUsers();
        return bannedUsers.stream()
                .map(UserProfileDto::new)
                .collect(Collectors.toList());
    }

    public List<UserProfileDto> getUsersApproachingBan() {
        validateAdminAccess();

        List<User> usersApproachingBan = userRepository.findUsersApproachingBan();
        return usersApproachingBan.stream()
                .map(UserProfileDto::new)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getBanStatistics() {
        validateAdminAccess();

        List<User> allUsers = userRepository.findAll();
        List<User> bannedUsers = userRepository.findAllBannedUsers();
        List<User> usersWithRejections = userRepository.findUsersByRejectionCountGreaterThanEqual(1);
        List<User> usersApproachingBan = userRepository.findUsersApproachingBan();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", allUsers.size());
        stats.put("bannedUsers", bannedUsers.size());
        stats.put("usersWithRejections", usersWithRejections.size());
        stats.put("usersApproachingBan", usersApproachingBan.size());
        stats.put("banRate", calculateBanRate(allUsers.size(), bannedUsers.size()));

        // Rejection distribution
        Map<Integer, Long> rejectionDistribution = usersWithRejections.stream()
                .collect(Collectors.groupingBy(User::getRejectionCount, Collectors.counting()));
        stats.put("rejectionDistribution", rejectionDistribution);

        return stats;
    }

    public UserProfileDto unbanUser(String userId, String reason) {
        validateAdminAccess();
        User currentAdmin = securityUtil.getCurrentUser();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (!user.isBanned()) {
            throw new OperationNotAllowedException("User is not banned");
        }

        // Unban the user
        user.setBanned(false);
        user.setBannedAt(null);
        user.setBanReason(null);

        // Optionally reset rejection count or keep it for history
        // user.setRejectionCount(0); // Uncomment if you want to reset rejection count

        User savedUser = userRepository.save(user);

        log.info("User {} unbanned by admin {} for reason: {}",
                user.getEmail(), currentAdmin.getEmail(), reason);

        // TODO: Send unban notification
        // notificationService.sendUnbanNotification(savedUser, reason);

        return new UserProfileDto(savedUser);
    }

    public UserProfileDto manualBanUser(String userId, String reason) {
        validateAdminAccess();
        User currentAdmin = securityUtil.getCurrentUser();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (user.isBanned()) {
            throw new OperationNotAllowedException("User is already banned");
        }

        if (user.isGlobalAdmin()) {
            throw new OperationNotAllowedException("Cannot ban global admin users");
        }

        // Ban the user
        user.setBanned(true);
        user.setBannedAt(LocalDateTime.now());
        user.setBanReason(reason + " (Manual ban by admin)");

        User savedUser = userRepository.save(user);

        log.warn("User {} manually banned by admin {} for reason: {}",
                user.getEmail(), currentAdmin.getEmail(), reason);

        notificationService.sendBanNotification(savedUser);

        return new UserProfileDto(savedUser);
    }

    public Map<String, Object> getUserRejectionDetails(String userId) {
        validateAdminAccess();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Map<String, Object> details = new HashMap<>();
        details.put("user", new UserProfileDto(user));
        details.put("rejectionHistory", user.getRejectionHistory());
        details.put("totalRejections", user.getRejectionCount());
        details.put("remainingAttempts", user.getRemainingAttempts());
        details.put("isBanned", user.isBanned());
        details.put("banDetails", user.isBanned() ? Map.of(
                "bannedAt", user.getBannedAt(),
                "banReason", user.getBanReason()
        ) : null);

        return details;
    }

    private void validateAdminAccess() {
        User currentUser = securityUtil.getCurrentUser();
        if (!currentUser.isGlobalAdmin()) {
            throw new OperationNotAllowedException("Admin access required");
        }
    }

    private double calculateBanRate(int totalUsers, int bannedUsers) {
        if (totalUsers == 0) return 0.0;
        return Math.round((double) bannedUsers / totalUsers * 100.0 * 100.0) / 100.0;
    }
}