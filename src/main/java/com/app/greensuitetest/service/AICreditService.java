package com.app.greensuitetest.service;

import com.app.greensuitetest.dto.CreditHistoryDto;
import com.app.greensuitetest.exception.EntityNotFoundException;
import com.app.greensuitetest.exception.InsufficientCreditsException;
import com.app.greensuitetest.model.CreditTransaction;
import com.app.greensuitetest.model.User;
import com.app.greensuitetest.repository.CreditTransactionRepository;
import com.app.greensuitetest.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AICreditService {
    
    private final UserRepository userRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    
    private static final int CHAT_COST = 2;
    private static final int DEFAULT_CREDITS = 50;
    
    /**
     * Log a credit transaction
     */
    private void logCreditTransaction(String userId, CreditTransaction.TransactionType type, int amount, 
                                    int balanceBefore, int balanceAfter, String reason, String conversationId) {
        try {
            CreditTransaction transaction = CreditTransaction.builder()
                    .userId(userId)
                    .type(type)
                    .amount(amount)
                    .balanceBefore(balanceBefore)
                    .balanceAfter(balanceAfter)
                    .reason(reason)
                    .conversationId(conversationId)
                    .timestamp(LocalDateTime.now())
                    .build();
            
            creditTransactionRepository.save(transaction);
            log.debug("Logged credit transaction: {} for user: {}", type, userId);
        } catch (Exception e) {
            log.error("Failed to log credit transaction for user: {}", userId, e);
        }
    }
    
    /**
     * Check if user has sufficient credits for chat
     */
    @Cacheable(value = "userCredits", key = "#userId + '_hasCredits'")
    public boolean hasCreditsForChat(String userId) {
        if (userId == null) return false;
        
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) return false;
        
        User user = userOpt.get();
        return user.getAiCredits() >= CHAT_COST;
    }
    
    /**
     * Deduct credits for chat and return remaining credits
     */
    @CacheEvict(value = "userCredits", key = "#userId + '_*'")
    public int deductChatCredits(String userId) {
        return deductChatCredits(userId, null, "Chat conversation");
    }
    
    /**
     * Deduct credits for chat with conversation ID and return remaining credits
     */
    @CacheEvict(value = "userCredits", key = "#userId + '_*'")
    public int deductChatCredits(String userId, String conversationId, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        
        if (user.getAiCredits() < CHAT_COST) {
            throw new InsufficientCreditsException(
                "Insufficient AI credits. Required: " + CHAT_COST + ", Available: " + user.getAiCredits(),
                Map.of(
                    "required", CHAT_COST,
                    "available", user.getAiCredits(),
                    "shortfall", CHAT_COST - user.getAiCredits(),
                    "solution", "Purchase more credits or upgrade your subscription"
                )
            );
        }
        
        int balanceBefore = user.getAiCredits();
        user.setAiCredits(user.getAiCredits() - CHAT_COST);
        user.setLastActive(LocalDateTime.now());
        
        User savedUser = userRepository.save(user);
        
        // Log the transaction
        logCreditTransaction(userId, CreditTransaction.TransactionType.CHAT_DEDUCTION, 
                           -CHAT_COST, balanceBefore, savedUser.getAiCredits(),
                           reason, conversationId);
        
        log.info("Deducted {} AI credits from user {}. Remaining: {}", 
                CHAT_COST, userId, savedUser.getAiCredits());
        
        return savedUser.getAiCredits();
    }
    
    /**
     * Get user's current credit balance
     */
    @Cacheable(value = "userCredits", key = "#userId + '_balance'")
    public int getUserCredits(String userId) {
        return userRepository.findById(userId)
                .map(User::getAiCredits)
                .orElse(0);
    }
    
    /**
     * Add credits to user account (unlimited total, but auto-refill stops at 50)
     */
    @CacheEvict(value = "userCredits", key = "#userId + '_*'")
    @Transactional
    public int addCredits(String userId, int amount, String reason) {
        // Input validation
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        
        int currentCredits = user.getAiCredits();
        
        // For purchases, allow unlimited credits
        // For auto-refill, only add if under 50 credits
        int actualAmount = amount;
        
        // Check if this is an auto-refill and user is at 50+ credits
        if (reason != null && reason.toLowerCase().contains("automatic") && currentCredits >= 50) {
            log.debug("Skipping auto-refill for user {}: already at 50+ credits (current: {})", userId, currentCredits);
            return currentCredits;
        }
        
        int newBalance = currentCredits + actualAmount;
        user.setAiCredits(newBalance);
        user.setLastActive(LocalDateTime.now());
        
        User savedUser = userRepository.save(user);
        
        // Determine transaction type based on reason
        CreditTransaction.TransactionType transactionType = CreditTransaction.TransactionType.ADMIN_GRANT;
        if (reason != null && reason.toLowerCase().contains("purchase")) {
            transactionType = CreditTransaction.TransactionType.CREDIT_PURCHASE;
        } else if (reason != null && reason.toLowerCase().contains("automatic")) {
            transactionType = CreditTransaction.TransactionType.AUTO_REFILL;
        }
        
        // Log the transaction
        logCreditTransaction(userId, transactionType, actualAmount, currentCredits, newBalance,
                           reason, null);
        
        log.info("Added {} AI credits to user {} ({}). New balance: {} (unlimited)", 
                actualAmount, userId, reason, newBalance);
        
        return savedUser.getAiCredits();
    }
    
    /**
     * Initialize credits for new users (called from registration)
     */
    public void initializeCredits(User user) {
        if (user.getAiCredits() == 0) {
            user.setAiCredits(DEFAULT_CREDITS);
            log.info("Initialized {} default AI credits for new user: {}", 
                    DEFAULT_CREDITS, user.getId());
        }
    }
    
    /**
     * Check if user is running low on credits (less than 10)
     */
    public boolean isLowOnCredits(String userId) {
        int credits = getUserCredits(userId);
        return credits < 10;
    }
    
    /**
     * Get credit usage statistics
     */
    public Map<String, Object> getCreditStats(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        
        int currentCredits = user.getAiCredits();
        boolean canChat = currentCredits >= CHAT_COST;
        int possibleChats = currentCredits / CHAT_COST;
        boolean isLow = currentCredits < 10;
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("currentCredits", currentCredits);
        stats.put("chatCost", CHAT_COST);
        stats.put("canChat", canChat);
        stats.put("possibleChats", possibleChats);
        stats.put("isLowOnCredits", isLow);
        stats.put("maxRefillAmount", 50);
        stats.put("totalCreditsLimit", "Unlimited");
        stats.put("autoRefillEnabled", true);
        stats.put("autoRefillAmount", 1);
        stats.put("autoRefillInterval", "5 minutes");
        
        if (isLow) {
            stats.put("warning", "You're running low on AI credits! You can purchase up to 50 credits at a time.");
        }
        
        if (currentCredits >= 50) {
            stats.put("autoRefillStatus", "Paused");
            stats.put("note", "Auto-refill paused (you have 50+ credits). You can still purchase credits.");
        }
        
        return stats;
    }
    
    /**
     * Refund credits (e.g., if chat fails)
     */
    public int refundCredits(String userId, int amount, String reason) {
        return refundCredits(userId, amount, reason, null);
    }
    
    /**
     * Refund credits with conversation ID (e.g., if chat fails)
     */
    @CacheEvict(value = "userCredits", key = "#userId + '_*'")
    @Transactional
    public int refundCredits(String userId, int amount, String reason, String conversationId) {
        // Input validation
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Refund amount must be positive");
        }
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        
        int balanceBefore = user.getAiCredits();
        int newBalance = user.getAiCredits() + amount;
        user.setAiCredits(newBalance);
        user.setLastActive(LocalDateTime.now());
        
        User savedUser = userRepository.save(user);
        
        // Log the transaction
        logCreditTransaction(userId, CreditTransaction.TransactionType.REFUND, amount, 
                           balanceBefore, newBalance, reason, conversationId);
        
        log.info("Refunded {} AI credits to user {} ({}). New balance: {}", 
                amount, userId, reason, newBalance);
        
        return savedUser.getAiCredits();
    }
    
    /**
     * Deduct credits from user account (for admin deductions)
     */
    @CacheEvict(value = "userCredits", key = "#userId + '_*'")
    @Transactional
    public int deductCredits(String userId, int amount, String reason) {
        // Input validation
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Deduction amount must be positive");
        }
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        
        int currentCredits = user.getAiCredits();
        
        // Check if user has enough credits to deduct
        if (currentCredits < amount) {
            throw new InsufficientCreditsException(
                String.format("Insufficient credits. Required: %d, Available: %d", amount, currentCredits)
            );
        }
        
        int newBalance = currentCredits - amount;
        user.setAiCredits(newBalance);
        user.setLastActive(LocalDateTime.now());
        
        User savedUser = userRepository.save(user);
        
        // Log the transaction with correct transaction type
        logCreditTransaction(userId, CreditTransaction.TransactionType.ADMIN_GRANT, -amount, currentCredits, newBalance,
                           reason, null);
        
        log.info("Deducted {} AI credits from user {} ({}). New balance: {}", 
                amount, userId, reason, newBalance);
        
        return savedUser.getAiCredits();
    }

    /**
     * Get simple credit history for a user
     */
    public CreditHistoryDto getCreditHistory(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        
        // Get paginated transactions
        Page<CreditTransaction> transactionPage = creditTransactionRepository
                .findByUserIdOrderByTimestampDesc(userId, pageable);
        
        // Convert to DTOs
        List<CreditHistoryDto.CreditTransactionDto> transactionDtos = transactionPage.getContent()
                .stream()
                .map(this::convertToDto)
                .toList();
        
        int currentBalance = getUserCredits(userId);
        
        return CreditHistoryDto.builder()
                .transactions(transactionDtos)
                .totalTransactions((int) transactionPage.getTotalElements())
                .currentBalance(currentBalance)
                .build();
    }
    
    /**
     * Convert CreditTransaction to DTO
     */
    private CreditHistoryDto.CreditTransactionDto convertToDto(CreditTransaction transaction) {
        return CreditHistoryDto.CreditTransactionDto.builder()
                .id(transaction.getId())
                .type(transaction.getType().name())
                .typeDescription(transaction.getType().getDescription())
                .amount(transaction.getAmount())
                .balanceBefore(transaction.getBalanceBefore())
                .balanceAfter(transaction.getBalanceAfter())
                .reason(transaction.getReason())
                .conversationId(transaction.getConversationId())
                .timestamp(transaction.getTimestamp())
                .build();
    }
    
    /**
     * Automatically refill 1 credit for all users every 5 minutes
     * Note: Auto-refill stops when users reach 50 credits
     */
    @Scheduled(fixedRate = 300000) // 5 minutes = 300,000 milliseconds
    @Transactional
    public void autoRefillCredits() {
        try {
            List<User> eligibleUsers = userRepository.findUsersEligibleForAutoRefill(50);
            int refilledCount = 0;
            int skippedCount = 0;
            
            for (User user : eligibleUsers) {
                try {
                    // Only add credit if user has less than 50 credits
                    if (user.getAiCredits() < 50) {
                    int balanceBefore = user.getAiCredits();
                    user.setAiCredits(user.getAiCredits() + 1);
                    user.setLastActive(LocalDateTime.now());
                        User savedUser = userRepository.save(user);
                    
                    // Log auto-refill transaction
                    logCreditTransaction(user.getId(), CreditTransaction.TransactionType.AUTO_REFILL, 1,
                                           balanceBefore, savedUser.getAiCredits(),
                                           "Automatic credit refill (1 credit every 5 minutes)", null);
                    
                    refilledCount++;
                        log.debug("Auto-refilled 1 credit for user {} (Balance: {} -> {})", 
                                user.getId(), balanceBefore, savedUser.getAiCredits());
                } else {
                        skippedCount++;
                    }
                } catch (Exception e) {
                    log.error("Failed to auto-refill credits for user {}: {}", user.getId(), e.getMessage());
                    skippedCount++;
                }
            }
            
            if (refilledCount > 0 || skippedCount > 0) {
                log.info("Auto-refill completed: {} users refilled, {} users skipped (at 50+ credits)", 
                    refilledCount, skippedCount);
            }
        } catch (Exception e) {
            log.error("Error during auto-refill process: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Get detailed credit analytics for admin dashboard
     */
    public Map<String, Object> getCreditSystemAnalytics() {
        try {
            List<User> allUsers = userRepository.findAll();
            List<User> lowCreditUsers = userRepository.findUsersWithLowCredits();
            List<User> zeroCreditUsers = userRepository.findUsersWithZeroCredits();
            
            int totalCreditsInSystem = allUsers.stream()
                    .mapToInt(User::getAiCredits)
                    .sum();
            
            double averageCreditsPerUser = allUsers.isEmpty() ? 0 : 
                    (double) totalCreditsInSystem / allUsers.size();
            
            Map<String, Object> analytics = new HashMap<>();
            analytics.put("totalUsersWithCredits", allUsers.size());
            analytics.put("totalCreditsInCirculation", totalCreditsInSystem);
            analytics.put("averageCreditsPerUser", Math.round(averageCreditsPerUser * 100.0) / 100.0);
            analytics.put("usersWithLowCredits", lowCreditUsers.size());
            analytics.put("usersWithZeroCredits", zeroCreditUsers.size());
            analytics.put("chatCostPerUser", CHAT_COST);
            analytics.put("autoRefillRate", "1 credit every 5 minutes");
            analytics.put("maxAutoRefillLimit", 50);
            
            return analytics;
        } catch (Exception e) {
            log.error("Error getting credit system analytics: {}", e.getMessage());
            return Map.of("error", "Failed to generate analytics");
        }
    }
    
    /**
     * Bulk credit operation for admin (add credits to multiple users)
     */
    @Transactional
    public Map<String, Object> bulkAddCredits(List<String> userIds, int amount, String reason) {
        int successCount = 0;
        int failureCount = 0;
        List<String> failedUsers = new java.util.ArrayList<>();
        
        for (String userId : userIds) {
            try {
                addCredits(userId, amount, reason);
                successCount++;
            } catch (Exception e) {
                failureCount++;
                failedUsers.add(userId);
                log.error("Failed to add credits to user {}: {}", userId, e.getMessage());
            }
        }
        
        return Map.of(
            "successCount", successCount,
            "failureCount", failureCount,
            "failedUsers", failedUsers,
            "totalRequested", userIds.size()
        );
    }
    
    /**
     * Validate user eligibility for credit operations
     */
    public boolean isUserEligibleForCredits(String userId) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            return user != null && 
                   !user.isBanned() && 
                   user.getApprovalStatus().name().equals("APPROVED");
        } catch (Exception e) {
            log.error("Error checking user eligibility: {}", e.getMessage());
            return false;
        }
    }
}