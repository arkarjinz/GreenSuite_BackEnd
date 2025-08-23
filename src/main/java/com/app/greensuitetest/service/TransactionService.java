package com.app.greensuitetest.service;

import com.app.greensuitetest.model.CreditTransaction;
import com.app.greensuitetest.repository.CreditTransactionRepository;
import com.app.greensuitetest.service.AICreditService;
import com.app.greensuitetest.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {
    
    private final CreditTransactionRepository creditTransactionRepository;
    private final SecurityUtil securityUtil;
    private final AICreditService aiCreditService;
    
    /**
     * Log a credit transaction
     */
    @Transactional
    public CreditTransaction logCreditTransaction(String userId, CreditTransaction.TransactionType type, 
                                                 int amount, int balanceBefore, int balanceAfter, 
                                                 String reason, String conversationId) {
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
            
            CreditTransaction savedTransaction = creditTransactionRepository.save(transaction);
            log.info("Logged credit transaction: {} for user {} - {} credits ({} -> {})", 
                    type, userId, amount, balanceBefore, balanceAfter);
            
            return savedTransaction;
        } catch (Exception e) {
            log.error("Error logging credit transaction for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to log credit transaction", e);
        }
    }
    
    /**
     * Get transaction history for user
     */
    public List<CreditTransaction> getUserTransactionHistory(String userId, int page, int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            return creditTransactionRepository.findByUserIdOrderByTimestampDesc(userId, pageable).getContent();
        } catch (Exception e) {
            log.error("Error getting transaction history for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to get transaction history", e);
        }
    }
    
    /**
     * Get transaction history by type
     */
    public List<CreditTransaction> getTransactionsByType(String userId, CreditTransaction.TransactionType type) {
        try {
            return creditTransactionRepository.findByUserIdAndTypeOrderByTimestampDesc(userId, type);
        } catch (Exception e) {
            log.error("Error getting transactions by type for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to get transactions by type", e);
        }
    }
    
    /**
     * Get auto-refill transactions
     */
    public List<CreditTransaction> getAutoRefillTransactions(String userId) {
        return getTransactionsByType(userId, CreditTransaction.TransactionType.AUTO_REFILL);
    }
    
    /**
     * Get chat deduction transactions
     */
    public List<CreditTransaction> getChatDeductionTransactions(String userId) {
        return getTransactionsByType(userId, CreditTransaction.TransactionType.CHAT_DEDUCTION);
    }
    
    /**
     * Get transaction statistics for user
     */
    public Map<String, Object> getUserTransactionStats(String userId) {
        try {
            List<CreditTransaction> allTransactions = creditTransactionRepository.findByUserId(userId);
            
            int totalCreditsRefilled = allTransactions.stream()
                    .filter(t -> t.getType() == CreditTransaction.TransactionType.AUTO_REFILL)
                    .mapToInt(CreditTransaction::getAmount)
                    .sum();
            
            int totalCreditsUsed = allTransactions.stream()
                    .filter(t -> t.getType() == CreditTransaction.TransactionType.CHAT_DEDUCTION)
                    .mapToInt(t -> Math.abs(t.getAmount()))
                    .sum();
            
            return Map.of(
                "totalCreditsRefilled", totalCreditsRefilled,
                "totalCreditsUsed", totalCreditsUsed,
                "transactionCount", allTransactions.size(),
                "lastTransaction", allTransactions.stream()
                        .max((t1, t2) -> t1.getTimestamp().compareTo(t2.getTimestamp()))
                        .map(CreditTransaction::getTimestamp)
                        .orElse(null)
            );
        } catch (Exception e) {
            log.error("Error getting transaction stats for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to get transaction stats", e);
        }
    }
    
    /**
     * Get current user ID
     */
    public String getCurrentUserId() {
        return securityUtil.getCurrentUser().getId();
    }
} 