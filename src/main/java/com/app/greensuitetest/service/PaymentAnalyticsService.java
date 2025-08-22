package com.app.greensuitetest.service;

import com.app.greensuitetest.model.PaymentAccount;
import com.app.greensuitetest.model.PaymentTransaction;
import com.app.greensuitetest.repository.PaymentAccountRepository;
import com.app.greensuitetest.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentAnalyticsService {
    
    private final PaymentAccountRepository paymentAccountRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    
    /**
     * Get comprehensive payment system dashboard data
     */
    public Map<String, Object> getPaymentDashboard() {
        try {
            Map<String, Object> dashboard = new HashMap<>();
            
            // Basic statistics
            dashboard.put("totalAccounts", paymentAccountRepository.count());
            dashboard.put("activeAccounts", paymentAccountRepository.findByStatus(PaymentAccount.AccountStatus.ACTIVE).size());
            dashboard.put("totalTransactions", paymentTransactionRepository.count());
            
            // Financial metrics
            BigDecimal totalBalance = paymentAccountRepository.getTotalActiveAccountBalance();
            BigDecimal averageBalance = paymentAccountRepository.getAverageAccountBalance();
            
            dashboard.put("totalSystemBalance", totalBalance != null ? totalBalance : BigDecimal.ZERO);
            dashboard.put("averageAccountBalance", averageBalance != null ? averageBalance : BigDecimal.ZERO);
            
            // Recent activity (last 24 hours)
            LocalDateTime last24Hours = LocalDateTime.now().minus(24, ChronoUnit.HOURS);
            dashboard.put("recentActivity", getRecentActivityStats(last24Hours));
            
            // Transaction statistics
            dashboard.put("transactionStats", getTransactionStatistics(last24Hours));
            
            // Risk and fraud metrics
            dashboard.put("riskMetrics", getRiskMetrics());
            
            // Account distribution
            dashboard.put("accountDistribution", getAccountDistribution());
            
            // Performance metrics
            dashboard.put("performanceMetrics", getPerformanceMetrics(last24Hours));
            
            return dashboard;
        } catch (Exception e) {
            log.error("Error generating payment dashboard: {}", e.getMessage());
            return Map.of("error", "Failed to generate dashboard data");
        }
    }
    
    /**
     * Get recent activity statistics
     */
    private Map<String, Object> getRecentActivityStats(LocalDateTime since) {
        Map<String, Object> stats = new HashMap<>();
        
        // Transaction counts by status
        List<Object[]> statusCounts = paymentTransactionRepository.getTransactionCountByStatus(since);
        Map<String, Integer> statusMap = new HashMap<>();
        for (Object[] row : statusCounts) {
            statusMap.put(row[0].toString(), ((Long) row[1]).intValue());
        }
        stats.put("transactionsByStatus", statusMap);
        
        // Volume by transaction type
        List<Object[]> typeStats = paymentTransactionRepository.getTransactionStatsByType(since);
        Map<String, Map<String, Object>> typeMap = new HashMap<>();
        for (Object[] row : typeStats) {
            Map<String, Object> typeData = new HashMap<>();
            typeData.put("count", ((Long) row[1]).intValue());
            typeData.put("volume", row[2]);
            typeMap.put(row[0].toString(), typeData);
        }
        stats.put("transactionsByType", typeMap);
        
        // Daily transaction volume
        List<Object[]> dailyVolume = paymentTransactionRepository.getTransactionVolumeByDate(since);
        stats.put("dailyVolume", dailyVolume);
        
        return stats;
    }
    
    /**
     * Get comprehensive transaction statistics
     */
    private Map<String, Object> getTransactionStatistics(LocalDateTime since) {
        Map<String, Object> stats = new HashMap<>();
        
        // Success rate
        Double successRate = paymentTransactionRepository.getSuccessRatePercentage(since);
        stats.put("successRate", successRate != null ? 
                  BigDecimal.valueOf(successRate).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        
        // Average processing time
        Double avgProcessingTime = paymentTransactionRepository.getAverageProcessingTimeInSeconds(since);
        stats.put("averageProcessingTimeSeconds", avgProcessingTime != null ? 
                  BigDecimal.valueOf(avgProcessingTime).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        
        // Large transactions
        BigDecimal largeTransactionThreshold = new BigDecimal("1000");
        List<PaymentTransaction> largeTransactions = paymentTransactionRepository
                .findLargeTransactions(largeTransactionThreshold);
        stats.put("largeTransactionsCount", largeTransactions.size());
        
        // Payment method distribution
        List<Object[]> paymentMethods = paymentTransactionRepository.getStatsByPaymentMethod(since);
        Map<String, Map<String, Object>> methodMap = new HashMap<>();
        for (Object[] row : paymentMethods) {
            Map<String, Object> methodData = new HashMap<>();
            methodData.put("count", ((Long) row[1]).intValue());
            methodData.put("volume", row[2]);
            methodMap.put(row[0] != null ? row[0].toString() : "Unknown", methodData);
        }
        stats.put("paymentMethods", methodMap);
        
        // Currency distribution
        List<Object[]> currencies = paymentTransactionRepository.getStatsByCurrency(since);
        Map<String, Map<String, Object>> currencyMap = new HashMap<>();
        for (Object[] row : currencies) {
            Map<String, Object> currencyData = new HashMap<>();
            currencyData.put("count", ((Long) row[1]).intValue());
            currencyData.put("volume", row[2]);
            currencyMap.put(row[0].toString(), currencyData);
        }
        stats.put("currencies", currencyMap);
        
        return stats;
    }
    
    /**
     * Get risk and fraud metrics
     */
    private Map<String, Object> getRiskMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        LocalDateTime last24Hours = LocalDateTime.now().minus(24, ChronoUnit.HOURS);
        
        // Risk level distribution
        List<Object[]> riskDistribution = paymentTransactionRepository.getRiskLevelDistribution(last24Hours);
        Map<String, Integer> riskMap = new HashMap<>();
        for (Object[] row : riskDistribution) {
            riskMap.put(row[0] != null ? row[0].toString() : "Unknown", ((Long) row[1]).intValue());
        }
        metrics.put("riskLevelDistribution", riskMap);
        
        // Failed fraud checks
        List<PaymentTransaction> failedFraudChecks = paymentTransactionRepository.findFailedFraudCheckTransactions();
        metrics.put("failedFraudChecks", failedFraudChecks.size());
        
        // High risk transactions
        List<PaymentTransaction> highRiskTransactions = paymentTransactionRepository
                .findTransactionsByRiskScore(50);
        metrics.put("highRiskTransactions", highRiskTransactions.size());
        
        // Accounts with high failure rates
        List<PaymentAccount> highFailureAccounts = paymentAccountRepository
                .findAccountsWithHighFailureRate(5);
        metrics.put("accountsWithHighFailureRate", highFailureAccounts.size());
        
        // Frozen accounts
        List<PaymentAccount> frozenAccounts = paymentAccountRepository.findFrozenAccounts();
        metrics.put("frozenAccounts", frozenAccounts.size());
        
        return metrics;
    }
    
    /**
     * Get account distribution statistics
     */
    private Map<String, Object> getAccountDistribution() {
        Map<String, Object> distribution = new HashMap<>();
        
        // By status
        List<Object[]> statusCounts = paymentAccountRepository.getAccountCountByStatus();
        Map<String, Integer> statusMap = new HashMap<>();
        for (Object[] row : statusCounts) {
            statusMap.put(row[0].toString(), ((Long) row[1]).intValue());
        }
        distribution.put("byStatus", statusMap);
        
        // By verification level
        List<Object[]> verificationCounts = paymentAccountRepository.getAccountCountByVerificationLevel();
        Map<String, Integer> verificationMap = new HashMap<>();
        for (Object[] row : verificationCounts) {
            verificationMap.put(row[0].toString(), ((Long) row[1]).intValue());
        }
        distribution.put("byVerificationLevel", verificationMap);
        
        // By currency
        List<Object[]> currencyStats = paymentAccountRepository.getAccountStatsByCurrency();
        Map<String, Map<String, Object>> currencyMap = new HashMap<>();
        for (Object[] row : currencyStats) {
            Map<String, Object> currencyData = new HashMap<>();
            currencyData.put("accountCount", ((Long) row[1]).intValue());
            currencyData.put("totalBalance", row[2]);
            currencyMap.put(row[0].toString(), currencyData);
        }
        distribution.put("byCurrency", currencyMap);
        
        return distribution;
    }
    
    /**
     * Get performance metrics
     */
    private Map<String, Object> getPerformanceMetrics(LocalDateTime since) {
        Map<String, Object> metrics = new HashMap<>();
        
        // Transactions eligible for retry
        List<PaymentTransaction> retryEligible = paymentTransactionRepository
                .findTransactionsEligibleForRetry(LocalDateTime.now());
        metrics.put("transactionsEligibleForRetry", retryEligible.size());
        
        // Stuck transactions
        LocalDateTime stuckThreshold = LocalDateTime.now().minus(1, ChronoUnit.HOURS);
        List<PaymentTransaction> stuckTransactions = paymentTransactionRepository
                .findStuckTransactions(stuckThreshold);
        metrics.put("stuckTransactions", stuckTransactions.size());
        
        // Transactions awaiting webhook
        List<PaymentTransaction> awaitingWebhook = paymentTransactionRepository
                .findTransactionsAwaitingWebhook();
        metrics.put("transactionsAwaitingWebhook", awaitingWebhook.size());
        
        // Accounts near limits
        List<PaymentAccount> nearDailyLimit = paymentAccountRepository.findAccountsNearDailyLimit();
        List<PaymentAccount> nearMonthlyLimit = paymentAccountRepository.findAccountsNearMonthlyLimit();
        
        metrics.put("accountsNearDailyLimit", nearDailyLimit.size());
        metrics.put("accountsNearMonthlyLimit", nearMonthlyLimit.size());
        
        return metrics;
    }
    
    /**
     * Get detailed user transaction analytics
     */
    public Map<String, Object> getUserTransactionAnalytics(String userId, int days) {
        Map<String, Object> analytics = new HashMap<>();
        
        try {
            LocalDateTime startDate = LocalDateTime.now().minus(days, ChronoUnit.DAYS);
            
            // Get user transactions in date range
            List<PaymentTransaction> transactions = paymentTransactionRepository
                    .findByUserIdAndDateRange(userId, startDate, LocalDateTime.now());
            
            // Basic statistics
            analytics.put("totalTransactions", transactions.size());
            analytics.put("periodDays", days);
            
            // Transaction status breakdown
            Map<String, Long> statusBreakdown = transactions.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            t -> t.getStatus().name(),
                            java.util.stream.Collectors.counting()));
            analytics.put("statusBreakdown", statusBreakdown);
            
            // Amount statistics
            BigDecimal totalAmount = transactions.stream()
                    .filter(t -> t.getStatus() == PaymentTransaction.TransactionStatus.COMPLETED)
                    .map(PaymentTransaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal averageAmount = transactions.isEmpty() ? BigDecimal.ZERO :
                    totalAmount.divide(BigDecimal.valueOf(transactions.size()), 2, RoundingMode.HALF_UP);
            
            analytics.put("totalAmount", totalAmount);
            analytics.put("averageAmount", averageAmount);
            
            // Risk analysis
            Map<String, Long> riskBreakdown = transactions.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            t -> t.getRiskLevel() != null ? t.getRiskLevel().name() : "UNKNOWN",
                            java.util.stream.Collectors.counting()));
            analytics.put("riskBreakdown", riskBreakdown);
            
            // Transaction type breakdown
            Map<String, Long> typeBreakdown = transactions.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            t -> t.getTransactionType().name(),
                            java.util.stream.Collectors.counting()));
            analytics.put("typeBreakdown", typeBreakdown);
            
            // High-risk transactions
            long highRiskCount = transactions.stream()
                    .filter(t -> t.getRiskLevel() == PaymentTransaction.RiskLevel.HIGH ||
                               t.getRiskLevel() == PaymentTransaction.RiskLevel.CRITICAL)
                    .count();
            analytics.put("highRiskTransactions", highRiskCount);
            
            // Failed transactions
            long failedCount = transactions.stream()
                    .filter(t -> t.getStatus() == PaymentTransaction.TransactionStatus.FAILED)
                    .count();
            analytics.put("failedTransactions", failedCount);
            
        } catch (Exception e) {
            log.error("Error generating user transaction analytics for user {}: {}", userId, e.getMessage());
            analytics.put("error", "Failed to generate analytics");
        }
        
        return analytics;
    }
    
    /**
     * Get fraud trend analysis
     */
    public Map<String, Object> getFraudTrendAnalysis(int days) {
        Map<String, Object> trends = new HashMap<>();
        
        try {
            LocalDateTime startDate = LocalDateTime.now().minus(days, ChronoUnit.DAYS);
            
            // High-risk transactions over time
            List<PaymentTransaction> highRiskTransactions = paymentTransactionRepository
                    .findTransactionsByRiskScore(50);
            
            // Group by date
            Map<String, Long> dailyHighRisk = highRiskTransactions.stream()
                    .filter(t -> t.getCreatedDate().isAfter(startDate))
                    .collect(java.util.stream.Collectors.groupingBy(
                            t -> t.getCreatedDate().toLocalDate().toString(),
                            java.util.stream.Collectors.counting()));
            
            trends.put("dailyHighRiskTransactions", dailyHighRisk);
            trends.put("totalHighRiskTransactions", highRiskTransactions.size());
            
            // Failed fraud checks
            List<PaymentTransaction> failedFraudChecks = paymentTransactionRepository
                    .findFailedFraudCheckTransactions();
            
            Map<String, Long> dailyFailedFraud = failedFraudChecks.stream()
                    .filter(t -> t.getCreatedDate().isAfter(startDate))
                    .collect(java.util.stream.Collectors.groupingBy(
                            t -> t.getCreatedDate().toLocalDate().toString(),
                            java.util.stream.Collectors.counting()));
            
            trends.put("dailyFailedFraudChecks", dailyFailedFraud);
            trends.put("totalFailedFraudChecks", failedFraudChecks.size());
            
            // Fraud prevention effectiveness
            long totalTransactionsSinceDate = paymentTransactionRepository
                    .findByUserIdAndDateRange("", startDate, LocalDateTime.now()).size();
            
            double fraudDetectionRate = totalTransactionsSinceDate > 0 ?
                    (double) failedFraudChecks.size() / totalTransactionsSinceDate * 100 : 0;
            
            trends.put("fraudDetectionRate", 
                      BigDecimal.valueOf(fraudDetectionRate).setScale(2, RoundingMode.HALF_UP));
            
        } catch (Exception e) {
            log.error("Error generating fraud trend analysis: {}", e.getMessage());
            trends.put("error", "Failed to generate fraud trends");
        }
        
        return trends;
    }
} 