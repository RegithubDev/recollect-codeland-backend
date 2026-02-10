package com.example.payment_services.dto.wallet;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class WalletDeductionDTO {
    private String userId;          // User whose wallet is being deducted
    private BigDecimal amount;      // Amount to deduct
    private String referenceId;     // Reference/transaction ID
    private String description;     // Reason for deduction
    private String orderId;         // Optional: associated order ID
    private String paymentMethod = "WALLET"; // Default to WALLET
}