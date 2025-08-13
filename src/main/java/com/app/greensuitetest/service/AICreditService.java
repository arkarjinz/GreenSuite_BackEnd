package com.app.greensuitetest.service;

import com.app.greensuitetest.exception.InsufficientCreditsException;
import com.app.greensuitetest.exception.EntityNotFoundException;
import com.app.greensuitetest.model.User;
import com.app.greensuitetest.model.CreditTransaction;
import com.app.greensuitetest.repository.UserRepository;
import com.app.greensuitetest.repository.CreditTransactionRepository;
import com.app.greensuitetest.dto.CreditHistoryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
    public int addCredits(String userId, int amount, String reason) {
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
    public int refundCredits(String userId, int amount, String reason, String conversationId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        
        int balanceBefore = user.getAiCredits();
        int newBalance = user.getAiCredits() + amount;
        user.setAiCredits(newBalance);
        
        User savedUser = userRepository.save(user);
        
        // Log the transaction
        logCreditTransaction(userId, CreditTransaction.TransactionType.REFUND, amount, 
                           balanceBefore, newBalance, reason, conversationId);
        
        log.info("Refunded {} AI credits to user {} ({}). New balance: {}", 
                amount, userId, reason, newBalance);
        
        return savedUser.getAiCredits();
    }
    
    /**
     * Deduct credits from user account (for refunds)
     */
    public int deductCredits(String userId, int amount, String reason) {
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
        
        // Log the transaction
        logCreditTransaction(userId, CreditTransaction.TransactionType.REFUND, amount, currentCredits, newBalance,
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
    public void autoRefillCredits() {
        try {
            List<User> allUsers = userRepository.findAll();
            int refilledCount = 0;
            int skippedCount = 0;
            
            for (User user : allUsers) {
                // Only add credit if user hasn't reached the maximum of 50 credits
                if (user.canReceiveCredits()) {
                    int balanceBefore = user.getAiCredits();
                    user.setAiCredits(user.getAiCredits() + 1);
                    user.setLastActive(LocalDateTime.now());
                    userRepository.save(user);
                    
                    // Log auto-refill transaction
                    logCreditTransaction(user.getId(), CreditTransaction.TransactionType.AUTO_REFILL, 1,
                                       balanceBefore, user.getAiCredits(), "Automatic credit refill", null);
                    
                    refilledCount++;
                } else {
                    skippedCount++;
                }
            }
            
            log.info("Auto-refill completed: {} users refilled, {} users at max credits (50)", 
                    refilledCount, skippedCount);
        } catch (Exception e) {
            log.error("Error during automatic credit refill", e);
        }
    }
}