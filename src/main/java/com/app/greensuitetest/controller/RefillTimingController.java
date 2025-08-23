package com.app.greensuitetest.controller;

import com.app.greensuitetest.dto.ApiResponse;
import com.app.greensuitetest.service.AICreditService;
import com.app.greensuitetest.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/refill")
@RequiredArgsConstructor
public class RefillTimingController {
    
    private final AICreditService aiCreditService;
    private final TransactionService transactionService;
    
    /**
     * Get current refill timing configuration
     */
    @GetMapping("/timing")
    public ApiResponse getRefillTiming() {
        try {
            Map<String, Object> timingInfo = Map.of(
                "enabled", true,
                "interval", "5 minutes",
                "intervalMs", 300000,
                "amount", 1,
                "maxCredits", 50,
                "maxLimit", 50,
                "lastRefill", LocalDateTime.now(),
                "nextRefill", LocalDateTime.now().plusMinutes(5),
                "description", "Credits are automatically refilled every 5 minutes. Maximum 50 credits total per user."
            );
            
            return ApiResponse.success("Refill timing configuration retrieved", timingInfo);
        } catch (Exception e) {
            log.error("Error getting refill timing configuration", e);
            return ApiResponse.error("Failed to get refill timing: " + e.getMessage());
        }
    }
    
    /**
     * Get refill statistics for current user
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('OWNER') or hasRole('MANAGER') or hasRole('EMPLOYEE')")
    public ApiResponse getRefillStats() {
        try {
            String userId = transactionService.getCurrentUserId();
            List<com.app.greensuitetest.model.CreditTransaction> refillTransactions = 
                transactionService.getAutoRefillTransactions(userId);
            
            int totalRefilled = refillTransactions.stream()
                .mapToInt(com.app.greensuitetest.model.CreditTransaction::getAmount)
                .sum();
            
            int currentCredits = aiCreditService.getUserCredits(userId);
            
            Map<String, Object> stats = Map.of(
                "totalRefilled", totalRefilled,
                "currentCredits", currentCredits,
                "maxCredits", 50,
                "refillCount", refillTransactions.size(),
                "lastRefill", refillTransactions.isEmpty() ? null : 
                    refillTransactions.get(0).getTimestamp(),
                "eligibleForRefill", currentCredits < 50,
                "nextRefillIn", currentCredits >= 50 ? "Not eligible (at max)" : "5 minutes",
                "creditsRemaining", Math.max(0, 50 - currentCredits)
            );
            
            return ApiResponse.success("Refill statistics retrieved", stats);
        } catch (Exception e) {
            log.error("Error getting refill statistics", e);
            return ApiResponse.error("Failed to get refill statistics: " + e.getMessage());
        }
    }
    
    /**
     * Get refill history for current user
     */
    @GetMapping("/history")
    @PreAuthorize("hasRole('OWNER') or hasRole('MANAGER') or hasRole('EMPLOYEE')")
    public ApiResponse getRefillHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            String userId = transactionService.getCurrentUserId();
            List<com.app.greensuitetest.model.CreditTransaction> refillTransactions = 
                transactionService.getAutoRefillTransactions(userId);
            
            // Simple pagination
            int start = page * size;
            int end = Math.min(start + size, refillTransactions.size());
            List<com.app.greensuitetest.model.CreditTransaction> paginatedTransactions = 
                refillTransactions.subList(start, end);
            
            Map<String, Object> history = Map.of(
                "refills", paginatedTransactions,
                "page", page,
                "size", size,
                "total", refillTransactions.size(),
                "hasMore", end < refillTransactions.size()
            );
            
            return ApiResponse.success("Refill history retrieved", history);
        } catch (Exception e) {
            log.error("Error getting refill history", e);
            return ApiResponse.error("Failed to get refill history: " + e.getMessage());
        }
    }
    
    /**
     * Get system-wide refill analytics (Admin only)
     */
    @GetMapping("/analytics")
    @PreAuthorize("hasRole('OWNER') or hasRole('MANAGER')")
    public ApiResponse getRefillAnalytics() {
        try {
            Map<String, Object> analytics = aiCreditService.getCreditSystemAnalytics();
            
            Map<String, Object> refillAnalytics = Map.of(
                "systemAnalytics", analytics,
                "refillConfig", Map.of(
                    "interval", "5 minutes",
                    "amount", 1,
                    "maxCredits", 50,
                    "maxLimit", 50,
                    "enabled", true
                ),
                "lastUpdated", LocalDateTime.now()
            );
            
            return ApiResponse.success("Refill analytics retrieved", refillAnalytics);
        } catch (Exception e) {
            log.error("Error getting refill analytics", e);
            return ApiResponse.error("Failed to get refill analytics: " + e.getMessage());
        }
    }
    
    /**
     * Trigger manual refill for current user (Admin only)
     */
    @PostMapping("/manual")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse triggerManualRefill(@RequestParam String userId) {
        try {
            int creditsBefore = aiCreditService.getUserCredits(userId);
            aiCreditService.addCredits(userId, 1, "Manual refill triggered by admin");
            int creditsAfter = aiCreditService.getUserCredits(userId);
            
            Map<String, Object> result = Map.of(
                "userId", userId,
                "creditsBefore", creditsBefore,
                "creditsAfter", creditsAfter,
                "refilled", creditsAfter - creditsBefore,
                "maxCredits", 50,
                "timestamp", LocalDateTime.now()
            );
            
            return ApiResponse.success("Manual refill completed", result);
        } catch (Exception e) {
            log.error("Error triggering manual refill", e);
            return ApiResponse.error("Failed to trigger manual refill: " + e.getMessage());
        }
    }
    
    /**
     * Get refill status for all users (Admin only)
     */
    @GetMapping("/status/all")
    @PreAuthorize("hasRole('OWNER') or hasRole('MANAGER')")
    public ApiResponse getAllUsersRefillStatus() {
        try {
            Map<String, Object> status = Map.of(
                "totalUsers", "N/A", // Would need user service
                "usersEligibleForRefill", "N/A",
                "usersAtMaxCredits", "N/A",
                "lastSystemRefill", LocalDateTime.now(),
                "nextSystemRefill", LocalDateTime.now().plusMinutes(5),
                "refillEnabled", true
            );
            
            return ApiResponse.success("All users refill status retrieved", status);
        } catch (Exception e) {
            log.error("Error getting all users refill status", e);
            return ApiResponse.error("Failed to get refill status: " + e.getMessage());
        }
    }
} 