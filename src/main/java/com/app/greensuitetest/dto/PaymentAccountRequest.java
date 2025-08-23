package com.app.greensuitetest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentAccountRequest {
    
    @NotBlank(message = "Account name is required")
    @Size(min = 2, max = 100, message = "Account name must be between 2 and 100 characters")
    @Pattern(regexp = "^[a-zA-Z0-9\\s\\-'.,&()]+$", message = "Account name contains invalid characters")
    private String accountName;
    
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter currency code (e.g., USD, EUR, GBP)")
    @Builder.Default
    private String currency = "USD";
    
    // Enhanced business information
    @Pattern(regexp = "^(PERSONAL|BUSINESS|ORGANIZATION|NON_PROFIT)$", 
             message = "Account type must be PERSONAL, BUSINESS, ORGANIZATION, or NON_PROFIT")
    @Builder.Default
    private String accountType = "PERSONAL";
    
    // Business-specific fields
    @Size(max = 200, message = "Business name must not exceed 200 characters")
    private String businessName;
    
    @Pattern(regexp = "^[0-9\\-]*$", message = "Tax ID must contain only numbers and hyphens")
    @Size(max = 20, message = "Tax ID must not exceed 20 characters")
    private String taxId;
    
    @Size(max = 100, message = "Industry must not exceed 100 characters")
    private String industry;
    
    // Contact information
    @Email(message = "Contact email must be valid")
    @Size(max = 100, message = "Contact email must not exceed 100 characters")
    private String contactEmail;
    
    @Pattern(regexp = "^[+]?[0-9\\s\\-()]*$", message = "Phone number format is invalid")
    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    private String phoneNumber;
    
    // Address information for compliance
    @Size(max = 200, message = "Address line 1 must not exceed 200 characters")
    private String addressLine1;
    
    @Size(max = 200, message = "Address line 2 must not exceed 200 characters")
    private String addressLine2;
    
    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;
    
    @Size(max = 100, message = "State/Province must not exceed 100 characters")
    private String stateProvince;
    
    @Pattern(regexp = "^[a-zA-Z0-9\\s\\-]*$", message = "Postal code format is invalid")
    @Size(max = 20, message = "Postal code must not exceed 20 characters")
    private String postalCode;
    
    @Pattern(regexp = "^[A-Z]{2,3}$", message = "Country code must be 2-3 letter country code")
    @Builder.Default
    private String countryCode = "US";
    
    // Preferred limits (these will be validated against verification level)
    @Pattern(regexp = "^[0-9]+(\\.[0-9]{1,2})?$", message = "Preferred daily limit must be a valid amount")
    private String preferredDailyLimit;
    
    @Pattern(regexp = "^[0-9]+(\\.[0-9]{1,2})?$", message = "Preferred monthly limit must be a valid amount")
    private String preferredMonthlyLimit;
    
    // Terms and compliance
    @Pattern(regexp = "^(true|false)$", message = "Terms acceptance must be true or false")
    private String termsAccepted = "false";
    
    @Pattern(regexp = "^(true|false)$", message = "Privacy policy acceptance must be true or false")
    private String privacyPolicyAccepted = "false";
} 