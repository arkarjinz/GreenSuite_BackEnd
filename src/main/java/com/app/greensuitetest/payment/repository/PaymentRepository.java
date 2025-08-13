package com.app.greensuitetest.payment.repository;

import com.app.greensuitetest.payment.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findPaymentByAccountNumber(String accountNumber);
    
    List<Payment> findByUserIdOrderByCreatedDateDesc(String userId);
    
    List<Payment> findByStatus(String status);
    
    List<Payment> findByPaymentMethod(String paymentMethod);
    
    @Query("SELECT p FROM Payment p WHERE p.createdDate BETWEEN :startDate AND :endDate")
    List<Payment> findPaymentsBetweenDates(@Param("startDate") LocalDateTime startDate, 
                                          @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT p FROM Payment p WHERE p.userId = :userId AND p.status = 'COMPLETED'")
    List<Payment> findCompletedPaymentsByUserId(@Param("userId") String userId);
    
    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.userId = :userId AND p.status = 'COMPLETED'")
    Double getTotalSpentByUserId(@Param("userId") String userId);
    
    Optional<Payment> findByUserId(String userId);
    
    Optional<Payment> findByTransactionReference(String transactionReference);
    
    // Stripe-related queries
    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);
    
    List<Payment> findByStripeCustomerId(String stripeCustomerId);
    
    @Query("SELECT p FROM Payment p WHERE p.stripeCustomerId = :customerId AND p.status = 'COMPLETED'")
    List<Payment> findCompletedPaymentsByStripeCustomerId(@Param("customerId") String customerId);
} 