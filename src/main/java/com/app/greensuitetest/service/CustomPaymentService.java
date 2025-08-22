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
import org.springframework.transaction.annotation.Isolation;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
    private final PaymentFraudDetectionService fraudDetectionService;
    private final PaymentAnalyticsService analyticsService;
    private final HttpServletRequest httpServletRequest;
    
    // Credit package configurations
    private static final Map<String, CreditPackage> CREDIT_PACKAGES = Map.of(
        "basic", new CreditPackage(50, new BigDecimal("4.99"), "Basic Package - 50 Credits (Perfect for casual users)"),
        "standard", new CreditPackage(150, new BigDecimal("12.99"), "Standard Package - 150 Credits (Great for regular users, 15% bonus)"),
        "premium", new CreditPackage(350, new BigDecimal("24.99"), "Premium Package - 350 Credits (Best value for power users, 25% bonus)"),
        "enterprise", new CreditPackage(500, new BigDecimal("39.99"), "Enterprise Package - 500 Credits (Maximum productivity)")
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
     * Create a payment account for the current user with enhanced security
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public PaymentAccount createPaymentAccount(PaymentAccountRequest request) {
        try {
            String userId = securityUtil.getCurrentUser().getId();
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Enhanced security checks
            if (user.isBanned()) {
                throw new PaymentProcessingException("Cannot create payment account for banned user");
            }
            
            // Check if user already has a payment account
            if (paymentAccountRepository.existsByUserId(userId)) {
                throw new PaymentProcessingException("User already has a payment account");
            }
            
            // Generate unique account number with better entropy
            String accountNumber = generateSecureAccountNumber();
            
            // Determine initial verification level based on user status
            PaymentAccount.VerificationLevel verificationLevel = determineInitialVerificationLevel(user);
            
            // Set appropriate limits based on verification level
            Map<String, BigDecimal> limits = getLimitsForVerificationLevel(verificationLevel);
            
            // Get client IP for security tracking
            String clientIp = getClientIpAddress();
            
            // Create payment account with enhanced security features
            PaymentAccount account = PaymentAccount.builder()
                    .userId(userId)
                    .accountNumber(accountNumber)
                    .accountName(request.getAccountName())
                    .balance(BigDecimal.ZERO)
                    .currency(request.getCurrency())
                    .status(PaymentAccount.AccountStatus.PENDING_VERIFICATION)
                    .verificationLevel(verificationLevel)
                    .dailyLimit(limits.get("daily"))
                    .monthlyLimit(limits.get("monthly"))
                    .dailySpent(BigDecimal.ZERO)
                    .monthlySpent(BigDecimal.ZERO)
                    .lastDailyReset(LocalDateTime.now())
                    .lastMonthlyReset(LocalDateTime.now())
                    .lastLoginIp(clientIp)
                    .lastTransactionIp(clientIp)
                    .failedTransactionCount(0)
                    .isFrozen(false)
                    .totalDeposits(BigDecimal.ZERO)
                    .totalWithdrawals(BigDecimal.ZERO)
                    .transactionCount(0)
                    .successfulTransactionCount(0)
                    .build();
            
            PaymentAccount savedAccount = paymentAccountRepository.save(account);
            
            log.info("Created payment account {} for user {} with verification level {}", 
                    accountNumber, userId, verificationLevel);
            
            return savedAccount;
            
        } catch (Exception e) {
            log.error("Error creating payment account: {}", e.getMessage());
            throw new PaymentProcessingException("Failed to create payment account: " + e.getMessage());
        }
    }
    
    /**
     * Get user's payment account with security validation
     */
    public PaymentAccount getUserPaymentAccount() {
        String userId = securityUtil.getCurrentUser().getId();
        
        PaymentAccount account = paymentAccountRepository.findActiveAccountByUserId(userId)
                .orElseThrow(() -> new PaymentProcessingException("No active payment account found"));
        
        // Check if account is frozen
        if (account.getIsFrozen() != null && account.getIsFrozen()) {
            if (account.getFrozenUntil() != null && account.getFrozenUntil().isBefore(LocalDateTime.now())) {
                // Auto-unfreeze expired freezes
                account.setIsFrozen(false);
                account.setFrozenUntil(null);
                account.setFrozenReason(null);
                paymentAccountRepository.save(account);
                log.info("Auto-unfroze account {} after expiration", account.getAccountNumber());
            } else {
                throw new PaymentProcessingException("Account is frozen: " + account.getFrozenReason());
            }
        }
        
        return account;
    }
    
    /**
     * Process a deposit with enhanced security and fraud detection
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public PaymentTransaction processDeposit(DepositRequest request) {
        try {
            String userId = securityUtil.getCurrentUser().getId();
            PaymentAccount account = getUserPaymentAccount();
            
            // Enhanced validation
            validateDepositRequest(request, account);
            
            // Generate transaction ID
            String transactionId = generateSecureTransactionId();
            
            // Get security context
            String clientIp = getClientIpAddress();
            String userAgent = getUserAgent();
            
            // Calculate new balance
            BigDecimal balanceBefore = account.getBalance();
            BigDecimal balanceAfter = balanceBefore.add(request.getAmount());
            
            // Create transaction record for fraud analysis
            PaymentTransaction transaction = PaymentTransaction.builder()
                    .userId(userId)
                    .accountNumber(account.getAccountNumber())
                    .transactionId(transactionId)
                    .transactionType(PaymentTransaction.TransactionType.DEPOSIT)
                    .category(PaymentTransaction.TransactionCategory.PAYMENT)
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .status(PaymentTransaction.TransactionStatus.PENDING)
                    .description(request.getDescription() != null ? request.getDescription() : "Account deposit")
                    .referenceNumber(request.getReferenceNumber())
                    .paymentMethod(request.getPaymentMethod())
                    .balanceBefore(balanceBefore)
                    .balanceAfter(balanceAfter)
                    .ipAddress(clientIp)
                    .userAgent(userAgent)
                    .build();
            
            // Perform fraud check
            PaymentFraudDetectionService.PaymentFraudResult fraudResult = 
                    fraudDetectionService.performFraudCheck(transaction, account, clientIp, userAgent);
            
            // Apply fraud check results
            transaction.setRiskScore(fraudResult.getRiskScore());
            transaction.setRiskLevel(fraudResult.getRiskLevel());
            transaction.setFraudCheckPassed(fraudResult.isFraudCheckPassed());
            transaction.setFraudReason(fraudResult.getFraudReason());
            transaction.setFraudAnalysis(fraudDetectionService.createFraudAnalysisJson(fraudResult));
            
            // Block high-risk transactions
            if (!fraudResult.isFraudCheckPassed()) {
                transaction.setStatus(PaymentTransaction.TransactionStatus.FAILED);
                transaction.setFailureReason("Transaction blocked by fraud detection: " + fraudResult.getFraudReason());
                transaction.setFailedDate(LocalDateTime.now());
                
                PaymentTransaction savedTransaction = paymentTransactionRepository.save(transaction);
                
                // Update account failure count
                account.setFailedTransactionCount(account.getFailedTransactionCount() + 1);
                account.setLastFailedTransaction(LocalDateTime.now());
                paymentAccountRepository.save(account);
                
                log.warn("Deposit transaction {} blocked for user {} due to fraud detection", 
                        transactionId, userId);
                
                throw new PaymentProcessingException("Transaction blocked by fraud detection system");
            }
            
            // Process the transaction
            PaymentTransaction savedTransaction = paymentTransactionRepository.save(transaction);
            
            try {
                // Update account balance and statistics
            account.setBalance(balanceAfter);
            account.setTotalDeposits(account.getTotalDeposits().add(request.getAmount()));
            account.setTransactionCount(account.getTransactionCount() + 1);
                account.setSuccessfulTransactionCount(account.getSuccessfulTransactionCount() + 1);
            account.setLastTransactionDate(LocalDateTime.now());
                account.setLastTransactionIp(clientIp);
                
                // Reset failure count on successful transaction
                if (account.getFailedTransactionCount() > 0) {
                    account.setFailedTransactionCount(0);
                }
                
            paymentAccountRepository.save(account);
            
            // Update transaction status to completed
            savedTransaction.setStatus(PaymentTransaction.TransactionStatus.COMPLETED);
            savedTransaction.setProcessedDate(LocalDateTime.now());
                savedTransaction.setCompletedDate(LocalDateTime.now());
            paymentTransactionRepository.save(savedTransaction);
            
                log.info("Processed deposit for user {}. Amount: ${}, New balance: ${}, Risk Score: {}", 
                        userId, request.getAmount(), balanceAfter, fraudResult.getRiskScore());
            
            return savedTransaction;
            
            } catch (Exception e) {
                // Rollback transaction on error
                savedTransaction.setStatus(PaymentTransaction.TransactionStatus.FAILED);
                savedTransaction.setFailureReason("Processing error: " + e.getMessage());
                savedTransaction.setFailedDate(LocalDateTime.now());
                paymentTransactionRepository.save(savedTransaction);
                
                log.error("Failed to process deposit for user {}: {}", userId, e.getMessage());
                throw new PaymentProcessingException("Deposit processing failed: " + e.getMessage());
            }
            
        } catch (PaymentProcessingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error processing deposit: {}", e.getMessage());
            throw new PaymentProcessingException("Failed to process deposit: " + e.getMessage());
        }
    }
    
    /**
     * Purchase credits with enhanced security and fraud detection
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public PaymentTransaction purchaseCredits(CreditPurchaseRequest request) {
        try {
            String userId = securityUtil.getCurrentUser().getId();
            PaymentAccount account = getUserPaymentAccount();
            
            // Get credit package details
            CreditPackage selectedPackage = CREDIT_PACKAGES.get(request.getCreditPackage().toLowerCase());
            if (selectedPackage == null) {
                throw new PaymentProcessingException("Invalid credit package: " + request.getCreditPackage());
            }
            
            // Enhanced validation
            validateCreditPurchase(request, account, selectedPackage);
            
            // Get current credit balance
            int creditBalanceBefore = aiCreditService.getUserCredits(userId);
            
            // Generate transaction ID
            String transactionId = generateSecureTransactionId();
            
            // Get security context
            String clientIp = getClientIpAddress();
            String userAgent = getUserAgent();
            
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
                    .category(PaymentTransaction.TransactionCategory.CREDIT_PURCHASE)
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
                    .ipAddress(clientIp)
                    .userAgent(userAgent)
                    .build();
            
            // Perform fraud check
            PaymentFraudDetectionService.PaymentFraudResult fraudResult = 
                    fraudDetectionService.performFraudCheck(transaction, account, clientIp, userAgent);
            
            // Apply fraud check results
            transaction.setRiskScore(fraudResult.getRiskScore());
            transaction.setRiskLevel(fraudResult.getRiskLevel());
            transaction.setFraudCheckPassed(fraudResult.isFraudCheckPassed());
            transaction.setFraudReason(fraudResult.getFraudReason());
            transaction.setFraudAnalysis(fraudDetectionService.createFraudAnalysisJson(fraudResult));
            
            // Save transaction first
            PaymentTransaction savedTransaction = paymentTransactionRepository.save(transaction);
            
            // Block high-risk transactions
            if (!fraudResult.isFraudCheckPassed()) {
                savedTransaction.setStatus(PaymentTransaction.TransactionStatus.FAILED);
                savedTransaction.setFailureReason("Transaction blocked by fraud detection: " + fraudResult.getFraudReason());
                savedTransaction.setFailedDate(LocalDateTime.now());
                paymentTransactionRepository.save(savedTransaction);
                
                // Update account failure count
                account.setFailedTransactionCount(account.getFailedTransactionCount() + 1);
                account.setLastFailedTransaction(LocalDateTime.now());
                paymentAccountRepository.save(account);
                
                log.warn("Credit purchase {} blocked for user {} due to fraud detection", 
                        transactionId, userId);
                
                throw new PaymentProcessingException("Transaction blocked by fraud detection system");
            }
            
            try {
                // Check spending limits
                updateSpendingLimits(account, selectedPackage.price);
            
            // Update account balance
            account.setBalance(balanceAfter);
            account.setTotalWithdrawals(account.getTotalWithdrawals().add(selectedPackage.price));
            account.setTransactionCount(account.getTransactionCount() + 1);
                account.setSuccessfulTransactionCount(account.getSuccessfulTransactionCount() + 1);
            account.setLastTransactionDate(LocalDateTime.now());
                account.setLastTransactionIp(clientIp);
                
                // Reset failure count on successful transaction
                if (account.getFailedTransactionCount() > 0) {
                    account.setFailedTransactionCount(0);
                }
                
            paymentAccountRepository.save(account);
            
            // Add credits to user account
            aiCreditService.addCredits(userId, selectedPackage.credits, 
                    "Credit purchase from account balance - " + request.getCreditPackage() + " package");
            
            // Update transaction status to completed
            savedTransaction.setStatus(PaymentTransaction.TransactionStatus.COMPLETED);
            savedTransaction.setProcessedDate(LocalDateTime.now());
                savedTransaction.setCompletedDate(LocalDateTime.now());
            paymentTransactionRepository.save(savedTransaction);
            
                log.info("Credit purchase completed for user {}. Purchased {} credits for ${}. Account balance: {} -> {}, Risk Score: {}", 
                        userId, selectedPackage.credits, selectedPackage.price, balanceBefore, balanceAfter, fraudResult.getRiskScore());
            
            return savedTransaction;
            
            } catch (Exception e) {
                // Rollback transaction on failure
                savedTransaction.setStatus(PaymentTransaction.TransactionStatus.FAILED);
                savedTransaction.setFailureReason("Processing error: " + e.getMessage());
                savedTransaction.setFailedDate(LocalDateTime.now());
                paymentTransactionRepository.save(savedTransaction);
                
                log.error("Credit purchase failed for user {}, transaction rolled back: {}", userId, e.getMessage());
                throw new PaymentProcessingException("Credit purchase failed: " + e.getMessage());
            }
            
        } catch (PaymentProcessingException e) {
            throw e;
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
     * Determine the initial verification level for a new user.
     */
    private PaymentAccount.VerificationLevel determineInitialVerificationLevel(User user) {
        // For now, all new accounts start with BASIC verification
        // This can be enhanced later with actual verification logic
        return PaymentAccount.VerificationLevel.BASIC;
    }
    
    /**
     * Get limits for a specific verification level.
     */
    private Map<String, BigDecimal> getLimitsForVerificationLevel(PaymentAccount.VerificationLevel level) {
        switch (level) {
            case ENTERPRISE:
                return Map.of("daily", new BigDecimal("50000.00"), "monthly", new BigDecimal("500000.00"));
            case PREMIUM:
                return Map.of("daily", new BigDecimal("10000.00"), "monthly", new BigDecimal("100000.00"));
            case STANDARD:
                return Map.of("daily", new BigDecimal("5000.00"), "monthly", new BigDecimal("50000.00"));
            case BASIC:
            default:
                return Map.of("daily", new BigDecimal("1000.00"), "monthly", new BigDecimal("10000.00"));
        }
    }
    
    /**
     * Get the maximum single deposit amount allowed for a verification level.
     */
    private BigDecimal getMaxSingleDepositForVerification(PaymentAccount.VerificationLevel level) {
        switch (level) {
            case ENTERPRISE:
                return new BigDecimal("25000.00");
            case PREMIUM:
                return new BigDecimal("10000.00");
            case STANDARD:
                return new BigDecimal("5000.00");
            case BASIC:
            default:
                return new BigDecimal("1000.00");
        }
    }
    
    /**
     * Generate secure account number with better entropy
     */
    private String generateSecureAccountNumber() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        sb.append("GS"); // GreenSuite prefix
        
        // Add timestamp component for uniqueness
        sb.append(System.currentTimeMillis() % 100000);
        
        // Add random component
        for (int i = 0; i < 6; i++) {
            sb.append(random.nextInt(10));
        }
        
        // Verify uniqueness
        String accountNumber = sb.toString();
        while (paymentAccountRepository.existsByAccountNumber(accountNumber)) {
            // Regenerate if collision detected
            sb = new StringBuilder("GS");
            sb.append(System.currentTimeMillis() % 100000);
            for (int i = 0; i < 6; i++) {
                sb.append(random.nextInt(10));
            }
            accountNumber = sb.toString();
        }
        
        return accountNumber;
    }
    
    /**
     * Generate secure transaction ID with better entropy
     */
    private String generateSecureTransactionId() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        sb.append("TXN");
        
        // Add timestamp component
        sb.append(System.currentTimeMillis() % 1000000);
        
        // Add random component
        for (int i = 0; i < 10; i++) {
            sb.append(random.nextInt(10));
        }
        
        // Verify uniqueness
        String transactionId = sb.toString();
        while (paymentTransactionRepository.findByTransactionId(transactionId).isPresent()) {
            // Regenerate if collision detected
            sb = new StringBuilder("TXN");
            sb.append(System.currentTimeMillis() % 1000000);
            for (int i = 0; i < 10; i++) {
                sb.append(random.nextInt(10));
            }
            transactionId = sb.toString();
        }
        
        return transactionId;
    }
    
    /**
     * Enhanced validation for deposit requests
     */
    private void validateDepositRequest(DepositRequest request, PaymentAccount account) {
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentProcessingException("Deposit amount must be positive");
        }
        
        // Check maximum deposit limits
        BigDecimal maxSingleDeposit = getMaxSingleDepositForVerification(account.getVerificationLevel());
        if (request.getAmount().compareTo(maxSingleDeposit) > 0) {
            throw new PaymentProcessingException(
                String.format("Deposit amount exceeds maximum allowed: $%s (limit: $%s)", 
                             request.getAmount(), maxSingleDeposit));
        }
        
        // Check daily deposit limits
        BigDecimal remainingDailyLimit = account.getRemainingDailyLimit();
        if (request.getAmount().compareTo(remainingDailyLimit) > 0) {
            throw new PaymentProcessingException(
                String.format("Deposit would exceed daily limit. Remaining: $%s", remainingDailyLimit));
        }
    }
    
    /**
     * Enhanced validation for credit purchases
     */
    private void validateCreditPurchase(CreditPurchaseRequest request, PaymentAccount account, CreditPackage selectedPackage) {
        // Check if user has sufficient balance
        if (account.getBalance().compareTo(selectedPackage.price) < 0) {
            throw new PaymentProcessingException(
                String.format("Insufficient account balance. Required: $%s, Available: $%s", 
                             selectedPackage.price, account.getBalance()));
        }
        
        // Check if transaction is within account limits
        if (!account.canProcessTransaction(selectedPackage.price)) {
            throw new PaymentProcessingException("Transaction exceeds account limits or account is not active");
        }
        
        // Check spending limits
        if (account.getDailySpent().add(selectedPackage.price).compareTo(account.getDailyLimit()) > 0) {
            throw new PaymentProcessingException("Transaction would exceed daily spending limit");
        }
        
        if (account.getMonthlySpent().add(selectedPackage.price).compareTo(account.getMonthlyLimit()) > 0) {
            throw new PaymentProcessingException("Transaction would exceed monthly spending limit");
        }
    }

    /**
     * Update spending limits for the account.
     */
    private void updateSpendingLimits(PaymentAccount account, BigDecimal transactionAmount) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastDailyReset = account.getLastDailyReset();
        LocalDateTime lastMonthlyReset = account.getLastMonthlyReset();

        // Reset daily limit if it's a new day
        if (lastDailyReset == null || lastDailyReset.isBefore(now.minusDays(1))) {
            account.setDailySpent(BigDecimal.ZERO);
            account.setLastDailyReset(now);
            log.info("Daily limit reset for account {}", account.getAccountNumber());
        }

        // Reset monthly limit if it's a new month
        if (lastMonthlyReset == null || lastMonthlyReset.isBefore(now.minusMonths(1))) {
            account.setMonthlySpent(BigDecimal.ZERO);
            account.setLastMonthlyReset(now);
            log.info("Monthly limit reset for account {}", account.getAccountNumber());
        }

        // Add to daily spent
        account.setDailySpent(account.getDailySpent().add(transactionAmount));
        // Add to monthly spent
        account.setMonthlySpent(account.getMonthlySpent().add(transactionAmount));
    }



    /**
     * Get the client IP address from the request.
     */
    private String getClientIpAddress() {
        String ip = httpServletRequest.getHeader("X-Forwarded-For");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = httpServletRequest.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = httpServletRequest.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = httpServletRequest.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = httpServletRequest.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = httpServletRequest.getRemoteAddr();
        }
        return ip;
    }

    /**
     * Get the user agent from the request.
     */
    private String getUserAgent() {
        return httpServletRequest.getHeader("User-Agent");
    }
} 