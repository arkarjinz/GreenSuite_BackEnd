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
} 