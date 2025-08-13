package com.app.greensuitetest.service;

import com.app.greensuitetest.model.CreditTransaction;
import com.app.greensuitetest.payment.model.Payment;
import com.app.greensuitetest.payment.repository.PaymentRepository;
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
    private final PaymentRepository paymentRepository;
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
     * Log a Stripe payment transaction
     */
    @Transactional
    public CreditTransaction logStripePaymentTransaction(String userId, String stripePaymentIntentId, 
                                                        String stripeCustomerId, double paymentAmount, 
                                                        String paymentCurrency, String paymentMethod, 
                                                        String creditPackage, int creditAmount, 
                                                        int balanceBefore, int balanceAfter) {
        try {
            CreditTransaction transaction = CreditTransaction.builder()
                    .userId(userId)
                    .type(CreditTransaction.TransactionType.STRIPE_PAYMENT)
                    .amount(creditAmount)
                    .balanceBefore(balanceBefore)
                    .balanceAfter(balanceAfter)
                    .reason("Stripe payment - " + creditPackage + " package")
                    .stripePaymentIntentId(stripePaymentIntentId)
                    .stripeCustomerId(stripeCustomerId)
                    .paymentAmount(paymentAmount)
                    .paymentCurrency(paymentCurrency)
                    .paymentMethod(paymentMethod)
                    .creditPackage(creditPackage)
                    .timestamp(LocalDateTime.now())
                    .build();
            
            CreditTransaction savedTransaction = creditTransactionRepository.save(transaction);
            log.info("Logged Stripe payment transaction: {} for user {} - {} credits for ${} {}", 
                    stripePaymentIntentId, userId, creditAmount, paymentAmount, paymentCurrency);
            
            return savedTransaction;
        } catch (Exception e) {
            log.error("Error logging Stripe payment transaction for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to log Stripe payment transaction", e);
        }
    }
    
    /**
     * Log an account deposit transaction
     */
    @Transactional
    public CreditTransaction logAccountDepositTransaction(String userId, String accountNumber, 
                                                         double depositAmount, double balanceBefore, 
                                                         double balanceAfter, String stripePaymentIntentId, 
                                                         String paymentMethod) {
        try {
            CreditTransaction transaction = CreditTransaction.builder()
                    .userId(userId)
                    .type(CreditTransaction.TransactionType.ACCOUNT_DEPOSIT)
                    .amount(0) // No credit change for account deposits
                    .balanceBefore(0) // Credit balance unchanged
                    .balanceAfter(0) // Credit balance unchanged
                    .reason("Account deposit via " + paymentMethod)
                    .stripePaymentIntentId(stripePaymentIntentId)
                    .accountNumber(accountNumber)
                    .accountBalanceBefore(balanceBefore)
                    .accountBalanceAfter(balanceAfter)
                    .paymentAmount(depositAmount)
                    .paymentCurrency("USD")
                    .paymentMethod(paymentMethod)
                    .timestamp(LocalDateTime.now())
                    .build();
            
            CreditTransaction savedTransaction = creditTransactionRepository.save(transaction);
            log.info("Logged account deposit transaction: {} for user {} - ${} to account {} ({} -> {})", 
                    stripePaymentIntentId, userId, depositAmount, accountNumber, balanceBefore, balanceAfter);
            
            return savedTransaction;
        } catch (Exception e) {
            log.error("Error logging account deposit transaction for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to log account deposit transaction", e);
        }
    }
    
    /**
     * Log an account withdrawal transaction (for credit purchases)
     */
    @Transactional
    public CreditTransaction logAccountWithdrawalTransaction(String userId, String accountNumber, 
                                                            double withdrawalAmount, double balanceBefore, 
                                                            double balanceAfter, int creditAmount, 
                                                            int creditBalanceBefore, int creditBalanceAfter) {
        try {
            CreditTransaction transaction = CreditTransaction.builder()
                    .userId(userId)
                    .type(CreditTransaction.TransactionType.ACCOUNT_WITHDRAWAL)
                    .amount(creditAmount)
                    .balanceBefore(creditBalanceBefore)
                    .balanceAfter(creditBalanceAfter)
                    .reason("Credit purchase from account balance")
                    .accountNumber(accountNumber)
                    .accountBalanceBefore(balanceBefore)
                    .accountBalanceAfter(balanceAfter)
                    .paymentAmount(withdrawalAmount)
                    .paymentCurrency("USD")
                    .paymentMethod("ACCOUNT_BALANCE")
                    .timestamp(LocalDateTime.now())
                    .build();
            
            CreditTransaction savedTransaction = creditTransactionRepository.save(transaction);
            log.info("Logged account withdrawal transaction for user {} - ${} from account {} for {} credits", 
                    userId, withdrawalAmount, accountNumber, creditAmount);
            
            return savedTransaction;
        } catch (Exception e) {
            log.error("Error logging account withdrawal transaction for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to log account withdrawal transaction", e);
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
     * Get Stripe payment transactions
     */
    public List<CreditTransaction> getStripePaymentTransactions(String userId) {
        return getTransactionsByType(userId, CreditTransaction.TransactionType.STRIPE_PAYMENT);
    }
    
    /**
     * Get account deposit transactions
     */
    public List<CreditTransaction> getAccountDepositTransactions(String userId) {
        return getTransactionsByType(userId, CreditTransaction.TransactionType.ACCOUNT_DEPOSIT);
    }
    
    /**
     * Get credit purchase transactions
     */
    public List<CreditTransaction> getCreditPurchaseTransactions(String userId) {
        return getTransactionsByType(userId, CreditTransaction.TransactionType.CREDIT_PURCHASE);
    }
    
    /**
     * Get transaction by Stripe payment intent ID
     */
    public CreditTransaction getTransactionByStripePaymentIntentId(String stripePaymentIntentId) {
        try {
            return creditTransactionRepository.findByStripePaymentIntentId(stripePaymentIntentId)
                    .orElse(null);
        } catch (Exception e) {
            log.error("Error getting transaction by Stripe payment intent ID {}: {}", stripePaymentIntentId, e.getMessage());
            return null;
        }
    }
    
    /**
     * Get transaction statistics for user
     */
    public Map<String, Object> getUserTransactionStats(String userId) {
        try {
            List<CreditTransaction> allTransactions = creditTransactionRepository.findByUserId(userId);
            
            double totalSpent = allTransactions.stream()
                    .filter(t -> t.getPaymentAmount() != null && t.getPaymentAmount() > 0)
                    .mapToDouble(CreditTransaction::getPaymentAmount)
                    .sum();
            
            int totalCreditsPurchased = allTransactions.stream()
                    .filter(t -> t.getType() == CreditTransaction.TransactionType.CREDIT_PURCHASE || 
                                t.getType() == CreditTransaction.TransactionType.STRIPE_PAYMENT)
                    .mapToInt(CreditTransaction::getAmount)
                    .sum();
            
            int totalCreditsUsed = allTransactions.stream()
                    .filter(t -> t.getType() == CreditTransaction.TransactionType.CHAT_DEDUCTION)
                    .mapToInt(t -> Math.abs(t.getAmount()))
                    .sum();
            
            double totalDeposited = allTransactions.stream()
                    .filter(t -> t.getType() == CreditTransaction.TransactionType.ACCOUNT_DEPOSIT)
                    .mapToDouble(t -> t.getPaymentAmount() != null ? t.getPaymentAmount() : 0)
                    .sum();
            
            return Map.of(
                "totalSpent", totalSpent,
                "totalCreditsPurchased", totalCreditsPurchased,
                "totalCreditsUsed", totalCreditsUsed,
                "totalDeposited", totalDeposited,
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
     * Log refund transaction
     */
    @Transactional
    public void logRefundTransaction(String userId, String paymentIntentId, String refundId, 
                                   Double refundAmount, String reason, Double originalAmount) {
        try {
            // Get current credit balance
            int currentCredits = aiCreditService.getUserCredits(userId);
            
            // Find original transaction to calculate credits to refund
            CreditTransaction originalTransaction = creditTransactionRepository
                .findByStripePaymentIntentId(paymentIntentId)
                .stream()
                .filter(t -> t.getType() == CreditTransaction.TransactionType.CREDIT_PURCHASE)
                .findFirst()
                .orElse(null);
            
            int creditsToRefund = 0;
            if (originalTransaction != null) {
                // Calculate proportional credits to refund
                double originalPaymentAmount = originalTransaction.getPaymentAmount();
                int originalCredits = originalTransaction.getAmount();
                creditsToRefund = (int) Math.round((refundAmount / originalPaymentAmount) * originalCredits);
            }
            
            // Create refund transaction
            CreditTransaction refundTransaction = CreditTransaction.builder()
                .userId(userId)
                .type(CreditTransaction.TransactionType.REFUND)
                .amount(creditsToRefund)
                .balanceBefore(currentCredits)
                .balanceAfter(currentCredits - creditsToRefund)
                .reason("Refund: " + reason)
                .stripePaymentIntentId(paymentIntentId)
                .stripeTransactionId(refundId)
                .paymentAmount(refundAmount)
                .paymentCurrency("usd")
                .paymentMethod("refund")
                .timestamp(LocalDateTime.now())
                .build();
            
            creditTransactionRepository.save(refundTransaction);
            
            log.info("Logged refund transaction for user {}: {} credits refunded for ${}", 
                    userId, creditsToRefund, refundAmount);
                    
        } catch (Exception e) {
            log.error("Error logging refund transaction for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to log refund transaction: " + e.getMessage());
        }
    }
    
    /**
     * Get refund transactions for a user
     */
    public List<CreditTransaction> getRefundTransactions(String userId) {
        return creditTransactionRepository.findByUserIdAndTypeOrderByTimestampDesc(
            userId, CreditTransaction.TransactionType.REFUND);
    }
    
    /**
     * Get refund statistics
     */
    public Map<String, Object> getRefundStatistics(String userId) {
        List<CreditTransaction> refundTransactions = getRefundTransactions(userId);
        
        double totalRefunded = refundTransactions.stream()
            .mapToDouble(CreditTransaction::getPaymentAmount)
            .sum();
        
        int totalCreditsRefunded = refundTransactions.stream()
            .mapToInt(CreditTransaction::getAmount)
            .sum();
        
        return Map.of(
            "totalRefunds", refundTransactions.size(),
            "totalAmountRefunded", totalRefunded,
            "totalCreditsRefunded", totalCreditsRefunded,
            "averageRefundAmount", refundTransactions.isEmpty() ? 0.0 : totalRefunded / refundTransactions.size()
        );
    }
    
    /**
     * Get current user ID
     */
    public String getCurrentUserId() {
        return securityUtil.getCurrentUser().getId();
    }
} 