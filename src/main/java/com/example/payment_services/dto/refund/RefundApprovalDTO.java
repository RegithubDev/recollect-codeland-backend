package com.example.payment_services.dto.refund;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RefundApprovalDTO {
    private String paymentTransactionId;
    private BigDecimal amount;
    private String approverId;
    private String transactionId;
    private String customerId;
    private String orderId;
}