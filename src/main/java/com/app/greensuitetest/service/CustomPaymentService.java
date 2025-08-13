package com.app.greensuitetest.service;

import com.app.greensuitetest.dto.CreditPurchaseRequest;
import com.app.greensuitetest.dto.DepositRequest;
import com.app.greensuitetest.dto.PaymentAccountRequest;
import com.app.greensuitetest.exception.PaymentProcessingException;
import com.app.greensuitetest.model.PaymentAccount;
import com.app.greensuitetest.model.PaymentTransaction;
import com.app.greensuitetest.model.User;
import com.app.greensuitetest.repository.PaymentAccountRepository;
import com.app.greensuitetest.repository.PaymentTransactionRepository;
import com.app.greensuitetest.repository.UserRepository;
import com.app.greensuitetest.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomPaymentService {
    
    private final PaymentAccountRepository paymentAccountRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final UserRepository userRepository;
    private final AICreditService aiCreditService;
    private final SecurityUtil securityUtil;
    
    // Credit package configurations
    private static final Map<String, CreditPackage> CREDIT_PACKAGES = Map.of(
        "basic", new CreditPackage(10, new BigDecimal("4.99"), "Basic Package - 10 Credits"),
        "standard", new CreditPackage(25, new BigDecimal("9.99"), "Standard Package - 25 Credits"),
        "premium", new CreditPackage(50, new BigDecimal("17.99"), "Premium Package - 50 Credits"),
        "enterprise", new CreditPackage(100, new BigDecimal("29.99"), "Enterprise Package - 100 Credits")
    );
    
    // Credit package data class
    private static class CreditPackage {
        final int credits;
        final BigDecimal price;
        final String description;
        
        CreditPackage(int credits, BigDecimal price, String description) {
            this.credits = credits;
            this.price = price;
            this.description = description;
        }
    }
    
    /**
     * Create a payment account for the current user
     */
    @Transactional
    public PaymentAccount createPaymentAccount(PaymentAccountRequest request) {
        try {
            String userId = securityUtil.getCurrentUser().getId();
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Check if user already has a payment account
            if (paymentAccountRepository.existsByUserId(userId)) {
                throw new PaymentProcessingException("User already has a payment account");
            }
            
            // Generate unique account number
            String accountNumber = generateAccountNumber();
            
            // Create payment account
            PaymentAccount account = PaymentAccount.builder()
                    .userId(userId)
                    .accountNumber(accountNumber)
                    .accountName(request.getAccountName())
                    .balance(BigDecimal.ZERO)
                    .currency(request.getCurrency())
                    .status(PaymentAccount.AccountStatus.ACTIVE)
                    .totalDeposits(BigDecimal.ZERO)
                    .totalWithdrawals(BigDecimal.ZERO)
                    .transactionCount(0)
                    .build();
            
            PaymentAccount savedAccount = paymentAccountRepository.save(account);
            
            log.info("Created payment account {} for user {}", accountNumber, userId);
            return savedAccount;
            
        } catch (Exception e) {
            log.error("Error creating payment account: {}", e.getMessage());
            throw new PaymentProcessingException("Failed to create payment account: " + e.getMessage());
        }
    }
    
    /**
     * Get user's payment account
     */
    public PaymentAccount getUserPaymentAccount() {
        String userId = securityUtil.getCurrentUser().getId();
        return paymentAccountRepository.findActiveAccountByUserId(userId)
                .orElseThrow(() -> new PaymentProcessingException("No active payment account found"));
    }
    
    /**
     * Process a deposit to user's payment account
     */
    @Transactional
    public PaymentTransaction processDeposit(DepositRequest request) {
        try {
            String userId = securityUtil.getCurrentUser().getId();
            PaymentAccount account = getUserPaymentAccount();
            
            // Validate amount
            if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new PaymentProcessingException("Deposit amount must be positive");
            }
            
            // Generate transaction ID
            String transactionId = generateTransactionId();
            
            // Get current balance
            BigDecimal balanceBefore = account.getBalance();
            BigDecimal balanceAfter = balanceBefore.add(request.getAmount());
            
            // Create transaction record
            PaymentTransaction transaction = PaymentTransaction.builder()
                    .userId(userId)
                    .accountNumber(account.getAccountNumber())
                    .transactionId(transactionId)
                    .transactionType(PaymentTransaction.TransactionType.DEPOSIT)
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .status(PaymentTransaction.TransactionStatus.PENDING)
                    .description(request.getDescription() != null ? request.getDescription() : "Account deposit")
                    .referenceNumber(request.getReferenceNumber())
                    .paymentMethod(request.getPaymentMethod())
                    .balanceBefore(balanceBefore)
                    .balanceAfter(balanceAfter)
                    .build();
            
            // Save transaction
            PaymentTransaction savedTransaction = paymentTransactionRepository.save(transaction);
            
            // Update account balance
            account.setBalance(balanceAfter);
            account.setTotalDeposits(account.getTotalDeposits().add(request.getAmount()));
            account.setTransactionCount(account.getTransactionCount() + 1);
            account.setLastTransactionDate(LocalDateTime.now());
            paymentAccountRepository.save(account);
            
            // Update transaction status to completed
            savedTransaction.setStatus(PaymentTransaction.TransactionStatus.COMPLETED);
            savedTransaction.setProcessedDate(LocalDateTime.now());
            paymentTransactionRepository.save(savedTransaction);
            
            log.info("Processed deposit for user {}. Amount: ${}, New balance: ${}", 
                    userId, request.getAmount(), balanceAfter);
            
            return savedTransaction;
            
        } catch (Exception e) {
            log.error("Error processing deposit: {}", e.getMessage());
            throw new PaymentProcessingException("Failed to process deposit: " + e.getMessage());
        }
    }
    
    /**
     * Purchase credits using account balance
     */
    @Transactional
    public PaymentTransaction purchaseCredits(CreditPurchaseRequest request) {
        try {
            String userId = securityUtil.getCurrentUser().getId();
            PaymentAccount account = getUserPaymentAccount();
            
            // Get credit package details
            CreditPackage selectedPackage = CREDIT_PACKAGES.get(request.getCreditPackage().toLowerCase());
            if (selectedPackage == null) {
                throw new PaymentProcessingException("Invalid credit package: " + request.getCreditPackage());
            }
            
            // Check if user has sufficient balance
            if (account.getBalance().compareTo(selectedPackage.price) < 0) {
                throw new PaymentProcessingException("Insufficient account balance. Required: $" + selectedPackage.price + ", Available: $" + account.getBalance());
            }
            
            // Get current credit balance
            int creditBalanceBefore = aiCreditService.getUserCredits(userId);
            
            // Generate transaction ID
            String transactionId = generateTransactionId();
            
            // Calculate new balances
            BigDecimal balanceBefore = account.getBalance();
            BigDecimal balanceAfter = balanceBefore.subtract(selectedPackage.price);
            int creditBalanceAfter = creditBalanceBefore + selectedPackage.credits;
            
            // Create transaction record
            PaymentTransaction transaction = PaymentTransaction.builder()
                    .userId(userId)
                    .accountNumber(account.getAccountNumber())
                    .transactionId(transactionId)
                    .transactionType(PaymentTransaction.TransactionType.CREDIT_PURCHASE)
                    .amount(selectedPackage.price)
                    .currency(request.getCurrency())
                    .status(PaymentTransaction.TransactionStatus.PENDING)
                    .description(selectedPackage.description)
                    .paymentMethod("ACCOUNT_BALANCE")
                    .balanceBefore(balanceBefore)
                    .balanceAfter(balanceAfter)
                    .creditsPurchased(selectedPackage.credits)
                    .creditBalanceBefore(creditBalanceBefore)
                    .creditBalanceAfter(creditBalanceAfter)
                    .creditPackage(request.getCreditPackage())
                    .build();
            
            // Save transaction
            PaymentTransaction savedTransaction = paymentTransactionRepository.save(transaction);
            
            // Update account balance
            account.setBalance(balanceAfter);
            account.setTotalWithdrawals(account.getTotalWithdrawals().add(selectedPackage.price));
            account.setTransactionCount(account.getTransactionCount() + 1);
            account.setLastTransactionDate(LocalDateTime.now());
            paymentAccountRepository.save(account);
            
            // Add credits to user account
            aiCreditService.addCredits(userId, selectedPackage.credits, 
                    "Credit purchase from account balance - " + request.getCreditPackage() + " package");
            
            // Update transaction status to completed
            savedTransaction.setStatus(PaymentTransaction.TransactionStatus.COMPLETED);
            savedTransaction.setProcessedDate(LocalDateTime.now());
            paymentTransactionRepository.save(savedTransaction);
            
            log.info("Credit purchase completed for user {}. Purchased {} credits for ${}. Account balance: {} -> {}", 
                    userId, selectedPackage.credits, selectedPackage.price, balanceBefore, balanceAfter);
            
            return savedTransaction;
            
        } catch (Exception e) {
            log.error("Error purchasing credits: {}", e.getMessage());
            throw new PaymentProcessingException("Failed to purchase credits: " + e.getMessage());
        }
    }
    
    /**
     * Get available credit packages
     */
    public List<Map<String, Object>> getCreditPackages() {
        return CREDIT_PACKAGES.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> packageInfo = new HashMap<>();
                    packageInfo.put("id", entry.getKey());
                    packageInfo.put("credits", entry.getValue().credits);
                    packageInfo.put("price", entry.getValue().price);
                    packageInfo.put("description", entry.getValue().description);
                    packageInfo.put("pricePerCredit", entry.getValue().price.divide(BigDecimal.valueOf(entry.getValue().credits), 2, BigDecimal.ROUND_HALF_UP));
                    return packageInfo;
                })
                .toList();
    }
    
    /**
     * Get transaction history for user
     */
    public List<PaymentTransaction> getTransactionHistory() {
        String userId = securityUtil.getCurrentUser().getId();
        return paymentTransactionRepository.findByUserIdOrderByCreatedDateDesc(userId);
    }
    
    /**
     * Get transaction by ID
     */
    public PaymentTransaction getTransaction(String transactionId) {
        return paymentTransactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new PaymentProcessingException("Transaction not found"));
    }
    
    /**
     * Get account statistics
     */
    public Map<String, Object> getAccountStatistics() {
        String userId = securityUtil.getCurrentUser().getId();
        PaymentAccount account = getUserPaymentAccount();
        
        BigDecimal totalDeposits = paymentTransactionRepository.getTotalDepositsByUserId(userId);
        BigDecimal totalWithdrawals = paymentTransactionRepository.getTotalWithdrawalsByUserId(userId);
        long transactionCount = paymentTransactionRepository.getCompletedTransactionCountByUserId(userId);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("accountNumber", account.getAccountNumber());
        stats.put("accountName", account.getAccountName());
        stats.put("currentBalance", account.getBalance());
        stats.put("currency", account.getCurrency());
        stats.put("totalDeposits", totalDeposits != null ? totalDeposits : BigDecimal.ZERO);
        stats.put("totalWithdrawals", totalWithdrawals != null ? totalWithdrawals : BigDecimal.ZERO);
        stats.put("transactionCount", transactionCount);
        stats.put("accountStatus", account.getStatus());
        stats.put("createdDate", account.getCreatedDate());
        stats.put("lastTransactionDate", account.getLastTransactionDate());
        
        return stats;
    }
    
    /**
     * Generate unique account number
     */
    private String generateAccountNumber() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        sb.append("GS"); // GreenSuite prefix
        
        for (int i = 0; i < 8; i++) {
            sb.append(random.nextInt(10));
        }
        
        return sb.toString();
    }
    
    /**
     * Generate unique transaction ID
     */
    private String generateTransactionId() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        sb.append("TXN");
        
        for (int i = 0; i < 12; i++) {
            sb.append(random.nextInt(10));
        }
        
        return sb.toString();
    }
} 