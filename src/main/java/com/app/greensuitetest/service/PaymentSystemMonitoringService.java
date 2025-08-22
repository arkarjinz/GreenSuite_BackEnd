package com.app.greensuitetest.service;

import com.app.greensuitetest.model.PaymentAccount;
import com.app.greensuitetest.model.PaymentTransaction;
import com.app.greensuitetest.repository.PaymentAccountRepository;
import com.app.greensuitetest.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentSystemMonitoringService {
    
    private final PaymentAccountRepository paymentAccountRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final NotificationService notificationService;
    
    /**
     * Comprehensive system health check
     */
    public Map<String, Object> performSystemHealthCheck() {
        Map<String, Object> healthCheck = new HashMap<>();
        
        try {
            // Database connectivity check
            healthCheck.put("databaseStatus", checkDatabaseHealth());
            
            // Transaction processing health
            healthCheck.put("transactionProcessing", checkTransactionProcessingHealth());
            
            // Account management health
            healthCheck.put("accountManagement", checkAccountManagementHealth());
            
            // Fraud detection system health
            healthCheck.put("fraudDetection", checkFraudDetectionHealth());
            
            // Performance metrics
            healthCheck.put("performanceMetrics", getPerformanceMetrics());
            
            // System capacity
            healthCheck.put("systemCapacity", checkSystemCapacity());
            
            // Overall status
            healthCheck.put("overallStatus", determineOverallSystemStatus(healthCheck));
            healthCheck.put("lastChecked", LocalDateTime.now());
            
        } catch (Exception e) {
            log.error("Error performing system health check: {}", e.getMessage());
            healthCheck.put("overallStatus", "CRITICAL");
            healthCheck.put("error", e.getMessage());
        }
        
        return healthCheck;
    }
    
    /**
     * Check database connectivity and performance
     */
    private Map<String, Object> checkDatabaseHealth() {
        Map<String, Object> dbHealth = new HashMap<>();
        
        try {
            long startTime = System.currentTimeMillis();
            long accountCount = paymentAccountRepository.count();
            long transactionCount = paymentTransactionRepository.count();
            long queryTime = System.currentTimeMillis() - startTime;
            
            dbHealth.put("status", "HEALTHY");
            dbHealth.put("accountCount", accountCount);
            dbHealth.put("transactionCount", transactionCount);
            dbHealth.put("queryResponseTime", queryTime + "ms");
            
            if (queryTime > 5000) {
                dbHealth.put("status", "SLOW");
                dbHealth.put("warning", "Database queries are slower than expected");
            }
            
        } catch (Exception e) {
            dbHealth.put("status", "UNHEALTHY");
            dbHealth.put("error", e.getMessage());
        }
        
        return dbHealth;
    }
    
    /**
     * Check transaction processing health
     */
    private Map<String, Object> checkTransactionProcessingHealth() {
        Map<String, Object> txHealth = new HashMap<>();
        
        try {
            LocalDateTime lastHour = LocalDateTime.now().minus(1, ChronoUnit.HOURS);
            LocalDateTime last24Hours = LocalDateTime.now().minus(24, ChronoUnit.HOURS);
            
            // Check stuck transactions
            List<PaymentTransaction> stuckTransactions = paymentTransactionRepository
                    .findStuckTransactions(lastHour);
            
            // Check retry-eligible transactions
            List<PaymentTransaction> retryEligible = paymentTransactionRepository
                    .findTransactionsEligibleForRetry(LocalDateTime.now());
            
            // Check success rate in last 24 hours
            Double successRate = paymentTransactionRepository.getSuccessRatePercentage(last24Hours);
            
            txHealth.put("status", "HEALTHY");
            txHealth.put("stuckTransactions", stuckTransactions.size());
            txHealth.put("retryEligibleTransactions", retryEligible.size());
            txHealth.put("successRate24h", successRate != null ? successRate : 0.0);
            
            // Determine health based on metrics
            if (stuckTransactions.size() > 10) {
                txHealth.put("status", "DEGRADED");
                txHealth.put("warning", "High number of stuck transactions detected");
            }
            
            if (successRate != null && successRate < 95.0) {
                txHealth.put("status", "DEGRADED");
                txHealth.put("warning", "Success rate below 95%");
            }
            
            if (stuckTransactions.size() > 50 || (successRate != null && successRate < 85.0)) {
                txHealth.put("status", "UNHEALTHY");
            }
            
        } catch (Exception e) {
            txHealth.put("status", "UNHEALTHY");
            txHealth.put("error", e.getMessage());
        }
        
        return txHealth;
    }
    
    /**
     * Check account management health
     */
    private Map<String, Object> checkAccountManagementHealth() {
        Map<String, Object> accountHealth = new HashMap<>();
        
        try {
            // Check frozen accounts
            List<PaymentAccount> frozenAccounts = paymentAccountRepository.findFrozenAccounts();
            
            // Check accounts ready for unfreeze
            List<PaymentAccount> readyForUnfreeze = paymentAccountRepository
                    .findAccountsReadyForUnfreeze(LocalDateTime.now());
            
            // Check accounts with high failure rates
            List<PaymentAccount> highFailureAccounts = paymentAccountRepository
                    .findAccountsWithHighFailureRate(10);
            
            // Check total system balance
            BigDecimal totalBalance = paymentAccountRepository.getTotalActiveAccountBalance();
            
            accountHealth.put("status", "HEALTHY");
            accountHealth.put("frozenAccounts", frozenAccounts.size());
            accountHealth.put("accountsReadyForUnfreeze", readyForUnfreeze.size());
            accountHealth.put("highFailureAccounts", highFailureAccounts.size());
            accountHealth.put("totalSystemBalance", totalBalance != null ? totalBalance : BigDecimal.ZERO);
            
            if (frozenAccounts.size() > 100) {
                accountHealth.put("status", "DEGRADED");
                accountHealth.put("warning", "High number of frozen accounts");
            }
            
        } catch (Exception e) {
            accountHealth.put("status", "UNHEALTHY");
            accountHealth.put("error", e.getMessage());
        }
        
        return accountHealth;
    }
    
    /**
     * Check fraud detection system health
     */
    private Map<String, Object> checkFraudDetectionHealth() {
        Map<String, Object> fraudHealth = new HashMap<>();
        
        try {
            LocalDateTime last24Hours = LocalDateTime.now().minus(24, ChronoUnit.HOURS);
            
            // Check failed fraud checks
            List<PaymentTransaction> failedFraudChecks = paymentTransactionRepository
                    .findFailedFraudCheckTransactions();
            
            // Check high-risk transactions
            List<PaymentTransaction> highRiskTransactions = paymentTransactionRepository
                    .findTransactionsByRiskScore(70);
            
            // Get risk level distribution
            List<Object[]> riskDistribution = paymentTransactionRepository
                    .getRiskLevelDistribution(last24Hours);
            
            fraudHealth.put("status", "HEALTHY");
            fraudHealth.put("failedFraudChecks", failedFraudChecks.size());
            fraudHealth.put("highRiskTransactions", highRiskTransactions.size());
            fraudHealth.put("riskDistribution", riskDistribution);
            
            // Calculate fraud detection rate
            long totalTransactions = paymentTransactionRepository.count();
            if (totalTransactions > 0) {
                double fraudDetectionRate = (double) failedFraudChecks.size() / totalTransactions * 100;
                fraudHealth.put("fraudDetectionRate", fraudDetectionRate);
                
                if (fraudDetectionRate > 10.0) {
                    fraudHealth.put("status", "DEGRADED");
                    fraudHealth.put("warning", "High fraud detection rate may indicate system issues");
                }
            }
            
        } catch (Exception e) {
            fraudHealth.put("status", "UNHEALTHY");
            fraudHealth.put("error", e.getMessage());
        }
        
        return fraudHealth;
    }
    
    /**
     * Get performance metrics
     */
    private Map<String, Object> getPerformanceMetrics() {
        Map<String, Object> performance = new HashMap<>();
        
        try {
            LocalDateTime last24Hours = LocalDateTime.now().minus(24, ChronoUnit.HOURS);
            
            // Average processing time
            Double avgProcessingTime = paymentTransactionRepository
                    .getAverageProcessingTimeInSeconds(last24Hours);
            
            // Transaction volume
            List<Object[]> volumeData = paymentTransactionRepository
                    .getTransactionVolumeByDate(last24Hours);
            
            performance.put("averageProcessingTime", avgProcessingTime != null ? avgProcessingTime : 0.0);
            performance.put("transactionVolume24h", volumeData.size());
            
            if (avgProcessingTime != null && avgProcessingTime > 30.0) {
                performance.put("warning", "Average processing time exceeds 30 seconds");
            }
            
        } catch (Exception e) {
            performance.put("error", e.getMessage());
        }
        
        return performance;
    }
    
    /**
     * Check system capacity and resource utilization
     */
    private Map<String, Object> checkSystemCapacity() {
        Map<String, Object> capacity = new HashMap<>();
        
        try {
            // Check memory usage
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            
            double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
            
            capacity.put("memoryUsagePercent", Math.round(memoryUsagePercent * 100.0) / 100.0);
            capacity.put("maxMemoryMB", maxMemory / (1024 * 1024));
            capacity.put("usedMemoryMB", usedMemory / (1024 * 1024));
            
            if (memoryUsagePercent > 85.0) {
                capacity.put("warning", "High memory usage detected");
            }
            
            // Check active threads
            int activeThreads = Thread.activeCount();
            capacity.put("activeThreads", activeThreads);
            
            if (activeThreads > 200) {
                capacity.put("warning", "High thread count detected");
            }
            
        } catch (Exception e) {
            capacity.put("error", e.getMessage());
        }
        
        return capacity;
    }
    
    /**
     * Determine overall system status
     */
    private String determineOverallSystemStatus(Map<String, Object> healthCheck) {
        try {
            for (Object component : healthCheck.values()) {
                if (component instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> componentMap = (Map<String, Object>) component;
                    String status = (String) componentMap.get("status");
                    
                    if ("UNHEALTHY".equals(status) || "CRITICAL".equals(status)) {
                        return "CRITICAL";
                    }
                }
            }
            
            for (Object component : healthCheck.values()) {
                if (component instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> componentMap = (Map<String, Object>) component;
                    String status = (String) componentMap.get("status");
                    
                    if ("DEGRADED".equals(status) || "SLOW".equals(status)) {
                        return "DEGRADED";
                    }
                }
            }
            
            return "HEALTHY";
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
    
    /**
     * Automated maintenance tasks - runs every hour
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    @Transactional
    public void performAutomatedMaintenance() {
        try {
            log.info("Starting automated payment system maintenance");
            
            // Unfreeze expired frozen accounts
            unfreezeExpiredAccounts();
            
            // Reset daily limits for accounts
            resetDailyLimits();
            
            // Clean up old processing attempts
            cleanupOldRetryAttempts();
            
            // Send alerts for critical issues
            checkAndSendAlerts();
            
            log.info("Automated payment system maintenance completed");
            
        } catch (Exception e) {
            log.error("Error during automated maintenance: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Unfreeze accounts that have passed their freeze expiration
     */
    private void unfreezeExpiredAccounts() {
        try {
            List<PaymentAccount> accountsToUnfreeze = paymentAccountRepository
                    .findAccountsReadyForUnfreeze(LocalDateTime.now());
            
            for (PaymentAccount account : accountsToUnfreeze) {
                account.setIsFrozen(false);
                account.setFrozenUntil(null);
                account.setFrozenReason(null);
                paymentAccountRepository.save(account);
                
                log.info("Auto-unfroze account {} after expiration", account.getAccountNumber());
            }
            
            if (!accountsToUnfreeze.isEmpty()) {
                log.info("Auto-unfroze {} accounts", accountsToUnfreeze.size());
            }
            
        } catch (Exception e) {
            log.error("Error unfreezing expired accounts: {}", e.getMessage());
        }
    }
    
    /**
     * Reset daily limits for accounts (called at midnight)
     */
    private void resetDailyLimits() {
        try {
            LocalDateTime yesterday = LocalDateTime.now().minus(1, ChronoUnit.DAYS);
            List<PaymentAccount> accountsToReset = paymentAccountRepository
                    .findAccountsNeedingDailyReset(yesterday);
            
            for (PaymentAccount account : accountsToReset) {
                account.setDailySpent(BigDecimal.ZERO);
                account.setLastDailyReset(LocalDateTime.now());
                paymentAccountRepository.save(account);
            }
            
            if (!accountsToReset.isEmpty()) {
                log.info("Reset daily limits for {} accounts", accountsToReset.size());
            }
            
        } catch (Exception e) {
            log.error("Error resetting daily limits: {}", e.getMessage());
        }
    }
    
    /**
     * Clean up old retry attempts
     */
    private void cleanupOldRetryAttempts() {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minus(7, ChronoUnit.DAYS);
            
            List<PaymentTransaction> oldFailedTransactions = paymentTransactionRepository
                    .findByStatusAndCreatedDateBetween(
                            PaymentTransaction.TransactionStatus.FAILED, 
                            LocalDateTime.now().minus(30, ChronoUnit.DAYS), 
                            cutoffTime, 
                            org.springframework.data.domain.PageRequest.of(0, 100))
                    .getContent();
            
            // Mark old failed transactions as expired to stop retry attempts
            for (PaymentTransaction transaction : oldFailedTransactions) {
                if (transaction.getRetryCount() > 0) {
                    transaction.setStatus(PaymentTransaction.TransactionStatus.EXPIRED);
                    paymentTransactionRepository.save(transaction);
                }
            }
            
            if (!oldFailedTransactions.isEmpty()) {
                log.info("Expired {} old failed transactions", oldFailedTransactions.size());
            }
            
        } catch (Exception e) {
            log.error("Error cleaning up old retry attempts: {}", e.getMessage());
        }
    }
    
    /**
     * Check for critical issues and send alerts
     */
    private void checkAndSendAlerts() {
        try {
            Map<String, Object> healthCheck = performSystemHealthCheck();
            String overallStatus = (String) healthCheck.get("overallStatus");
            
            if ("CRITICAL".equals(overallStatus)) {
                // Send critical alert
                try {
                    // TODO: Implement system alert notification once NotificationService is enhanced
                    log.error("CRITICAL: Payment System Issues Detected - Health Check: {}", healthCheck);
                } catch (Exception e) {
                    log.error("Failed to send critical alert: {}", e.getMessage());
                }
                log.warn("CRITICAL payment system status detected - alert logged");
            } else if ("DEGRADED".equals(overallStatus)) {
                // Send warning alert
                try {
                    // TODO: Implement system alert notification once NotificationService is enhanced
                    log.warn("WARNING: Payment System Performance Degraded - Health Check: {}", healthCheck);
                } catch (Exception e) {
                    log.error("Failed to send degraded alert: {}", e.getMessage());
                }
                log.warn("DEGRADED payment system status detected - alert logged");
            }
            
        } catch (Exception e) {
            log.error("Error checking and sending alerts: {}", e.getMessage());
        }
    }
} 