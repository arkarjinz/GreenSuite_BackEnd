package com.app.greensuitetest.service;

import com.app.greensuitetest.model.PaymentAccount;
import com.app.greensuitetest.model.PaymentTransaction;
import com.app.greensuitetest.repository.PaymentTransactionRepository;
import com.app.greensuitetest.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentFraudDetectionService {
    
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    
    // Fraud detection thresholds
    private static final BigDecimal LARGE_TRANSACTION_THRESHOLD = new BigDecimal("500.00");
    private static final BigDecimal VELOCITY_THRESHOLD_HOURLY = new BigDecimal("1000.00");
    private static final BigDecimal VELOCITY_THRESHOLD_DAILY = new BigDecimal("5000.00");
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int VELOCITY_CHECK_HOURS = 24;
    
    /**
     * Comprehensive fraud check for a transaction
     */
    public PaymentFraudResult performFraudCheck(PaymentTransaction transaction, PaymentAccount account, 
                                              String ipAddress, String userAgent) {
        log.debug("Performing fraud check for transaction: {}", transaction.getTransactionId());
        
        PaymentFraudResult result = new PaymentFraudResult();
        int riskScore = 0;
        Map<String, Object> analysisDetails = new HashMap<>();
        
        try {
            // 1. Amount-based checks
            riskScore += checkTransactionAmount(transaction, analysisDetails);
            
            // 2. Velocity checks
            riskScore += checkTransactionVelocity(transaction, analysisDetails);
            
            // 3. User behavior analysis
            riskScore += checkUserBehavior(transaction, account, analysisDetails);
            
            // 4. Geographic/IP analysis
            riskScore += checkGeographicRisk(ipAddress, account, analysisDetails);
            
            // 5. Time-based analysis
            riskScore += checkTimeBasedRisk(transaction, analysisDetails);
            
            // 6. Account status checks
            riskScore += checkAccountRisk(account, analysisDetails);
            
            // 7. Pattern analysis
            riskScore += checkTransactionPatterns(transaction, analysisDetails);
            
            // Determine risk level and fraud status
            result.setRiskScore(riskScore);
            result.setRiskLevel(determineRiskLevel(riskScore));
            result.setFraudCheckPassed(riskScore < 70); // Threshold for blocking
            result.setAnalysisDetails(analysisDetails);
            
            if (!result.isFraudCheckPassed()) {
                result.setFraudReason("High risk score: " + riskScore + " (threshold: 70)");
                log.warn("Transaction {} flagged as high risk. Score: {}", 
                        transaction.getTransactionId(), riskScore);
            }
            
        } catch (Exception e) {
            log.error("Error during fraud check for transaction {}: {}", 
                     transaction.getTransactionId(), e.getMessage());
            result.setFraudCheckPassed(false);
            result.setFraudReason("Fraud check system error");
            result.setRiskLevel(PaymentTransaction.RiskLevel.CRITICAL);
        }
        
        return result;
    }
    
    /**
     * Check transaction amount risks
     */
    private int checkTransactionAmount(PaymentTransaction transaction, Map<String, Object> analysis) {
        int score = 0;
        BigDecimal amount = transaction.getAmount();
        
        if (amount.compareTo(LARGE_TRANSACTION_THRESHOLD) > 0) {
            score += 20;
            analysis.put("largeAmount", true);
            analysis.put("amountThreshold", LARGE_TRANSACTION_THRESHOLD);
        }
        
        if (amount.compareTo(new BigDecimal("1000")) > 0) {
            score += 15;
            analysis.put("veryLargeAmount", true);
        }
        
        // Check for round numbers (potential fraud indicator)
        if (amount.remainder(new BigDecimal("100")).equals(BigDecimal.ZERO) && 
            amount.compareTo(new BigDecimal("500")) > 0) {
            score += 10;
            analysis.put("roundAmount", true);
        }
        
        analysis.put("amountRiskScore", score);
        return score;
    }
    
    /**
     * Check transaction velocity risks
     */
    private int checkTransactionVelocity(PaymentTransaction transaction, Map<String, Object> analysis) {
        int score = 0;
        LocalDateTime checkTime = LocalDateTime.now().minus(VELOCITY_CHECK_HOURS, ChronoUnit.HOURS);
        
        // Get recent transactions
        List<PaymentTransaction> recentTransactions = paymentTransactionRepository
                .findByUserIdAndDateRange(transaction.getUserId(), checkTime, LocalDateTime.now());
        
        BigDecimal totalAmount = recentTransactions.stream()
                .filter(t -> t.getStatus() == PaymentTransaction.TransactionStatus.COMPLETED)
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        int transactionCount = recentTransactions.size();
        
        // Velocity checks
        if (totalAmount.compareTo(VELOCITY_THRESHOLD_DAILY) > 0) {
            score += 30;
            analysis.put("highDailyVelocity", true);
        } else if (totalAmount.compareTo(VELOCITY_THRESHOLD_HOURLY) > 0) {
            score += 20;
            analysis.put("highHourlyVelocity", true);
        }
        
        if (transactionCount > 10) {
            score += 25;
            analysis.put("highTransactionFrequency", true);
        } else if (transactionCount > 5) {
            score += 15;
            analysis.put("moderateTransactionFrequency", true);
        }
        
        analysis.put("recentTransactionCount", transactionCount);
        analysis.put("recentTransactionAmount", totalAmount);
        analysis.put("velocityRiskScore", score);
        
        return score;
    }
    
    /**
     * Check user behavior patterns
     */
    private int checkUserBehavior(PaymentTransaction transaction, PaymentAccount account, 
                                  Map<String, Object> analysis) {
        int score = 0;
        
        // Check failed transaction count
        if (account.getFailedTransactionCount() >= MAX_FAILED_ATTEMPTS) {
            score += 40;
            analysis.put("highFailedTransactions", true);
        } else if (account.getFailedTransactionCount() >= 3) {
            score += 20;
            analysis.put("moderateFailedTransactions", true);
        }
        
        // Check if account is new
        if (account.getCreatedDate().isAfter(LocalDateTime.now().minus(7, ChronoUnit.DAYS))) {
            score += 15;
            analysis.put("newAccount", true);
        }
        
        // Check transaction success rate
        if (account.getTransactionCount() > 0) {
            double successRate = (double) account.getSuccessfulTransactionCount() / account.getTransactionCount();
            if (successRate < 0.5) {
                score += 25;
                analysis.put("lowSuccessRate", true);
                analysis.put("successRate", successRate);
            }
        }
        
        analysis.put("behaviorRiskScore", score);
        return score;
    }
    
    /**
     * Check geographic and IP-based risks
     */
    private int checkGeographicRisk(String ipAddress, PaymentAccount account, Map<String, Object> analysis) {
        int score = 0;
        
        // Check if IP has changed (simplified check)
        if (account.getLastTransactionIp() != null && 
            !account.getLastTransactionIp().equals(ipAddress)) {
            score += 10;
            analysis.put("ipAddressChanged", true);
        }
        
        // TODO: Implement geolocation checks, VPN detection, etc.
        // For now, just basic IP validation
        if (ipAddress == null || ipAddress.trim().isEmpty()) {
            score += 20;
            analysis.put("missingIpAddress", true);
        }
        
        analysis.put("geographicRiskScore", score);
        return score;
    }
    
    /**
     * Check time-based risk patterns
     */
    private int checkTimeBasedRisk(PaymentTransaction transaction, Map<String, Object> analysis) {
        int score = 0;
        LocalDateTime now = LocalDateTime.now();
        
        // Check for unusual hours (late night transactions)
        int hour = now.getHour();
        if (hour >= 23 || hour <= 5) {
            score += 10;
            analysis.put("offHoursTransaction", true);
        }
        
        // Check for weekend transactions
        if (now.getDayOfWeek().getValue() >= 6) {
            score += 5;
            analysis.put("weekendTransaction", true);
        }
        
        analysis.put("timeRiskScore", score);
        return score;
    }
    
    /**
     * Check account-level risks
     */
    private int checkAccountRisk(PaymentAccount account, Map<String, Object> analysis) {
        int score = 0;
        
        if (account.getIsFrozen() != null && account.getIsFrozen()) {
            score += 100; // Immediate block
            analysis.put("accountFrozen", true);
        }
        
        if (account.getStatus() != PaymentAccount.AccountStatus.ACTIVE) {
            score += 50;
            analysis.put("accountNotActive", true);
            analysis.put("accountStatus", account.getStatus());
        }
        
        if (account.getVerificationLevel() == PaymentAccount.VerificationLevel.BASIC) {
            score += 10;
            analysis.put("basicVerificationOnly", true);
        }
        
        analysis.put("accountRiskScore", score);
        return score;
    }
    
    /**
     * Check for suspicious transaction patterns
     */
    private int checkTransactionPatterns(PaymentTransaction transaction, Map<String, Object> analysis) {
        int score = 0;
        
        // Get recent transactions to check for patterns
        LocalDateTime checkTime = LocalDateTime.now().minus(1, ChronoUnit.HOURS);
        List<PaymentTransaction> recentTransactions = paymentTransactionRepository
                .findByUserIdAndDateRange(transaction.getUserId(), checkTime, LocalDateTime.now());
        
        // Check for rapid-fire transactions
        if (recentTransactions.size() >= 3) {
            score += 20;
            analysis.put("rapidFireTransactions", true);
        }
        
        // Check for repeated amounts
        long sameAmountCount = recentTransactions.stream()
                .filter(t -> t.getAmount().equals(transaction.getAmount()))
                .count();
        
        if (sameAmountCount >= 2) {
            score += 15;
            analysis.put("repeatedAmounts", true);
        }
        
        analysis.put("patternRiskScore", score);
        return score;
    }
    
    /**
     * Determine risk level based on score
     */
    private PaymentTransaction.RiskLevel determineRiskLevel(int riskScore) {
        if (riskScore >= 80) {
            return PaymentTransaction.RiskLevel.CRITICAL;
        } else if (riskScore >= 50) {
            return PaymentTransaction.RiskLevel.HIGH;
        } else if (riskScore >= 25) {
            return PaymentTransaction.RiskLevel.MEDIUM;
        } else {
            return PaymentTransaction.RiskLevel.LOW;
        }
    }
    
    /**
     * Create fraud analysis JSON for storage
     */
    public JsonNode createFraudAnalysisJson(PaymentFraudResult result) {
        try {
            Map<String, Object> fraudData = new HashMap<>();
            fraudData.put("riskScore", result.getRiskScore());
            fraudData.put("riskLevel", result.getRiskLevel().name());
            fraudData.put("fraudCheckPassed", result.isFraudCheckPassed());
            fraudData.put("fraudReason", result.getFraudReason());
            fraudData.put("analysisTimestamp", LocalDateTime.now());
            fraudData.put("analysisDetails", result.getAnalysisDetails());
            
            return objectMapper.valueToTree(fraudData);
        } catch (Exception e) {
            log.error("Error creating fraud analysis JSON: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }
    
    /**
     * Fraud detection result class
     */
    public static class PaymentFraudResult {
        private int riskScore;
        private PaymentTransaction.RiskLevel riskLevel;
        private boolean fraudCheckPassed;
        private String fraudReason;
        private Map<String, Object> analysisDetails;
        
        // Getters and setters
        public int getRiskScore() { return riskScore; }
        public void setRiskScore(int riskScore) { this.riskScore = riskScore; }
        
        public PaymentTransaction.RiskLevel getRiskLevel() { return riskLevel; }
        public void setRiskLevel(PaymentTransaction.RiskLevel riskLevel) { this.riskLevel = riskLevel; }
        
        public boolean isFraudCheckPassed() { return fraudCheckPassed; }
        public void setFraudCheckPassed(boolean fraudCheckPassed) { this.fraudCheckPassed = fraudCheckPassed; }
        
        public String getFraudReason() { return fraudReason; }
        public void setFraudReason(String fraudReason) { this.fraudReason = fraudReason; }
        
        public Map<String, Object> getAnalysisDetails() { return analysisDetails; }
        public void setAnalysisDetails(Map<String, Object> analysisDetails) { this.analysisDetails = analysisDetails; }
    }
} 