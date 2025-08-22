package com.app.greensuitetest.controller;

import com.app.greensuitetest.dto.ApiResponse;
import com.app.greensuitetest.dto.CreditPurchaseRequest;
import com.app.greensuitetest.dto.DepositRequest;
import com.app.greensuitetest.dto.PaymentAccountRequest;
import com.app.greensuitetest.model.PaymentAccount;
import com.app.greensuitetest.model.PaymentTransaction;
import com.app.greensuitetest.service.CustomPaymentService;
import com.app.greensuitetest.service.PaymentAnalyticsService;
import com.app.greensuitetest.exception.PaymentProcessingException;
import com.app.greensuitetest.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class CustomPaymentController {
    
    private final CustomPaymentService customPaymentService;
    private final PaymentAnalyticsService paymentAnalyticsService;
    private final SecurityUtil securityUtil;
    
    /**
     * Create a payment account for the current user
     */
    @PostMapping("/account/create")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<ApiResponse> createPaymentAccount(@Valid @RequestBody PaymentAccountRequest request) {
        try {
        PaymentAccount account = customPaymentService.createPaymentAccount(request);
            
            return ResponseEntity.ok(ApiResponse.success("Payment account created successfully", Map.of(
                "account", account,
                "accountNumber", account.getAccountNumber(),
                "status", account.getStatus(),
                "verificationLevel", account.getVerificationLevel(),
                "dailyLimit", account.getDailyLimit(),
                "monthlyLimit", account.getMonthlyLimit()
            )));
        } catch (PaymentProcessingException e) {
            log.warn("Payment account creation failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to create payment account: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error creating payment account", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("An unexpected error occurred"));
        }
    }
    
    /**
     * Get user's payment account with enhanced details
     */
    @GetMapping("/account")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<ApiResponse> getPaymentAccount() {
        try {
        PaymentAccount account = customPaymentService.getUserPaymentAccount();
            
            return ResponseEntity.ok(ApiResponse.success("Payment account retrieved successfully", Map.of(
                "account", account,
                "availableBalance", account.getAvailableBalance(),
                "remainingDailyLimit", account.getRemainingDailyLimit(),
                "remainingMonthlyLimit", account.getRemainingMonthlyLimit(),
                "isActive", account.isActive(),
                "transactionCount", account.getTransactionCount(),
                "successfulTransactionCount", account.getSuccessfulTransactionCount()
            )));
        } catch (PaymentProcessingException e) {
            log.warn("Payment account retrieval failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to retrieve payment account: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error retrieving payment account", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("An unexpected error occurred"));
        }
    }
    
    /**
     * Get comprehensive account statistics
     */
    @GetMapping("/account/statistics")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<ApiResponse> getAccountStatistics() {
        try {
        Map<String, Object> stats = customPaymentService.getAccountStatistics();
            
            return ResponseEntity.ok(ApiResponse.success("Account statistics retrieved successfully", stats));
        } catch (Exception e) {
            log.error("Error retrieving account statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve account statistics: " + e.getMessage()));
        }
    }
    
    /**
     * Process a deposit to user's payment account with fraud detection
     */
    @PostMapping("/deposit")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<ApiResponse> processDeposit(@Valid @RequestBody DepositRequest request) {
        try {
        PaymentTransaction transaction = customPaymentService.processDeposit(request);
            
            return ResponseEntity.ok(ApiResponse.success("Deposit processed successfully", Map.of(
                "transaction", transaction,
                "transactionId", transaction.getTransactionId(),
                "amount", transaction.getAmount(),
                "newBalance", transaction.getBalanceAfter(),
                "status", transaction.getStatus(),
                "riskLevel", transaction.getRiskLevel(),
                "riskScore", transaction.getRiskScore()
            )));
        } catch (PaymentProcessingException e) {
            log.warn("Deposit processing failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Deposit failed: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error processing deposit", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("An unexpected error occurred"));
        }
    }
    
    /**
     * Purchase credits using account balance with enhanced security
     */
    @PostMapping("/credits/purchase")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<ApiResponse> purchaseCredits(@Valid @RequestBody CreditPurchaseRequest request) {
        try {
        PaymentTransaction transaction = customPaymentService.purchaseCredits(request);
            
            return ResponseEntity.ok(ApiResponse.success("Credits purchased successfully", Map.of(
                "transaction", transaction,
                "transactionId", transaction.getTransactionId(),
                "creditsPurchased", transaction.getCreditsPurchased(),
                "amount", transaction.getAmount(),
                "newAccountBalance", transaction.getBalanceAfter(),
                "newCreditBalance", transaction.getCreditBalanceAfter(),
                "status", transaction.getStatus(),
                "riskLevel", transaction.getRiskLevel(),
                "riskScore", transaction.getRiskScore(),
                "creditPackage", transaction.getCreditPackage()
            )));
        } catch (PaymentProcessingException e) {
            log.warn("Credit purchase failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Credit purchase failed: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error purchasing credits", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("An unexpected error occurred"));
        }
    }
    
    /**
     * Get available credit packages with detailed information
     */
    @GetMapping("/credits/packages")
    public ResponseEntity<ApiResponse> getCreditPackages() {
        try {
        List<Map<String, Object>> packages = customPaymentService.getCreditPackages();
            
            return ResponseEntity.ok(ApiResponse.success("Credit packages retrieved successfully", Map.of(
                "packages", packages,
                "currency", "USD",
                "note", "All packages include instant credit delivery and fraud protection"
            )));
        } catch (Exception e) {
            log.error("Error retrieving credit packages", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve credit packages: " + e.getMessage()));
        }
    }
    
    /**
     * Get paginated transaction history for user
     */
    @GetMapping("/transactions")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<ApiResponse> getTransactionHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {
        try {
            // TODO: Implement pagination and filtering in CustomPaymentService
        List<PaymentTransaction> transactions = customPaymentService.getTransactionHistory();
            
            return ResponseEntity.ok(ApiResponse.success("Transaction history retrieved successfully", Map.of(
                "transactions", transactions,
                "page", page,
                "size", size,
                "totalCount", transactions.size(),
                "hasNext", false // TODO: implement proper pagination
            )));
        } catch (Exception e) {
            log.error("Error retrieving transaction history", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve transaction history: " + e.getMessage()));
        }
    }
    
    /**
     * Get specific transaction by ID
     */
    @GetMapping("/transactions/{transactionId}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<ApiResponse> getTransaction(@PathVariable String transactionId) {
        try {
        PaymentTransaction transaction = customPaymentService.getTransaction(transactionId);
            
            return ResponseEntity.ok(ApiResponse.success("Transaction retrieved successfully", Map.of(
                "transaction", transaction,
                "fraudAnalysis", transaction.getFraudAnalysis(),
                "metadata", transaction.getMetadata()
            )));
        } catch (PaymentProcessingException e) {
            log.warn("Transaction retrieval failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Transaction not found: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error retrieving transaction", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve transaction: " + e.getMessage()));
        }
    }
    
    /**
     * Get payment analytics dashboard
     */
    @GetMapping("/analytics/dashboard")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponse> getPaymentDashboard() {
        try {
            Map<String, Object> dashboard = paymentAnalyticsService.getPaymentDashboard();
            
            return ResponseEntity.ok(ApiResponse.success("Payment dashboard retrieved successfully", dashboard));
        } catch (Exception e) {
            log.error("Error retrieving payment dashboard", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve payment dashboard: " + e.getMessage()));
        }
    }
    
    /**
     * Get user transaction analytics
     */
    @GetMapping("/analytics/user")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<ApiResponse> getUserAnalytics(@RequestParam(defaultValue = "30") int days) {
        try {
            String userId = securityUtil.getCurrentUser().getId();
            Map<String, Object> analytics = paymentAnalyticsService.getUserTransactionAnalytics(userId, days);
            
            return ResponseEntity.ok(ApiResponse.success("User analytics retrieved successfully", analytics));
        } catch (Exception e) {
            log.error("Error retrieving user analytics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve user analytics: " + e.getMessage()));
        }
    }
    
    /**
     * Get fraud trends analysis (Admin only)
     */
    @GetMapping("/analytics/fraud-trends")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse> getFraudTrends(@RequestParam(defaultValue = "30") int days) {
        try {
            Map<String, Object> trends = paymentAnalyticsService.getFraudTrendAnalysis(days);
            
            return ResponseEntity.ok(ApiResponse.success("Fraud trends retrieved successfully", trends));
        } catch (Exception e) {
            log.error("Error retrieving fraud trends", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve fraud trends: " + e.getMessage()));
        }
    }
} 