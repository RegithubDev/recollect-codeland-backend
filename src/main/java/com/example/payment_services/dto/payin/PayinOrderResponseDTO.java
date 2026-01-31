// PayinOrderResponseDTO.java
package com.example.payment_services.dto.payin;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PayinOrderResponseDTO {
    @JsonProperty("cf_order_id")
    private String cfOrderId;

    @JsonProperty("order_id")
    private String orderId;

    @JsonProperty("order_amount")
    private Double orderAmount;

    @JsonProperty("order_currency")
    private String orderCurrency;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("customer_details")
    private CustomerDetails customerDetails;

    @JsonProperty("payment_session_id")
    private String paymentSessionId;

    @JsonProperty("order_expiry_time")
    private String orderExpiryTime;

    @JsonProperty("order_meta")
    private OrderMeta orderMeta;

    @JsonProperty("order_note")
    private String orderNote;

    @JsonProperty("order_splits")
    private Object[] orderSplits;

    @JsonProperty("order_status")
    private String orderStatus;

    @JsonProperty("order_tags")
    private Object orderTags;

    @JsonProperty("entity")
    private String entity;

    @Data
    public static class CustomerDetails {
        @JsonProperty("customer_id")
        private String customerId;

        @JsonProperty("customer_name")
        private String customerName;

        @JsonProperty("customer_email")
        private String customerEmail;

        @JsonProperty("customer_phone")
        private String customerPhone;

        @JsonProperty("customer_uid")
        private String customerUid;
    }

    @Data
    public static class OrderMeta {
        @JsonProperty("return_url")
        private String returnUrl;

        @JsonProperty("payment_methods")
        private String paymentMethods;

        @JsonProperty("notify_url")
        private String notifyUrl;
    }
}