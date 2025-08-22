package com.app.greensuitetest.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transactions", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_transaction_id", columnList = "transaction_id"),
    @Index(name = "idx_account_number", columnList = "account_number"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_transaction_type", columnList = "transaction_type"),
    @Index(name = "idx_created_date", columnList = "created_date"),
    @Index(name = "idx_reference_number", columnList = "reference_number")
})
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
    
    @Column(name = "amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal amount;
    
    @Column(name = "currency", nullable = false)
    @Builder.Default
    private String currency = "USD";
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    private TransactionCategory category;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "reference_number")
    private String referenceNumber;
    
    @Column(name = "payment_method")
    private String paymentMethod;
    
    // Balance tracking
    @Column(name = "balance_before", precision = 15, scale = 2)
    private BigDecimal balanceBefore;
    
    @Column(name = "balance_after", precision = 15, scale = 2)
    private BigDecimal balanceAfter;
    
    // Credit-related fields
    @Column(name = "credits_purchased")
    private Integer creditsPurchased;
    
    @Column(name = "credit_balance_before")
    private Integer creditBalanceBefore;
    
    @Column(name = "credit_balance_after")
    private Integer creditBalanceAfter;
    
    @Column(name = "credit_package")
    private String creditPackage;
    
    // Security and fraud detection
    @Column(name = "ip_address")
    private String ipAddress;
    
    @Column(name = "user_agent")
    private String userAgent;
    
    @Column(name = "geolocation")
    private String geolocation;
    
    @Column(name = "device_fingerprint")
    private String deviceFingerprint;
    
    @Column(name = "risk_score")
    @Builder.Default
    private Integer riskScore = 0;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level")
    @Builder.Default
    private RiskLevel riskLevel = RiskLevel.LOW;
    
    @Column(name = "fraud_check_passed")
    @Builder.Default
    private Boolean fraudCheckPassed = true;
    
    @Column(name = "fraud_reason")
    private String fraudReason;
    
    // Processing details
    @Column(name = "processing_fee", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal processingFee = BigDecimal.ZERO;
    
    @Column(name = "exchange_rate", precision = 10, scale = 6)
    private BigDecimal exchangeRate;
    
    @Column(name = "original_amount", precision = 15, scale = 2)
    private BigDecimal originalAmount;
    
    @Column(name = "original_currency")
    private String originalCurrency;
    
    // External payment integration
    @Column(name = "external_transaction_id")
    private String externalTransactionId;
    
    @Column(name = "external_status")
    private String externalStatus;
    
    @Column(name = "webhook_received")
    @Builder.Default
    private Boolean webhookReceived = false;
    
    // Timestamps
    @CreationTimestamp
    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;
    
    @Column(name = "processed_date")
    private LocalDateTime processedDate;
    
    @Column(name = "completed_date")
    private LocalDateTime completedDate;
    
    @Column(name = "failed_date")
    private LocalDateTime failedDate;
    
    // Retry mechanism
    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;
    
    @Column(name = "max_retries")
    @Builder.Default
    private Integer maxRetries = 3;
    
    @Column(name = "next_retry_date")
    private LocalDateTime nextRetryDate;
    
    // Error handling
    @Column(name = "error_code")
    private String errorCode;
    
    @Column(name = "error_message")
    private String errorMessage;
    
    @Column(name = "failure_reason")
    private String failureReason;
    
    // Metadata for additional information
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "json")
    private JsonNode metadata;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fraud_analysis", columnDefinition = "json")
    private JsonNode fraudAnalysis;
    
    // Enums
    public enum TransactionType {
        DEPOSIT("Account deposit"),
        WITHDRAWAL("Account withdrawal"),
        CREDIT_PURCHASE("AI credit purchase"),
        REFUND("Transaction refund"),
        TRANSFER("Account transfer"),
        FEE("Processing fee"),
        CHARGEBACK("Chargeback"),
        ADJUSTMENT("Balance adjustment");
        
        private final String description;
        
        TransactionType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum TransactionStatus {
        PENDING("Transaction pending"),
        PROCESSING("Transaction processing"),
        COMPLETED("Transaction completed successfully"),
        FAILED("Transaction failed"),
        CANCELLED("Transaction cancelled"),
        REFUNDED("Transaction refunded"),
        DISPUTED("Transaction disputed"),
        CHARGEBACK("Transaction charged back"),
        EXPIRED("Transaction expired");
        
        private final String description;
        
        TransactionStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum TransactionCategory {
        PAYMENT("General payment"),
        SUBSCRIPTION("Subscription payment"),
        CREDIT_PURCHASE("Credit purchase"),
        REFUND("Refund"),
        CASHBACK("Cashback"),
        BONUS("Bonus credit"),
        PENALTY("Penalty fee"),
        MAINTENANCE("Maintenance fee");
        
        private final String description;
        
        TransactionCategory(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum RiskLevel {
        LOW("Low risk transaction"),
        MEDIUM("Medium risk transaction"),
        HIGH("High risk transaction"),
        CRITICAL("Critical risk transaction - requires manual review");
        
        private final String description;
        
        RiskLevel(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    // Utility methods
    public boolean isCompleted() {
        return status == TransactionStatus.COMPLETED;
    }
    
    public boolean isFailed() {
        return status == TransactionStatus.FAILED || status == TransactionStatus.CANCELLED;
    }
    
    public boolean isPending() {
        return status == TransactionStatus.PENDING || status == TransactionStatus.PROCESSING;
    }
    
    public boolean canRetry() {
        return isFailed() && retryCount < maxRetries && 
               (nextRetryDate == null || nextRetryDate.isBefore(LocalDateTime.now()));
    }
    
    public boolean isHighRisk() {
        return riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL;
    }
} 