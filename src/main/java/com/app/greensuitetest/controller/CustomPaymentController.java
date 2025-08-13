package com.app.greensuitetest.controller;

import com.app.greensuitetest.dto.CreditPurchaseRequest;
import com.app.greensuitetest.dto.DepositRequest;
import com.app.greensuitetest.dto.PaymentAccountRequest;
import com.app.greensuitetest.model.PaymentAccount;
import com.app.greensuitetest.model.PaymentTransaction;
import com.app.greensuitetest.service.CustomPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class CustomPaymentController {
    
    private final CustomPaymentService customPaymentService;
    
    /**
     * Create a payment account for the current user
     */
    @PostMapping("/account/create")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PaymentAccount> createPaymentAccount(@Valid @RequestBody PaymentAccountRequest request) {
        PaymentAccount account = customPaymentService.createPaymentAccount(request);
        return ResponseEntity.ok(account);
    }
    
    /**
     * Get user's payment account
     */
    @GetMapping("/account")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PaymentAccount> getPaymentAccount() {
        PaymentAccount account = customPaymentService.getUserPaymentAccount();
        return ResponseEntity.ok(account);
    }
    
    /**
     * Get account statistics
     */
    @GetMapping("/account/statistics")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> getAccountStatistics() {
        Map<String, Object> stats = customPaymentService.getAccountStatistics();
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Process a deposit to user's payment account
     */
    @PostMapping("/deposit")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PaymentTransaction> processDeposit(@Valid @RequestBody DepositRequest request) {
        PaymentTransaction transaction = customPaymentService.processDeposit(request);
        return ResponseEntity.ok(transaction);
    }
    
    /**
     * Purchase credits using account balance
     */
    @PostMapping("/credits/purchase")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PaymentTransaction> purchaseCredits(@Valid @RequestBody CreditPurchaseRequest request) {
        PaymentTransaction transaction = customPaymentService.purchaseCredits(request);
        return ResponseEntity.ok(transaction);
    }
    
    /**
     * Get available credit packages
     */
    @GetMapping("/credits/packages")
    public ResponseEntity<List<Map<String, Object>>> getCreditPackages() {
        List<Map<String, Object>> packages = customPaymentService.getCreditPackages();
        return ResponseEntity.ok(packages);
    }
    
    /**
     * Get transaction history for user
     */
    @GetMapping("/transactions")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<PaymentTransaction>> getTransactionHistory() {
        List<PaymentTransaction> transactions = customPaymentService.getTransactionHistory();
        return ResponseEntity.ok(transactions);
    }
    
    /**
     * Get specific transaction by ID
     */
    @GetMapping("/transactions/{transactionId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PaymentTransaction> getTransaction(@PathVariable String transactionId) {
        PaymentTransaction transaction = customPaymentService.getTransaction(transactionId);
        return ResponseEntity.ok(transaction);
    }
} 