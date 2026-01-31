// RefundResponseDTO.java
package com.example.payment_services.dto.refund;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class RefundResponseDTO {
    @JsonProperty("cf_payment_id")
    private String cfPaymentId;

    @JsonProperty("cf_refund_id")
    private String cfRefundId;

    @JsonProperty("refund_id")
    private String refundId;

    @JsonProperty("order_id")
    private String orderId;

    @JsonProperty("refund_amount")
    private Double refundAmount;

    @JsonProperty("refund_currency")
    private String refundCurrency;

    @JsonProperty("refund_note")
    private String refundNote;

    @JsonProperty("refund_status")
    private String refundStatus;

    @JsonProperty("refund_type")
    private String refundType;

    @JsonProperty("refund_arn")
    private String refundArn;

    @JsonProperty("status_description")
    private String statusDescription;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("processed_at")
    private String processedAt;

    @JsonProperty("refund_charge")
    private Double refundCharge;

    @JsonProperty("refund_mode")
    private String refundMode;

    @JsonProperty("refund_speed")
    private RefundSpeed refundSpeed;

    @JsonProperty("metadata")
    private Object metadata;

    @JsonProperty("refund_splits")
    private Object[] refundSplits;

    @Data
    public static class RefundSpeed {
        @JsonProperty("requested")
        private String requested;

        @JsonProperty("accepted")
        private String accepted;

        @JsonProperty("processed")
        private String processed;

        @JsonProperty("message")
        private String message;
    }
}