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
@Table(name = "payment_accounts")
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
    
    @Column(name = "balance", precision = 10, scale = 2, nullable = false)
    private BigDecimal balance;
    
    @Column(name = "currency", nullable = false)
    private String currency;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AccountStatus status;
    
    @CreationTimestamp
    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;
    
    @UpdateTimestamp
    @Column(name = "updated_date", nullable = false)
    private LocalDateTime updatedDate;
    
    @Column(name = "last_transaction_date")
    private LocalDateTime lastTransactionDate;
    
    @Column(name = "total_deposits", precision = 10, scale = 2)
    private BigDecimal totalDeposits;
    
    @Column(name = "total_withdrawals", precision = 10, scale = 2)
    private BigDecimal totalWithdrawals;
    
    @Column(name = "transaction_count")
    private Integer transactionCount;
    
    public enum AccountStatus {
        ACTIVE, SUSPENDED, CLOSED, PENDING_VERIFICATION
    }
} 