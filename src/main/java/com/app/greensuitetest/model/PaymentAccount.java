package com.app.greensuitetest.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_accounts", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_account_number", columnList = "account_number"),
    @Index(name = "idx_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentAccount {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "account_number", unique = true, nullable = false)
    private String accountNumber;
    
    @Column(name = "account_name", nullable = false)
    private String accountName;
    
    @Column(name = "balance", precision = 15, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;
    
    @Column(name = "currency", nullable = false)
    @Builder.Default
    private String currency = "USD";
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private AccountStatus status = AccountStatus.PENDING_VERIFICATION;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_level", nullable = false)
    @Builder.Default
    private VerificationLevel verificationLevel = VerificationLevel.BASIC;
    
    // Security and limits
    @Column(name = "daily_limit", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal dailyLimit = new BigDecimal("1000.00");
    
    @Column(name = "monthly_limit", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal monthlyLimit = new BigDecimal("10000.00");
    
    @Column(name = "daily_spent", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal dailySpent = BigDecimal.ZERO;
    
    @Column(name = "monthly_spent", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal monthlySpent = BigDecimal.ZERO;
    
    @Column(name = "last_daily_reset")
    private LocalDateTime lastDailyReset;
    
    @Column(name = "last_monthly_reset")
    private LocalDateTime lastMonthlyReset;
    
    // Security tracking
    @Column(name = "last_login_ip")
    private String lastLoginIp;
    
    @Column(name = "last_transaction_ip")
    private String lastTransactionIp;
    
    @Column(name = "failed_transaction_count")
    @Builder.Default
    private Integer failedTransactionCount = 0;
    
    @Column(name = "last_failed_transaction")
    private LocalDateTime lastFailedTransaction;
    
    @Column(name = "is_frozen")
    @Builder.Default
    private Boolean isFrozen = false;
    
    @Column(name = "frozen_reason")
    private String frozenReason;
    
    @Column(name = "frozen_until")
    private LocalDateTime frozenUntil;
    
    // Timestamps
    @CreationTimestamp
    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;
    
    @UpdateTimestamp
    @Column(name = "updated_date", nullable = false)
    private LocalDateTime updatedDate;
    
    @Column(name = "last_transaction_date")
    private LocalDateTime lastTransactionDate;
    
    // Account statistics
    @Column(name = "total_deposits", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalDeposits = BigDecimal.ZERO;
    
    @Column(name = "total_withdrawals", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalWithdrawals = BigDecimal.ZERO;
    
    @Column(name = "transaction_count")
    @Builder.Default
    private Integer transactionCount = 0;
    
    @Column(name = "successful_transaction_count")
    @Builder.Default
    private Integer successfulTransactionCount = 0;
    
    // Enums
    public enum AccountStatus {
        ACTIVE("Account is active and operational"),
        SUSPENDED("Account is temporarily suspended"),
        CLOSED("Account is permanently closed"),
        PENDING_VERIFICATION("Account pending verification"),
        FROZEN("Account is frozen due to security concerns"),
        LIMITED("Account has limited functionality");
        
        private final String description;
        
        AccountStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum VerificationLevel {
        BASIC("Basic verification - Limited functionality"),
        STANDARD("Standard verification - Normal limits"),
        PREMIUM("Premium verification - Higher limits"),
        ENTERPRISE("Enterprise verification - Maximum limits");
        
        private final String description;
        
        VerificationLevel(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    // Utility methods
    public boolean isActive() {
        return status == AccountStatus.ACTIVE && !isFrozen;
    }
    
    public boolean canProcessTransaction(BigDecimal amount) {
        if (!isActive()) return false;
        if (balance.compareTo(amount) < 0) return false;
        
        // Check daily limit
        if (dailySpent.add(amount).compareTo(dailyLimit) > 0) return false;
        
        // Check monthly limit
        if (monthlySpent.add(amount).compareTo(monthlyLimit) > 0) return false;
        
        return true;
    }
    
    public BigDecimal getAvailableBalance() {
        return balance;
    }
    
    public BigDecimal getRemainingDailyLimit() {
        return dailyLimit.subtract(dailySpent).max(BigDecimal.ZERO);
    }
    
    public BigDecimal getRemainingMonthlyLimit() {
        return monthlyLimit.subtract(monthlySpent).max(BigDecimal.ZERO);
    }
} 