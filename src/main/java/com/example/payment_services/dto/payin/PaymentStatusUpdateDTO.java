package com.example.payment_services.dto.payin;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentStatusUpdateDTO {
    private String orderId;           // Your internal order ID
    private String cfOrderId;         // Cashfree order ID
    private String cfPaymentId;       // Cashfree payment ID
    private String paymentStatus;     // SUCCESS, FAILED, etc.
    private BigDecimal amount;        // Actual amount paid
    private String paymentMethod;     // UPI, CARD, NETBANKING, etc.
    private String bankReference;     // Bank reference number
    private String utr;               // UTR for bank transfers
    private String authCode;          // Auth code for cards
    private String paymentGateway;    // cashfree, razorpay, etc.
    private LocalDateTime paymentTime; // When payment was made
}