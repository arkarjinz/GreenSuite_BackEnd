package com.app.greensuitetest.controller;

import com.app.greensuitetest.dto.ApiResponse;
import com.app.greensuitetest.service.AICreditService;
import com.app.greensuitetest.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/credits")
@RequiredArgsConstructor
public class AICreditController {

    private final AICreditService aiCreditService;
    private final SecurityUtil securityUtil;

    /**
     * Get current user's credit balance and stats
     */
    @GetMapping("/balance")
    @PreAuthorize("hasRole('OWNER') or hasRole('MANAGER') or hasRole('EMPLOYEE')")
    public ApiResponse getCreditBalance() {
        try {
            String userId = securityUtil.getCurrentUser().getId();
            Map<String, Object> creditStats = aiCreditService.getCreditStats(userId);
            
            return ApiResponse.success("Current AI credit information", creditStats);
        } catch (Exception e) {
            log.error("Error getting credit balance", e);
            return ApiResponse.error("Failed to retrieve credit balance: " + e.getMessage());
        }
    }

    /**
     * Check if user can chat (has enough credits)
     */
    @GetMapping("/can-chat")
    @PreAuthorize("hasRole('OWNER') or hasRole('MANAGER') or hasRole('EMPLOYEE')")
    public ApiResponse canUserChat() {
        try {
            String userId = securityUtil.getCurrentUser().getId();
            boolean canChat = aiCreditService.hasCreditsForChat(userId);
            int credits = aiCreditService.getUserCredits(userId);
            
            return ApiResponse.success("Chat availability status", Map.of(
                "canChat", canChat,
                "currentCredits", credits,
                "chatCost", 2,
                "possibleChats", credits / 2
            ));
        } catch (Exception e) {
            log.error("Error checking chat availability", e);
            return ApiResponse.error("Failed to check chat availability: " + e.getMessage());
        }
    }

    /**
     * Add credits to user account (Admin only or payment processing)
     */
    @PostMapping("/add")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse addCredits(
            @RequestParam String userId,
            @RequestParam int amount,
            @RequestParam(defaultValue = "Admin credit grant") String reason) {
        try {
            if (amount <= 0) {
                return ApiResponse.error("Credit amount must be positive");
            }

            int newBalance = aiCreditService.addCredits(userId, amount, reason);
            
            return ApiResponse.success("Credits added successfully", Map.of(
                "userId", userId,
                "creditsAdded", amount,
                "newBalance", newBalance,
                "reason", reason
            ));
        } catch (Exception e) {
            log.error("Error adding credits", e);
            return ApiResponse.error("Failed to add credits: " + e.getMessage());
        }
    }

    /**
     * Purchase credits (placeholder for payment integration)
     */
    @PostMapping("/purchase")
    @PreAuthorize("hasRole('OWNER') or hasRole('MANAGER') or hasRole('EMPLOYEE')")
    public ApiResponse purchaseCredits(
            @RequestParam int amount,
            @RequestParam String paymentMethod) {
        try {
            String userId = securityUtil.getCurrentUser().getId();
            
            if (amount <= 0) {
                return ApiResponse.error("Purchase amount must be positive");
            }

            // TODO: Integrate with payment processor (Stripe, PayPal, etc.)
            // For now, this is a placeholder that simulates a successful purchase
            
            // Simulate payment processing delay
            Thread.sleep(1000);
            
            // Add credits after "successful" payment
            int newBalance = aiCreditService.addCredits(userId, amount, "Credit purchase via " + paymentMethod);
            
            return ApiResponse.success("Credits purchased successfully!", Map.of(
                "creditsPurchased", amount,
                "newBalance", newBalance,
                "paymentMethod", paymentMethod,
                "transactionId", "sim_" + System.currentTimeMillis(), // Simulated transaction ID
                "note", "This is a simulation - integrate with real payment processor"
            ));
        } catch (Exception e) {
            log.error("Error purchasing credits", e);
            return ApiResponse.error("Failed to purchase credits: " + e.getMessage());
        }
    }

    /**
     * Get credit usage history (basic stats)
     */
    @GetMapping("/history")
    @PreAuthorize("hasRole('OWNER') or hasRole('MANAGER') or hasRole('EMPLOYEE')")
    public ApiResponse getCreditHistory() {
        try {
            String userId = securityUtil.getCurrentUser().getId();
            Map<String, Object> stats = aiCreditService.getCreditStats(userId);
            
            // TODO: Implement detailed credit transaction history
            // For now, return basic stats
            return ApiResponse.success("Credit usage statistics", Map.of(
                "currentStats", stats,
                "note", "Detailed transaction history coming soon!"
            ));
        } catch (Exception e) {
            log.error("Error getting credit history", e);
            return ApiResponse.error("Failed to retrieve credit history: " + e.getMessage());
        }
    }

    /**
     * Get credit pricing information
     */
    @GetMapping("/pricing")
    public ApiResponse getCreditPricing() {
        return ApiResponse.success("AI Credit Pricing Information", Map.of(
            "chatCost", 2,
            "pricingTiers", Map.of(
                "basic", Map.of(
                    "credits", 50,
                    "price", 4.99,
                    "currency", "USD",
                    "description", "Perfect for casual users"
                ),
                "standard", Map.of(
                    "credits", 150,
                    "price", 12.99,
                    "currency", "USD",
                    "description", "Great for regular users",
                    "bonus", "15% bonus credits"
                ),
                "premium", Map.of(
                    "credits", 350,
                    "price", 24.99,
                    "currency", "USD",
                    "description", "Best value for power users",
                    "bonus", "25% bonus credits"
                )
            ),
            "features", Map.of(
                "chatWithRin", "2 credits per conversation",
                "environmentalTips", "Free",
                "historyAccess", "Free",
                "personalityAnalysis", "Free"
            )
        ));
    }

    /**
     * Admin endpoint to view all users' credit stats
     */
    @GetMapping("/admin/overview")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse getCreditsOverview() {
        try {
            // TODO: Implement admin overview of all users' credits
            return ApiResponse.success("Credit system overview", Map.of(
                "note", "Admin credit overview coming soon!",
                "features", Map.of(
                    "totalUsersWithCredits", "TBD",
                    "totalCreditsInCirculation", "TBD",
                    "averageCreditsPerUser", "TBD",
                    "dailyCreditUsage", "TBD"
                )
            ));
        } catch (Exception e) {
            log.error("Error getting credits overview", e);
            return ApiResponse.error("Failed to retrieve credits overview: " + e.getMessage());
        }
    }
}