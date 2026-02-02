package com.example.payment_services.dto.wallet;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PayoutResponseDTO {
    private String referenceId;
    private String fundAccountId;
    private String customerId;
    private BigDecimal amount;
    private String contactId;
    private String currency;
}