package com.app.greensuitetest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "credit_transactions")
public class CreditTransaction {
    
    @Id
    private String id;
    
    @Indexed
    private String userId;
    
    private TransactionType type;
    private int amount; // Positive for credits added, negative for credits deducted
    private int balanceBefore;
    private int balanceAfter;
    private String reason;
    private String conversationId; // For chat-related transactions
    
    // Stripe integration fields
    private String stripePaymentIntentId;
    private String stripeCustomerId;
    private String stripeTransactionId;
    private Double paymentAmount;
    private String paymentCurrency;
    private String paymentMethod;
    private String creditPackage;
    
    // Account balance fields (for add money functionality)
    private String accountNumber;
    private Double accountBalanceBefore;
    private Double accountBalanceAfter;
    
    @Indexed
    private LocalDateTime timestamp;
    
    public enum TransactionType {
        CHAT_DEDUCTION("Chat Credit Deduction"),
        CREDIT_PURCHASE("Credit Purchase"),
        ADMIN_GRANT("Admin Credit Grant"),
        REFUND("Credit Refund"),
        AUTO_REFILL("Automatic Credit Refill"),
        ACCOUNT_DEPOSIT("Account Deposit"),
        ACCOUNT_WITHDRAWAL("Account Withdrawal"),
        STRIPE_PAYMENT("Stripe Payment"),
        STRIPE_REFUND("Stripe Refund");
        
        private final String description;
        
        TransactionType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
} 