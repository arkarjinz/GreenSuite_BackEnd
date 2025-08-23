package com.app.greensuitetest.controller;

import com.app.greensuitetest.model.CreditTransaction;
import com.app.greensuitetest.service.TransactionService;
import com.app.greensuitetest.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
     * Get transaction history for current user
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
                "count", transactions.size()
            ));
        } catch (Exception e) {
            log.error("Error retrieving transaction history", e);
            return ApiResponse.error("Failed to retrieve transaction history: " + e.getMessage());
        }
    }
    
    /**
     * Get transaction statistics for current user
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('OWNER') or hasRole('MANAGER') or hasRole('EMPLOYEE')")
    public ApiResponse getTransactionStatistics() {
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
     * Get auto-refill transactions
     */
    @GetMapping("/auto-refills")
    @PreAuthorize("hasRole('OWNER') or hasRole('MANAGER') or hasRole('EMPLOYEE')")
    public ApiResponse getAutoRefillTransactions() {
        try {
            String userId = transactionService.getCurrentUserId();
            List<CreditTransaction> transactions = transactionService.getAutoRefillTransactions(userId);
            
            return ApiResponse.success("Auto-refill transactions retrieved successfully", Map.of(
                "transactions", transactions,
                "count", transactions.size()
            ));
        } catch (Exception e) {
            log.error("Error retrieving auto-refill transactions", e);
            return ApiResponse.error("Failed to retrieve auto-refill transactions: " + e.getMessage());
        }
    }
    
    /**
     * Get chat deduction transactions
     */
    @GetMapping("/chat-deductions")
    @PreAuthorize("hasRole('OWNER') or hasRole('MANAGER') or hasRole('EMPLOYEE')")
    public ApiResponse getChatDeductionTransactions() {
        try {
            String userId = transactionService.getCurrentUserId();
            List<CreditTransaction> transactions = transactionService.getChatDeductionTransactions(userId);
            
            return ApiResponse.success("Chat deduction transactions retrieved successfully", Map.of(
                "transactions", transactions,
                "count", transactions.size()
            ));
        } catch (Exception e) {
            log.error("Error retrieving chat deduction transactions", e);
            return ApiResponse.error("Failed to retrieve chat deduction transactions: " + e.getMessage());
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
} 