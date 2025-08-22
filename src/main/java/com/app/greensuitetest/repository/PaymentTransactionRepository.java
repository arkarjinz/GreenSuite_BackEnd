package com.app.greensuitetest.repository;

import com.app.greensuitetest.model.PaymentTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    
    // Basic transaction queries
    List<PaymentTransaction> findByUserIdOrderByCreatedDateDesc(String userId);
    
    List<PaymentTransaction> findByAccountNumberOrderByCreatedDateDesc(String accountNumber);
    
    List<PaymentTransaction> findByUserIdAndTransactionTypeOrderByCreatedDateDesc(
        String userId, PaymentTransaction.TransactionType transactionType);
    
    List<PaymentTransaction> findByUserIdAndStatusOrderByCreatedDateDesc(
        String userId, PaymentTransaction.TransactionStatus status);
    
    Optional<PaymentTransaction> findByTransactionId(String transactionId);
    
    Optional<PaymentTransaction> findByReferenceNumber(String referenceNumber);
    
    Page<PaymentTransaction> findByUserId(String userId, Pageable pageable);
    
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.userId = :userId AND pt.createdDate >= :startDate AND pt.createdDate <= :endDate ORDER BY pt.createdDate DESC")
    List<PaymentTransaction> findByUserIdAndDateRange(
        @Param("userId") String userId, 
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate);
    
    // Aggregation queries
    @Query("SELECT SUM(pt.amount) FROM PaymentTransaction pt WHERE pt.userId = :userId AND pt.transactionType = 'DEPOSIT' AND pt.status = 'COMPLETED'")
    BigDecimal getTotalDepositsByUserId(@Param("userId") String userId);
    
    @Query("SELECT SUM(pt.amount) FROM PaymentTransaction pt WHERE pt.userId = :userId AND pt.transactionType = 'WITHDRAWAL' AND pt.status = 'COMPLETED'")
    BigDecimal getTotalWithdrawalsByUserId(@Param("userId") String userId);
    
    @Query("SELECT COUNT(pt) FROM PaymentTransaction pt WHERE pt.userId = :userId AND pt.status = 'COMPLETED'")
    long getCompletedTransactionCountByUserId(@Param("userId") String userId);
    
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.accountNumber = :accountNumber AND pt.transactionType = :transactionType AND pt.status = 'COMPLETED' ORDER BY pt.createdDate DESC")
    List<PaymentTransaction> findCompletedTransactionsByAccountAndType(
        @Param("accountNumber") String accountNumber, 
        @Param("transactionType") PaymentTransaction.TransactionType transactionType);
    
    // Fraud detection and security queries
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.userId = :userId AND pt.riskLevel IN ('HIGH', 'CRITICAL')")
    List<PaymentTransaction> findHighRiskTransactionsByUser(@Param("userId") String userId);
    
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.fraudCheckPassed = false")
    List<PaymentTransaction> findFailedFraudCheckTransactions();
    
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.ipAddress = :ipAddress AND pt.createdDate >= :startDate")
    List<PaymentTransaction> findTransactionsByIpSince(@Param("ipAddress") String ipAddress, 
                                                      @Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.riskScore >= :threshold ORDER BY pt.riskScore DESC")
    List<PaymentTransaction> findTransactionsByRiskScore(@Param("threshold") int threshold);
    
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.status = 'FAILED' AND pt.userId = :userId AND pt.createdDate >= :startDate")
    List<PaymentTransaction> findFailedTransactionsByUserSince(@Param("userId") String userId, 
                                                              @Param("startDate") LocalDateTime startDate);
    
    // Retry and error handling queries
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.status = 'FAILED' AND pt.retryCount < pt.maxRetries AND (pt.nextRetryDate IS NULL OR pt.nextRetryDate <= :currentTime)")
    List<PaymentTransaction> findTransactionsEligibleForRetry(@Param("currentTime") LocalDateTime currentTime);
    
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.status = 'PROCESSING' AND pt.createdDate < :timeout")
    List<PaymentTransaction> findStuckTransactions(@Param("timeout") LocalDateTime timeout);
    
    // Analytics queries
    @Query("SELECT DATE(pt.createdDate) as date, COUNT(pt) as count, SUM(pt.amount) as volume FROM PaymentTransaction pt " +
           "WHERE pt.status = 'COMPLETED' AND pt.createdDate >= :startDate GROUP BY DATE(pt.createdDate) ORDER BY date")
    List<Object[]> getTransactionVolumeByDate(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT pt.transactionType, COUNT(pt), SUM(pt.amount) FROM PaymentTransaction pt " +
           "WHERE pt.status = 'COMPLETED' AND pt.createdDate >= :startDate GROUP BY pt.transactionType")
    List<Object[]> getTransactionStatsByType(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT pt.status, COUNT(pt) FROM PaymentTransaction pt WHERE pt.createdDate >= :startDate GROUP BY pt.status")
    List<Object[]> getTransactionCountByStatus(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT pt.riskLevel, COUNT(pt) FROM PaymentTransaction pt WHERE pt.createdDate >= :startDate GROUP BY pt.riskLevel")
    List<Object[]> getRiskLevelDistribution(@Param("startDate") LocalDateTime startDate);
    
    // High-value and suspicious transaction queries
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.amount >= :threshold AND pt.status = 'COMPLETED' ORDER BY pt.amount DESC")
    List<PaymentTransaction> findLargeTransactions(@Param("threshold") BigDecimal threshold);
    
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.amount >= :threshold AND pt.status IN ('PENDING', 'PROCESSING')")
    List<PaymentTransaction> findPendingLargeTransactions(@Param("threshold") BigDecimal threshold);
    
    @Query("SELECT pt.userId, COUNT(pt), SUM(pt.amount) FROM PaymentTransaction pt " +
           "WHERE pt.status = 'COMPLETED' AND pt.createdDate >= :startDate " +
           "GROUP BY pt.userId HAVING COUNT(pt) >= :transactionThreshold ORDER BY COUNT(pt) DESC")
    List<Object[]> findHighVolumeUsers(@Param("startDate") LocalDateTime startDate, 
                                      @Param("transactionThreshold") long transactionThreshold);
    
    // Payment method and currency queries
    @Query("SELECT pt.paymentMethod, COUNT(pt), SUM(pt.amount) FROM PaymentTransaction pt " +
           "WHERE pt.status = 'COMPLETED' AND pt.createdDate >= :startDate GROUP BY pt.paymentMethod")
    List<Object[]> getStatsByPaymentMethod(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT pt.currency, COUNT(pt), SUM(pt.amount) FROM PaymentTransaction pt " +
           "WHERE pt.status = 'COMPLETED' AND pt.createdDate >= :startDate GROUP BY pt.currency")
    List<Object[]> getStatsByCurrency(@Param("startDate") LocalDateTime startDate);
    
    // Geographic and device queries
    @Query("SELECT pt.geolocation, COUNT(pt) FROM PaymentTransaction pt " +
           "WHERE pt.geolocation IS NOT NULL AND pt.createdDate >= :startDate GROUP BY pt.geolocation ORDER BY COUNT(pt) DESC")
    List<Object[]> getTransactionsByLocation(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.deviceFingerprint = :fingerprint ORDER BY pt.createdDate DESC")
    List<PaymentTransaction> findTransactionsByDeviceFingerprint(@Param("fingerprint") String fingerprint);
    
    // Paginated advanced queries
    Page<PaymentTransaction> findByStatusAndCreatedDateBetween(PaymentTransaction.TransactionStatus status, 
                                                              LocalDateTime startDate, 
                                                              LocalDateTime endDate, 
                                                              Pageable pageable);
    
    Page<PaymentTransaction> findByTransactionTypeAndStatusOrderByCreatedDateDesc(PaymentTransaction.TransactionType type, 
                                                                                 PaymentTransaction.TransactionStatus status, 
                                                                                 Pageable pageable);
    
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.amount >= :minAmount AND pt.amount <= :maxAmount " +
           "AND pt.status = 'COMPLETED' ORDER BY pt.createdDate DESC")
    Page<PaymentTransaction> findTransactionsByAmountRange(@Param("minAmount") BigDecimal minAmount, 
                                                          @Param("maxAmount") BigDecimal maxAmount, 
                                                          Pageable pageable);
    
    // External integration queries
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.externalTransactionId = :externalId")
    Optional<PaymentTransaction> findByExternalTransactionId(@Param("externalId") String externalId);
    
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.webhookReceived = false AND pt.externalTransactionId IS NOT NULL")
    List<PaymentTransaction> findTransactionsAwaitingWebhook();
    
    // Performance and monitoring queries
    @Query("SELECT AVG(TIMESTAMPDIFF(SECOND, pt.createdDate, pt.processedDate)) FROM PaymentTransaction pt " +
           "WHERE pt.status = 'COMPLETED' AND pt.processedDate IS NOT NULL AND pt.createdDate >= :startDate")
    Double getAverageProcessingTimeInSeconds(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT CASE WHEN (SELECT COUNT(pt2) FROM PaymentTransaction pt2 WHERE pt2.createdDate >= :startDate) = 0 " +
           "THEN 0.0 ELSE COUNT(pt) * 100.0 / (SELECT COUNT(pt2) FROM PaymentTransaction pt2 WHERE pt2.createdDate >= :startDate) END " +
           "FROM PaymentTransaction pt WHERE pt.status = 'COMPLETED' AND pt.createdDate >= :startDate")
    Double getSuccessRatePercentage(@Param("startDate") LocalDateTime startDate);
} 