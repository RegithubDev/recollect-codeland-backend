package com.example.payment_services.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static com.example.payment_services.util.SecurityUtil.getCurrentUserId;

@Entity
@Table(name = "payment_transactions")
@Data
public class PaymentTransaction {

    // ============ PRIMARY KEY ============
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ============ TRANSACTION IDENTIFIERS ============
    @Column(name = "transaction_id", unique = true, nullable = false, length = 100)
    private String transactionId;

    @Column(name = "razorpay_payment_id", length = 100)
    private String razorpayPaymentId;

    @Column(name = "razorpay_order_id", length = 100)
    private String razorpayOrderId;

    @Column(name = "razorpay_refund_id", length = 100)
    private String razorpayRefundId;

    // ============ BUSINESS IDENTIFIERS ============
    @Column(name = "customer_id", nullable = false, length = 50)
    private String customerId;

    @Column(name = "order_id", length = 50)
    private String orderId;

    // ============ TRANSACTION DETAILS ============
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 30)
    private PaymentMethod paymentMethod;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 10)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TransactionStatus status;

    // ============ REFUND FIELDS ============
    @Column(name = "refund_requested_at")
    private LocalDateTime refundRequestedAt;

    @Column(name = "refund_approved_by", length = 50)
    private String refundApprovedBy;

    @Column(name = "refund_approved_at")
    private LocalDateTime refundApprovedAt;

    @Column(name = "refund_approval_remark", columnDefinition = "TEXT")
    private String refundApprovalRemark;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status", length = 30)
    private RefundStatus refundStatus;

    @Column(name = "refund_requester_id", length = 50)
    private String refundRequesterId;

    @Column(name = "refund_reason", length = 500)
    private String refundReason;

    @Column(name = "refund_approver_id", length = 50)
    private String refundApproverId;

    @Column(name = "approval_comments", columnDefinition = "TEXT")
    private String approvalComments;

    @Column(name = "refund_processed_at")
    private LocalDateTime refundProcessedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_approval_status", length = 30)
    private RefundApprovalStatus refundApprovalStatus = RefundApprovalStatus.PENDING;

    @Column(name = "is_partial_refund")
    private Boolean isPartialRefund = false;

    @Column(name = "approval_reference", length = 100)
    private String approvalReference;

    @Column(name = "refund_amount", precision = 10, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "original_transaction_id", length = 100)
    private String originalTransactionId;

    // ============ 3 METADATA SECTIONS ============
    @Column(name = "order_metadata", columnDefinition = "JSON")
    private String orderMetadata;

    @Column(name = "payment_metadata", columnDefinition = "JSON")
    private String paymentMetadata;

    @Column(name = "refund_metadata", columnDefinition = "JSON")
    private String refundMetadata;

    // ============ TIMESTAMPS ============
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_uid", updatable = false)
    private String createdUid;

    @Column(name = "updated_uid")
    private String updatedUid;

    // ============ ENUM DEFINITIONS ============
    public enum TransactionType {
        PAYIN, REFUND, WALLET_TRANSFER
    }

    public enum PaymentMethod {
        CARD, UPI, NETBANKING, WALLET, EMI
    }

    public enum TransactionStatus {
        CREATED, PENDING, CAPTURED, FAILED
    }

    public enum RefundStatus {
        NONE, REQUESTED, PENDING, PROCESSED, FAILED
    }

    public enum RefundApprovalStatus {
        NOT_APPLICABLE, PENDING, APPROVED, REJECTED
    }

    // ============ CORRECTED ACTION-BASED METHODS ============

    /**
     * METHOD 1: For ORDER CREATE action
     * Called when creating a new payment order
     */
    public void setOrderCreateFields(String transactionId, String customerId, String orderId,
                                     BigDecimal amount, String razorpayOrderId, JSONObject razorpayResponse) {
        this.transactionId = transactionId;
        this.customerId = customerId;
        this.orderId = orderId;
        this.amount = amount;
        this.razorpayOrderId = razorpayOrderId;
        this.transactionType = TransactionType.PAYIN;
        this.status = TransactionStatus.CREATED;
        this.updatedUid = getCurrentUserId();
        this.updatedAt = LocalDateTime.now();

        // Set order metadata - CREATE NEW
        JSONObject orderMeta = new JSONObject();
        JSONObject createOrder = new JSONObject();
        createOrder.put("razorpay_order_id", razorpayOrderId);
        createOrder.put("razorpay_response", razorpayResponse);
        createOrder.put("created_at", LocalDateTime.now().toString());
        orderMeta.put("create_order", createOrder);
        this.orderMetadata = orderMeta.toString(); // Use = not +=
    }

    /**
     * METHOD 2: For PAYMENT CAPTURE action
     * Called when payment is successfully captured
     */
    public void setPaymentCaptureFields(String razorpayPaymentId, String paymentMethod,
                                        JSONObject paymentDetails, JSONObject webhookPayload) {
        this.razorpayPaymentId = razorpayPaymentId;
        this.paymentMethod = PaymentMethod.valueOf(paymentMethod.toUpperCase());
        this.status = TransactionStatus.CAPTURED;
        this.updatedUid = getCurrentUserId();
        this.updatedAt = LocalDateTime.now();

        // Set payment metadata - CREATE NEW or MERGE
        JSONObject paymentMeta = this.paymentMetadata != null ?
                new JSONObject(this.paymentMetadata) : new JSONObject();

        JSONObject captureDetails = new JSONObject();
        captureDetails.put("razorpay_payment_id", razorpayPaymentId);
        captureDetails.put("captured_at", LocalDateTime.now().toString());
        captureDetails.put("payment_method", paymentMethod);
        captureDetails.put("payment_details", paymentDetails);
        captureDetails.put("webhook_payload", webhookPayload);

        paymentMeta.put("payment_capture", captureDetails);
        this.paymentMetadata = paymentMeta.toString();
    }

    /**
     * METHOD 3: For PAYMENT VERIFICATION action
     * Called when manually verifying payment (QR/UPI)
     */
    public void setPaymentVerificationFields(String razorpayPaymentId, boolean signatureVerified,
                                             JSONObject verificationDetails) {
        this.razorpayPaymentId = razorpayPaymentId;
        this.status = TransactionStatus.CAPTURED;
        this.updatedUid = getCurrentUserId();
        this.updatedAt = LocalDateTime.now();

        // Update payment metadata - MERGE with existing
        JSONObject paymentMeta = this.paymentMetadata != null ?
                new JSONObject(this.paymentMetadata) : new JSONObject();

        JSONObject verification = new JSONObject();
        verification.put("verified_at", LocalDateTime.now().toString());
        verification.put("signature_verified", signatureVerified);
        verification.put("verification_details", verificationDetails);

        paymentMeta.put("payment_verification", verification);
        this.paymentMetadata = paymentMeta.toString();
    }

    /**
     * METHOD 4: For REFUND REQUEST action
     * Called when customer requests a refund
     */
    public void setRefundRequestFields(String refundTransactionId, String originalTransactionId,
                                       BigDecimal refundAmount, String reason, String requesterId) {
        this.transactionId = refundTransactionId;
        this.originalTransactionId = originalTransactionId;
        this.refundAmount = refundAmount;
        this.refundReason = reason;
        this.refundRequesterId = requesterId;
        this.transactionType = TransactionType.REFUND;
        this.status = TransactionStatus.PENDING;
        this.refundStatus = RefundStatus.REQUESTED;
        this.refundApprovalStatus = RefundApprovalStatus.PENDING;
        this.refundRequestedAt = LocalDateTime.now();
        this.updatedUid = getCurrentUserId();
        this.updatedAt = LocalDateTime.now();

        // Set refund metadata - CREATE NEW
        JSONObject refundMeta = new JSONObject();
        JSONObject requestDetails = new JSONObject();
        requestDetails.put("original_transaction_id", originalTransactionId);
        requestDetails.put("requested_amount", refundAmount.toString());
        requestDetails.put("reason", reason);
        requestDetails.put("requester_id", requesterId);
        requestDetails.put("requested_at", LocalDateTime.now().toString());

        refundMeta.put("refund_request", requestDetails);
        this.refundMetadata = refundMeta.toString();
    }

    /**
     * METHOD 5: For REFUND APPROVAL action
     * Called when admin approves/rejects refund
     */
    public void setRefundApprovalFields(boolean isApproved, String approverId,
                                        String comments, String reference) {
        this.refundApproverId = approverId;
        this.refundApprovedBy = approverId;
        this.approvalComments = comments;
        this.approvalReference = reference;
        this.refundApprovedAt = LocalDateTime.now();
        this.updatedUid = getCurrentUserId();
        this.updatedAt = LocalDateTime.now();

        if (isApproved) {
            this.refundApprovalStatus = RefundApprovalStatus.APPROVED;
            this.refundStatus = RefundStatus.PENDING;
        } else {
            this.refundApprovalStatus = RefundApprovalStatus.REJECTED;
            this.status = TransactionStatus.FAILED;
        }

        // Update refund metadata - MERGE with existing
        JSONObject refundMeta = this.refundMetadata != null ?
                new JSONObject(this.refundMetadata) : new JSONObject();

        JSONObject approvalDetails = new JSONObject();
        approvalDetails.put("approver_id", approverId);
        approvalDetails.put("is_approved", isApproved);
        approvalDetails.put("comments", comments);
        approvalDetails.put("reference", reference);
        approvalDetails.put("approved_at", LocalDateTime.now().toString());

        refundMeta.put("refund_approval", approvalDetails);
        this.refundMetadata = refundMeta.toString();
    }

    /**
     * METHOD 6: For REFUND PROCESSING action
     * Called when refund is processed with Razorpay
     */
    public void setRefundProcessingFields(String razorpayRefundId, JSONObject razorpayResponse) {
        this.razorpayRefundId = razorpayRefundId;
        this.refundStatus = RefundStatus.PROCESSED;
        this.refundApprovalStatus = RefundApprovalStatus.APPROVED;
        this.status = TransactionStatus.CAPTURED;
        this.refundProcessedAt = LocalDateTime.now();
        this.updatedUid = getCurrentUserId();
        this.updatedAt = LocalDateTime.now();

        // Update refund metadata - MERGE with existing
        JSONObject refundMeta = this.refundMetadata != null ?
                new JSONObject(this.refundMetadata) : new JSONObject();

        JSONObject processingDetails = new JSONObject();
        processingDetails.put("razorpay_refund_id", razorpayRefundId);
        processingDetails.put("razorpay_response", razorpayResponse);
        processingDetails.put("processed_at", LocalDateTime.now().toString());

        refundMeta.put("refund_processing", processingDetails);
        this.refundMetadata = refundMeta.toString();
    }

    /**
     * METHOD 7: For PAYMENT FAILURE action
     * Called when payment fails
     */
    public void setPaymentFailureFields(String failureReason, JSONObject failureDetails) {
        this.status = TransactionStatus.FAILED;
        this.updatedUid = getCurrentUserId();
        this.updatedAt = LocalDateTime.now();

        // Update payment metadata - MERGE with existing
        JSONObject paymentMeta = this.paymentMetadata != null ?
                new JSONObject(this.paymentMetadata) : new JSONObject();

        JSONObject failureInfo = new JSONObject();
        failureInfo.put("failed_at", LocalDateTime.now().toString());
        failureInfo.put("failure_reason", failureReason);
        failureInfo.put("failure_details", failureDetails);

        paymentMeta.put("payment_failure", failureInfo);
        this.paymentMetadata = paymentMeta.toString();
    }

    /**
     * HELPER: Merge JSON objects properly
     */
    private JSONObject mergeJson(JSONObject existing, String key, JSONObject newData) {
        if (existing.has(key)) {
            // Merge existing and new data
            JSONObject existingData = existing.getJSONObject(key);
            for (String newKey : newData.keySet()) {
                existingData.put(newKey, newData.get(newKey));
            }
            existing.put(key, existingData);
        } else {
            // Add new data
            existing.put(key, newData);
        }
        return existing;
    }

    /**
     * HELPER: Add history entry to refund metadata
     */
    public void addRefundHistory(String action, String userId, String notes) {
        if (this.refundMetadata == null) {
            this.refundMetadata = new JSONObject().toString();
        }

        JSONObject refundMeta = new JSONObject(this.refundMetadata);
        if (!refundMeta.has("history")) {
            refundMeta.put("history", new org.json.JSONArray());
        }

        org.json.JSONArray history = refundMeta.getJSONArray("history");
        JSONObject historyEntry = new JSONObject();
        historyEntry.put("action", action);
        historyEntry.put("user_id", userId);
        historyEntry.put("timestamp", LocalDateTime.now().toString());
        historyEntry.put("notes", notes);

        history.put(historyEntry);
        refundMeta.put("history", history);
        this.refundMetadata = refundMeta.toString();
    }

    /**
     * HELPER: Get order metadata as JSONObject
     */
    public JSONObject getOrderMetadataJson() {
        return this.orderMetadata != null ? new JSONObject(this.orderMetadata) : new JSONObject();
    }

    /**
     * HELPER: Get payment metadata as JSONObject
     */
    public JSONObject getPaymentMetadataJson() {
        return this.paymentMetadata != null ? new JSONObject(this.paymentMetadata) : new JSONObject();
    }

    /**
     * HELPER: Get refund metadata as JSONObject
     */
    public JSONObject getRefundMetadataJson() {
        return this.refundMetadata != null ? new JSONObject(this.refundMetadata) : new JSONObject();
    }

    /**
     * HELPER: Update specific field in metadata
     */
    public void updateOrderMetadataField(String field, Object value) {
        JSONObject orderMeta = getOrderMetadataJson();
        orderMeta.put(field, value);
        this.orderMetadata = orderMeta.toString();
    }

    public void updatePaymentMetadataField(String field, Object value) {
        JSONObject paymentMeta = getPaymentMetadataJson();
        paymentMeta.put(field, value);
        this.paymentMetadata = paymentMeta.toString();
    }

    public void updateRefundMetadataField(String field, Object value) {
        JSONObject refundMeta = getRefundMetadataJson();
        refundMeta.put(field, value);
        this.refundMetadata = refundMeta.toString();
    }
}