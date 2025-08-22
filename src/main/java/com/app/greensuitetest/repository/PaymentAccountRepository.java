package com.app.greensuitetest.repository;

import com.app.greensuitetest.model.PaymentAccount;
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
public interface PaymentAccountRepository extends JpaRepository<PaymentAccount, Long> {
    
    // Basic account queries
    Optional<PaymentAccount> findByUserId(String userId);
    
    Optional<PaymentAccount> findByAccountNumber(String accountNumber);
    
    List<PaymentAccount> findByUserIdOrderByCreatedDateDesc(String userId);
    
    List<PaymentAccount> findByStatus(PaymentAccount.AccountStatus status);
    
    @Query("SELECT pa FROM PaymentAccount pa WHERE pa.userId = :userId AND pa.status = 'ACTIVE'")
    Optional<PaymentAccount> findActiveAccountByUserId(@Param("userId") String userId);
    
    boolean existsByUserId(String userId);
    
    boolean existsByAccountNumber(String accountNumber);
    
    @Query("SELECT COUNT(pa) FROM PaymentAccount pa WHERE pa.userId = :userId")
    long countByUserId(@Param("userId") String userId);
    
    // Security and fraud detection queries
    @Query("SELECT pa FROM PaymentAccount pa WHERE pa.failedTransactionCount >= :threshold")
    List<PaymentAccount> findAccountsWithHighFailureRate(@Param("threshold") int threshold);
    
    @Query("SELECT pa FROM PaymentAccount pa WHERE pa.isFrozen = true")
    List<PaymentAccount> findFrozenAccounts();
    
    @Query("SELECT pa FROM PaymentAccount pa WHERE pa.frozenUntil IS NOT NULL AND pa.frozenUntil <= :currentTime")
    List<PaymentAccount> findAccountsReadyForUnfreeze(@Param("currentTime") LocalDateTime currentTime);
    
    @Query("SELECT pa FROM PaymentAccount pa WHERE pa.lastTransactionIp = :ipAddress")
    List<PaymentAccount> findAccountsByLastTransactionIp(@Param("ipAddress") String ipAddress);
    
    // Analytics queries
    @Query("SELECT pa FROM PaymentAccount pa WHERE pa.createdDate >= :startDate AND pa.createdDate <= :endDate")
    List<PaymentAccount> findAccountsCreatedBetween(@Param("startDate") LocalDateTime startDate, 
                                                   @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT pa.verificationLevel, COUNT(pa) FROM PaymentAccount pa GROUP BY pa.verificationLevel")
    List<Object[]> getAccountCountByVerificationLevel();
    
    @Query("SELECT pa.status, COUNT(pa) FROM PaymentAccount pa GROUP BY pa.status")
    List<Object[]> getAccountCountByStatus();
    
    @Query("SELECT SUM(pa.balance) FROM PaymentAccount pa WHERE pa.status = 'ACTIVE'")
    BigDecimal getTotalActiveAccountBalance();
    
    @Query("SELECT AVG(pa.balance) FROM PaymentAccount pa WHERE pa.status = 'ACTIVE'")
    BigDecimal getAverageAccountBalance();
    
    // Limit and spending queries
    @Query("SELECT pa FROM PaymentAccount pa WHERE pa.dailySpent >= pa.dailyLimit * 0.9")
    List<PaymentAccount> findAccountsNearDailyLimit();
    
    @Query("SELECT pa FROM PaymentAccount pa WHERE pa.monthlySpent >= pa.monthlyLimit * 0.9")
    List<PaymentAccount> findAccountsNearMonthlyLimit();
    
    @Query("SELECT pa FROM PaymentAccount pa WHERE pa.lastDailyReset IS NULL OR pa.lastDailyReset < :resetTime")
    List<PaymentAccount> findAccountsNeedingDailyReset(@Param("resetTime") LocalDateTime resetTime);
    
    @Query("SELECT pa FROM PaymentAccount pa WHERE pa.lastMonthlyReset IS NULL OR pa.lastMonthlyReset < :resetTime")
    List<PaymentAccount> findAccountsNeedingMonthlyReset(@Param("resetTime") LocalDateTime resetTime);
    
    // High-value account queries
    @Query("SELECT pa FROM PaymentAccount pa WHERE pa.balance >= :threshold ORDER BY pa.balance DESC")
    List<PaymentAccount> findHighValueAccounts(@Param("threshold") BigDecimal threshold);
    
    @Query("SELECT pa FROM PaymentAccount pa WHERE pa.totalDeposits >= :threshold ORDER BY pa.totalDeposits DESC")
    List<PaymentAccount> findHighVolumeAccounts(@Param("threshold") BigDecimal threshold);
    
    // Paginated queries
    Page<PaymentAccount> findByStatusOrderByCreatedDateDesc(PaymentAccount.AccountStatus status, Pageable pageable);
    
    Page<PaymentAccount> findByVerificationLevelOrderByCreatedDateDesc(PaymentAccount.VerificationLevel level, Pageable pageable);
    
    @Query("SELECT pa FROM PaymentAccount pa WHERE pa.balance >= :minBalance AND pa.balance <= :maxBalance ORDER BY pa.balance DESC")
    Page<PaymentAccount> findAccountsByBalanceRange(@Param("minBalance") BigDecimal minBalance, 
                                                   @Param("maxBalance") BigDecimal maxBalance, 
                                                   Pageable pageable);
    
    // Compliance and monitoring
    @Query("SELECT pa FROM PaymentAccount pa WHERE pa.lastTransactionDate < :inactiveThreshold")
    List<PaymentAccount> findInactiveAccounts(@Param("inactiveThreshold") LocalDateTime inactiveThreshold);
    
    @Query("SELECT pa FROM PaymentAccount pa WHERE pa.successfulTransactionCount = 0 AND pa.createdDate < :threshold")
    List<PaymentAccount> findUnusedAccounts(@Param("threshold") LocalDateTime threshold);
    
    // Advanced analytics
    @Query("SELECT DATE(pa.createdDate) as date, COUNT(pa) as count FROM PaymentAccount pa " +
           "WHERE pa.createdDate >= :startDate GROUP BY DATE(pa.createdDate) ORDER BY date")
    List<Object[]> getAccountCreationTrends(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT pa.currency, COUNT(pa), SUM(pa.balance) FROM PaymentAccount pa " +
           "WHERE pa.status = 'ACTIVE' GROUP BY pa.currency")
    List<Object[]> getAccountStatsByCurrency();
} 