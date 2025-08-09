package com.app.greensuitetest.service;

import com.app.greensuitetest.exception.InsufficientCreditsException;
import com.app.greensuitetest.exception.EntityNotFoundException;
import com.app.greensuitetest.model.User;
import com.app.greensuitetest.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;

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
    
    private static final int CHAT_COST = 2;
    private static final int DEFAULT_CREDITS = 50;
    
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
        
        user.setAiCredits(user.getAiCredits() - CHAT_COST);
        user.setLastActive(LocalDateTime.now());
        
        User savedUser = userRepository.save(user);
        
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
     * Add credits to user account (respects subscription tier limits)
     */
    public int addCredits(String userId, int amount, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        
        int maxCredits = user.getMaxCredits();
        int currentCredits = user.getAiCredits();
        int actualAmount = Math.min(amount, maxCredits - currentCredits);
        
        if (actualAmount <= 0) {
            log.warn("Cannot add credits to user {}: already at max ({} credits)", userId, maxCredits);
            return currentCredits;
        }
        
        int newBalance = currentCredits + actualAmount;
        user.setAiCredits(newBalance);
        user.setLastActive(LocalDateTime.now());
        
        User savedUser = userRepository.save(user);
        
        log.info("Added {} AI credits to user {} ({}). New balance: {}/{}", 
                actualAmount, userId, reason, newBalance, maxCredits);
        
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
        
        if (isLow) {
            stats.put("warning", "You're running low on AI credits!");
        }
        
        return stats;
    }
    
    /**
     * Refund credits (e.g., if chat fails)
     */
    public int refundCredits(String userId, int amount, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        
        int newBalance = user.getAiCredits() + amount;
        user.setAiCredits(newBalance);
        
        User savedUser = userRepository.save(user);
        
        log.info("Refunded {} AI credits to user {} ({}). New balance: {}", 
                amount, userId, reason, newBalance);
        
        return savedUser.getAiCredits();
    }
    
    /**
     * Automatically refill 1 credit for all users every 5 minutes
     * This runs every 5 minutes (300,000 milliseconds)
     * Respects subscription tier credit limits
     */
    @Scheduled(fixedRate = 300000) // 5 minutes = 300,000 milliseconds
    public void autoRefillCredits() {
        try {
            List<User> allUsers = userRepository.findAll();
            int refilledCount = 0;
            int skippedCount = 0;
            
            for (User user : allUsers) {
                // Only add credit if user hasn't reached their tier's maximum
                if (user.canReceiveCredits()) {
                    user.setAiCredits(user.getAiCredits() + 1);
                    user.setLastActive(LocalDateTime.now());
                    userRepository.save(user);
                    refilledCount++;
                } else {
                    skippedCount++;
                }
            }
            
            log.info("Auto-refill completed: {} users refilled, {} users at max credits", 
                    refilledCount, skippedCount);
        } catch (Exception e) {
            log.error("Error during automatic credit refill", e);
        }
    }
}