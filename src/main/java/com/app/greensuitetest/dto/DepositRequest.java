package com.app.greensuitetest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepositRequest {
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least $0.01")
    @DecimalMax(value = "100000.00", message = "Amount cannot exceed $100,000.00")
    private BigDecimal amount;
    
    @NotBlank(message = "Payment method is required")
    @Pattern(regexp = "^(CARD|BANK_TRANSFER|WALLET|CASH|WIRE_TRANSFER|ACH|CRYPTO)$", 
             message = "Invalid payment method. Allowed: CARD, BANK_TRANSFER, WALLET, CASH, WIRE_TRANSFER, ACH, CRYPTO")
    private String paymentMethod;
    
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
    
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter currency code (e.g., USD, EUR, GBP)")
    @Builder.Default
    private String currency = "USD";
    
    // For bank transfer and wire transfers
    @Size(max = 100, message = "Reference number must not exceed 100 characters")
    @Pattern(regexp = "^[a-zA-Z0-9\\-_]*$", message = "Reference number can only contain letters, numbers, hyphens, and underscores")
    private String referenceNumber;
    
    // Enhanced security fields
    @Size(max = 200, message = "Source account info must not exceed 200 characters")
    private String sourceAccount; // Last 4 digits of card or account identifier
    
    @Pattern(regexp = "^(PERSONAL|BUSINESS)$", message = "Account type must be PERSONAL or BUSINESS")
    @Builder.Default
    private String accountType = "PERSONAL";
    
    // For compliance and fraud detection
    @Size(max = 1000, message = "Additional info must not exceed 1000 characters")
    private String additionalInfo; // Any additional information for compliance
    
    // Metadata for tracking
    @Size(max = 100, message = "Client transaction ID must not exceed 100 characters")
    private String clientTransactionId; // Client-side transaction ID for reconciliation
} 