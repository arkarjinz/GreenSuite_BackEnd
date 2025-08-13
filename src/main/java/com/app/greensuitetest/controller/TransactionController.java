package com.app.greensuitetest.controller;

import com.app.greensuitetest.dto.ApiResponse;
import com.app.greensuitetest.model.CreditTransaction;
import com.app.greensuitetest.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {
    
    private final TransactionService transactionService;
    
    /**
     * Get user's transaction history with pagination
     */
    @GetMapping("/history")
    @PreAuthorize("hasRole('OWNER') or hasRole('MANAGER') or hasRole('EMPLOYEE')")
    public ApiResponse getTransactionHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            String userId = transactionService.getCurrentUserId();
            List<CreditTransaction> transactions = transactionService.getUserTransactionHistory(userId, page, size);
            
            return ApiResponse.success("Transaction history retrieved successfully", Map.of(
                "transactions", transactions,
                "page", page,
                "size", size,
                "totalTransactions", transactions.size()
            ));
        } catch (Exception e) {
            log.error("Error retrieving transaction history", e);
            return ApiResponse.error("Failed to retrieve transaction history: " + e.getMessage());
        }
    }
    
    /**
     * Get transaction statistics for user
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('OWNER') or hasRole('MANAGER') or hasRole('EMPLOYEE')")
    public ApiResponse getTransactionStats() {
        try {
            String userId = transactionService.getCurrentUserId();
            Map<String, Object> stats = transactionService.getUserTransactionStats(userId);
            
            return ApiResponse.success("Transaction statistics retrieved successfully", stats);
        } catch (Exception e) {
            log.error("Error retrieving transaction statistics", e);
            return ApiResponse.error("Failed to retrieve transaction statistics: " + e.getMessage());
        }
    }
    
    /**
     * Get Stripe payment transactions
     */
    @GetMapping("/stripe-payments")
    @PreAuthorize("hasRole('OWNER') or hasRole('MANAGER') or hasRole('EMPLOYEE')")
    public ApiResponse getStripePaymentTransactions() {
        try {
            String userId = transactionService.getCurrentUserId();
            List<CreditTransaction> transactions = transactionService.getStripePaymentTransactions(userId);
            
            return ApiResponse.success("Stripe payment transactions retrieved successfully", Map.of(
                "transactions", transactions,
                "count", transactions.size()
            ));
        } catch (Exception e) {
            log.error("Error retrieving Stripe payment transactions", e);
            return ApiResponse.error("Failed to retrieve Stripe payment transactions: " + e.getMessage());
        }
    }
    
    /**
     * Get account deposit transactions
     */
    @GetMapping("/account-deposits")
    @PreAuthorize("hasRole('OWNER') or hasRole('MANAGER') or hasRole('EMPLOYEE')")
    public ApiResponse getAccountDepositTransactions() {
        try {
            String userId = transactionService.getCurrentUserId();
            List<CreditTransaction> transactions = transactionService.getAccountDepositTransactions(userId);
            
            return ApiResponse.success("Account deposit transactions retrieved successfully", Map.of(
                "transactions", transactions,
                "count", transactions.size()
            ));
        } catch (Exception e) {
            log.error("Error retrieving account deposit transactions", e);
            return ApiResponse.error("Failed to retrieve account deposit transactions: " + e.getMessage());
        }
    }
    
    /**
     * Get credit purchase transactions
     */
    @GetMapping("/credit-purchases")
    @PreAuthorize("hasRole('OWNER') or hasRole('MANAGER') or hasRole('EMPLOYEE')")
    public ApiResponse getCreditPurchaseTransactions() {
        try {
            String userId = transactionService.getCurrentUserId();
            List<CreditTransaction> transactions = transactionService.getCreditPurchaseTransactions(userId);
            
            return ApiResponse.success("Credit purchase transactions retrieved successfully", Map.of(
                "transactions", transactions,
                "count", transactions.size()
            ));
        } catch (Exception e) {
            log.error("Error retrieving credit purchase transactions", e);
            return ApiResponse.error("Failed to retrieve credit purchase transactions: " + e.getMessage());
        }
    }
    
    /**
     * Get transaction by Stripe payment intent ID
     */
    @GetMapping("/by-payment-intent/{paymentIntentId}")
    @PreAuthorize("hasRole('OWNER') or hasRole('MANAGER') or hasRole('EMPLOYEE')")
    public ApiResponse getTransactionByPaymentIntentId(@PathVariable String paymentIntentId) {
        try {
            CreditTransaction transaction = transactionService.getTransactionByStripePaymentIntentId(paymentIntentId);
            
            if (transaction == null) {
                return ApiResponse.error("Transaction not found for payment intent ID: " + paymentIntentId);
            }
            
            return ApiResponse.success("Transaction retrieved successfully", transaction);
        } catch (Exception e) {
            log.error("Error retrieving transaction by payment intent ID", e);
            return ApiResponse.error("Failed to retrieve transaction: " + e.getMessage());
        }
    }
    
    /**
     * Get transactions by type
     */
    @GetMapping("/by-type/{type}")
    @PreAuthorize("hasRole('OWNER') or hasRole('MANAGER') or hasRole('EMPLOYEE')")
    public ApiResponse getTransactionsByType(@PathVariable String type) {
        try {
            String userId = transactionService.getCurrentUserId();
            CreditTransaction.TransactionType transactionType;
            
            try {
                transactionType = CreditTransaction.TransactionType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ApiResponse.error("Invalid transaction type: " + type);
            }
            
            List<CreditTransaction> transactions = transactionService.getTransactionsByType(userId, transactionType);
            
            return ApiResponse.success("Transactions retrieved successfully", Map.of(
                "transactions", transactions,
                "type", transactionType.name(),
                "count", transactions.size()
            ));
        } catch (Exception e) {
            log.error("Error retrieving transactions by type", e);
            return ApiResponse.error("Failed to retrieve transactions: " + e.getMessage());
        }
    }
    
    /**
     * Get available transaction types
     */
    @GetMapping("/types")
    @PreAuthorize("hasRole('OWNER') or hasRole('MANAGER') or hasRole('EMPLOYEE')")
    public ApiResponse getAvailableTransactionTypes() {
        try {
            CreditTransaction.TransactionType[] types = CreditTransaction.TransactionType.values();
            
            return ApiResponse.success("Available transaction types retrieved successfully", Map.of(
                "types", types,
                "count", types.length
            ));
        } catch (Exception e) {
            log.error("Error retrieving transaction types", e);
            return ApiResponse.error("Failed to retrieve transaction types: " + e.getMessage());
        }
    }

    /**
     * Get refund transactions for current user
     */
    @GetMapping("/refunds")
    @PreAuthorize("hasRole('OWNER') or hasRole('MANAGER') or hasRole('EMPLOYEE')")
    public ApiResponse getRefundTransactions() {
        try {
            String userId = transactionService.getCurrentUserId();
            List<CreditTransaction> refunds = transactionService.getRefundTransactions(userId);
            
            List<Map<String, Object>> refundData = refunds.stream()
                .map(refund -> {
                    Map<String, Object> refundMap = new HashMap<>();
                    refundMap.put("id", refund.getId());
                    refundMap.put("amount", refund.getAmount());
                    refundMap.put("paymentAmount", refund.getPaymentAmount());
                    refundMap.put("paymentCurrency", refund.getPaymentCurrency());
                    refundMap.put("reason", refund.getReason());
                    refundMap.put("stripePaymentIntentId", refund.getStripePaymentIntentId());
                    refundMap.put("stripeTransactionId", refund.getStripeTransactionId());
                    refundMap.put("timestamp", refund.getTimestamp());
                    return refundMap;
                })
                .collect(Collectors.toList());
            
            return ApiResponse.success("Refund transactions retrieved successfully", Map.of(
                "refunds", refundData,
                "count", refunds.size()
            ));
        } catch (Exception e) {
            log.error("Error retrieving refund transactions", e);
            return ApiResponse.error("Failed to retrieve refund transactions: " + e.getMessage());
        }
    }
    
    /**
     * Get refund statistics for current user
     */
    @GetMapping("/refunds/stats")
    @PreAuthorize("hasRole('OWNER') or hasRole('MANAGER') or hasRole('EMPLOYEE')")
    public ApiResponse getRefundStatistics() {
        try {
            String userId = transactionService.getCurrentUserId();
            Map<String, Object> stats = transactionService.getRefundStatistics(userId);
            return ApiResponse.success("Refund statistics retrieved successfully", stats);
        } catch (Exception e) {
            log.error("Error retrieving refund statistics", e);
            return ApiResponse.error("Failed to retrieve refund statistics: " + e.getMessage());
        }
    }
} 