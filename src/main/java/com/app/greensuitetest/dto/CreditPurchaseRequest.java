package com.app.greensuitetest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditPurchaseRequest {
    
    @NotBlank(message = "Credit package is required")
    @Pattern(regexp = "^(basic|standard|premium|enterprise)$", 
             message = "Invalid credit package. Available: basic, standard, premium, enterprise")
    private String creditPackage;
    
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter currency code")
    private String currency = "USD";
} 