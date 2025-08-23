package com.app.greensuitetest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditHistoryDto {
    
    private List<CreditTransactionDto> transactions;
    private int totalTransactions;
    private int currentBalance;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreditTransactionDto {
        private String id;
        private String type;
        private String typeDescription;
        private int amount;
        private int balanceBefore;
        private int balanceAfter;
        private String reason;
        private String conversationId;
        private LocalDateTime timestamp;
    }
} 