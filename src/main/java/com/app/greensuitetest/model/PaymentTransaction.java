package com.app.greensuitetest.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "account_number", nullable = false)
    private String accountNumber;
    
    @Column(name = "transaction_id", unique = true, nullable = false)
    private String transactionId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;
    
    @Column(name = "amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;
    
    @Column(name = "currency", nullable = false)
    private String currency;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "reference_number")
    private String referenceNumber;
    
    @Column(name = "payment_method")
    private String paymentMethod;
    
    @Column(name = "balance_before", precision = 10, scale = 2)
    private BigDecimal balanceBefore;
    
    @Column(name = "balance_after", precision = 10, scale = 2)
    private BigDecimal balanceAfter;
    
    @Column(name = "credits_purchased")
    private Integer creditsPurchased;
    
    @Column(name = "credit_balance_before")
    private Integer creditBalanceBefore;
    
    @Column(name = "credit_balance_after")
    private Integer creditBalanceAfter;
    
    @Column(name = "credit_package")
    private String creditPackage;
    
    @CreationTimestamp
    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;
    
    @Column(name = "processed_date")
    private LocalDateTime processedDate;
    
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
    
    public enum TransactionType {
        DEPOSIT, WITHDRAWAL, CREDIT_PURCHASE, REFUND, TRANSFER
    }
    
    public enum TransactionStatus {
        PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED, REFUNDED
    }
} 