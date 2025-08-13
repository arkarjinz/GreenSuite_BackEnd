package com.app.greensuitetest.payment.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigInteger;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String userName;
    private String userId; // Link to user
    
    @Column(nullable = true)
    private String accountNumber;
    
    private String status; // PENDING, COMPLETED, FAILED, REFUNDED
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
    
    private double amount;
    private BigInteger creditPoints;
    private String paymentMethod; // CARD, BANK_TRANSFER, WALLET
    private String transactionReference; // External payment reference
    
    // Credit purchase specific fields
    private Integer creditsPurchased;
    private String creditPackage; // basic, standard, premium, enterprise
    
    // Stripe integration fields
    private String stripePaymentIntentId;
    private String stripeCustomerId;
    
    @PrePersist
    protected void onCreate() {
        createdDate = LocalDateTime.now();
        updatedDate = LocalDateTime.now();
        if (creditPoints == null) {
            creditPoints = BigInteger.valueOf(50);
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedDate = LocalDateTime.now();
    }
    
    public Payment(String userName, String status, double amount) {
        this.userName = userName;
        this.status = status;
        this.amount = amount;
        this.creditPoints = BigInteger.valueOf(50);
        this.createdDate = LocalDateTime.now();
        this.updatedDate = LocalDateTime.now();
    }
} 