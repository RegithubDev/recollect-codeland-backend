package com.example.payment_services.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "payment_transactions")
@Data
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // ========== ORDER INFORMATION ==========
    @Column(name = "order_id", unique = true, nullable = false)
    private String orderId;

    @Column(name = "cf_order_id", unique = true)
    private String cfOrderId;

    @Column(name = "payment_session_id")
    private String paymentSessionId;

    @Column(name = "order_amount", precision = 12, scale = 2)
    private BigDecimal orderAmount;

    @Column(name = "wallet_amount", precision = 12, scale = 2)
    private BigDecimal walletAmount;

    @Column(name = "real_amount", precision = 12, scale = 2)
    private BigDecimal realAmount;

    @Column(name = "order_currency")
    private String orderCurrency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status")
    private OrderStatus orderStatus = OrderStatus.CREATED;

    @Column(name = "order_note", length = 500)
    private String orderNote;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "order_tags")
    private Map<String, Object> orderTags;

    @Column(name = "order_expiry_time")
    private LocalDateTime orderExpiryTime;

    // ========== PAYMENT INFORMATION ==========
    @Column(name = "cf_payment_id")
    private String cfPaymentId;

    @Column(name = "payment_amount", precision = 12, scale = 2)
    private BigDecimal paymentAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status")
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "payment_method")
    private String paymentMethod; // UPI, CARD, NETBANKING

    @Column(name = "payment_gateway")
    private String paymentGateway = "cashfree";

    @Column(name = "bank_reference")
    private String bankReference;

    @Column(name = "utr")
    private String utr;

    @Column(name = "auth_code")
    private String authCode;

    @Column(name = "card_last4")
    private String cardLast4;

    @Column(name = "card_network")
    private String cardNetwork;

    @Column(name = "gateway_fee", precision = 10, scale = 2)
    private BigDecimal gatewayFee = BigDecimal.ZERO;

    @Column(name = "settlement_amount", precision = 12, scale = 2)
    private BigDecimal settlementAmount;

    @Column(name = "payment_processed_at")
    private LocalDateTime paymentProcessedAt;

    @Column(name = "settlement_date")
    private LocalDateTime settlementDate;

    // ========== REFUND INFORMATION ==========
    @Column(name = "cf_refund_id")
    private String cfRefundId;

    @Column(name = "refund_id")
    private String refundId;

    @Column(name = "refund_amount", precision = 12, scale = 2)
    private BigDecimal refundAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status")
    private RefundStatus refundStatus;

    @Column(name = "refund_note", length = 500)
    private String refundNote;

    @Column(name = "refund_speed")
    private String refundSpeed; // STANDARD, INSTANT

    @Column(name = "refund_arn")
    private String refundArn;

    @Column(name = "refund_charge", precision = 10, scale = 2)
    private BigDecimal refundCharge = BigDecimal.ZERO;

    @Column(name = "refund_mode")
    private String refundMode; // NORMAL

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "refund_speed_details")
    private Map<String, Object> refundSpeedDetails;

    @Column(name = "refund_created_at")
    private LocalDateTime refundCreatedAt;

    @Column(name = "refund_processed_at")
    private LocalDateTime refundProcessedAt;

    // ========== CUSTOMER INFORMATION ==========
    @Embedded
    private CustomerDetails customerDetails;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "customer_json")
    private Map<String, Object> customerJson;

    // ========== EXTENDED DETAILS (JSON) ==========
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "order_meta")
    private Map<String, Object> orderMeta;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "order_splits")
    private Map<String, Object> orderSplits;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "charges")
    private Map<String, Object> charges;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shipping_address")
    private Map<String, Object> shippingAddress;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "billing_address")
    private Map<String, Object> billingAddress;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cart")
    private Map<String, Object> cart;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "offer")
    private Map<String, Object> offer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "refund_splits")
    private Map<String, Object> refundSplits;

    // ========== TIMESTAMPS ==========
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ========== BUSINESS FIELDS ==========
    @Column(name = "merchant_id")
    private String merchantId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "product_id")
    private String productId;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "attempt_count")
    private Integer attemptCount = 0;

    @Column(name = "callback_received")
    private Boolean callbackReceived = false;

    @Column(name = "webhook_payload", columnDefinition = "text")
    private String webhookPayload;

    @Column(name = "entity")
    private String entity = "order";

    // ========== ENUMS ==========
    public enum OrderStatus {
        CREATED, ACTIVE, PAID, FAILED, TERMINATED, EXPIRED
    }

    public enum PaymentStatus {
        PENDING, SUCCESS, FAILED, PROCESSING, CANCELLED
    }

    public enum RefundStatus {
        PENDING, SUCCESS, FAILED, PROCESSING, REVERSED
    }

    // ========== EMBEDDABLE CLASSES ==========
    @Embeddable
    @Data
    public static class CustomerDetails {
        @Column(name = "customer_id")
        private String customerId;

        @Column(name = "customer_name")
        private String customerName;

        @Column(name = "customer_email")
        private String customerEmail;

        @Column(name = "customer_phone")
        private String customerPhone;

        @Column(name = "customer_uid")
        private String customerUid;
    }

    // ========== BUSINESS METHODS ==========
    public boolean isRefundable() {
        return paymentStatus == PaymentStatus.SUCCESS &&
                orderStatus != OrderStatus.TERMINATED &&
                orderStatus != OrderStatus.EXPIRED;
    }

    public BigDecimal getRemainingRefundableAmount() {
        if (refundAmount == null) {
            return orderAmount;
        }
        return orderAmount.subtract(refundAmount);
    }

    public boolean isPartiallyRefunded() {
        return refundAmount != null &&
                refundAmount.compareTo(BigDecimal.ZERO) > 0 &&
                refundAmount.compareTo(orderAmount) < 0;
    }

    public boolean isFullyRefunded() {
        return refundAmount != null &&
                refundAmount.compareTo(orderAmount) >= 0;
    }

    // Update refund details
    public void addRefund(BigDecimal amount, String cfRefundId, String refundId) {
        if (this.refundAmount == null) {
            this.refundAmount = BigDecimal.ZERO;
        }
        this.refundAmount = this.refundAmount.add(amount);
        this.cfRefundId = cfRefundId;
        this.refundId = refundId;
        this.refundStatus = RefundStatus.PENDING;
        this.refundCreatedAt = LocalDateTime.now();
    }

    // Update payment success
    public void markPaymentSuccess(String cfPaymentId, BigDecimal amount, String paymentMethod) {
        this.paymentStatus = PaymentStatus.SUCCESS;
        this.cfPaymentId = cfPaymentId;
        this.paymentAmount = amount;
        this.paymentMethod = paymentMethod;
        this.paymentProcessedAt = LocalDateTime.now();
        this.orderStatus = OrderStatus.PAID;
    }
}