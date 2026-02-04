package com.example.payment_services.dto.wallet;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class AddToWalletRequest {
    private String userId;
    private BigDecimal amount;
    private String referenceId;
    private String description;
}