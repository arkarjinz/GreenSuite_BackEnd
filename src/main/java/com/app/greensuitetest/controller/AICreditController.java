package com.app.greensuitetest.controller;

import com.app.greensuitetest.dto.ApiResponse;
import com.app.greensuitetest.dto.CreditHistoryDto;
import com.app.greensuitetest.model.CreditTransaction;
import com.app.greensuitetest.service.AICreditService;
import com.app.greensuitetest.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

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
     * Purchase credits via account balance (replaces Stripe redirect)
     */
    @PostMapping("/purchase")
    @PreAuthorize("hasRole('OWNER') or hasRole('MANAGER') or hasRole('EMPLOYEE')")
    public ApiResponse purchaseCredits(
            @RequestParam String creditPackage,
            @RequestParam(defaultValue = "USD") String currency) {
        try {
            // Validate credit package
            if (!List.of("basic", "standard", "premium", "enterprise").contains(creditPackage.toLowerCase())) {
                return ApiResponse.error("Invalid credit package. Available: basic, standard, premium, enterprise");
            }
            
            String userId = securityUtil.getCurrentUser().getId();
            
            // Get credit pricing information for the selected package
            Map<String, Object> packageInfo = getCreditPackageInfo(creditPackage);
            
            return ApiResponse.success("Credit purchase information", Map.of(
                "message", "To complete your credit purchase, please use the payment system",
                "selectedPackage", packageInfo,
                "steps", List.of(
                    "1. Ensure you have sufficient balance in your payment account",
                    "2. Use endpoint: POST /api/payment/credits/purchase",
                    "3. Credits will be added automatically upon successful payment"
                ),
                "endpoint", "/api/payment/credits/purchase",
                "method", "POST",
                "currentCredits", aiCreditService.getUserCredits(userId),
                "requiredBody", Map.of(
                    "creditPackage", creditPackage,
                    "currency", currency
                )
            ));
        } catch (Exception e) {
            log.error("Error with credit purchase information", e);
            return ApiResponse.error("Failed to get credit purchase information: " + e.getMessage());
        }
    }
    
    /**
     * Get credit package information
     */
    private Map<String, Object> getCreditPackageInfo(String packageName) {
        Map<String, Map<String, Object>> packages = Map.of(
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
            ),
            "enterprise", Map.of(
                "credits", 500,
                "price", 39.99,
                "currency", "USD",
                "description", "Maximum productivity"
            )
        );
        
        return packages.getOrDefault(packageName.toLowerCase(), new HashMap<>());
    }

    /**
     * Get simple credit usage history
     */
    @GetMapping("/history")
    @PreAuthorize("hasRole('OWNER') or hasRole('MANAGER') or hasRole('EMPLOYEE')")
    public ApiResponse getCreditHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            String userId = securityUtil.getCurrentUser().getId();
            
            CreditHistoryDto history = aiCreditService.getCreditHistory(userId, page, size);
            
            return ApiResponse.success("Credit usage history", history);
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
    @GetMapping("/credit-overview")
    @PreAuthorize("hasRole('OWNER') or hasRole('MANAGER') or hasRole('EMPLOYEE')")
    public ApiResponse getCreditsOverview() {
        try {
            Map<String, Object> analytics = aiCreditService.getCreditSystemAnalytics();
            
            return ApiResponse.success("Credit system overview", analytics);
        } catch (Exception e) {
            log.error("Error getting credits overview", e);
            return ApiResponse.error("Failed to retrieve credits overview: " + e.getMessage());
        }
    }
}