// PayinOrderRequestDTO.java
package com.example.payment_services.dto.payin;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PayinOrderRequestDTO {
    @JsonProperty("order_currency")
    private String orderCurrency = "INR";

    @JsonProperty("order_amount")
    private Double orderAmount;

    @JsonProperty("wallet_amount")
    private BigDecimal walletAmount;

    @JsonProperty("real_amount")
    private BigDecimal realAmount;

    @JsonProperty("customer_details")
    private CustomerDetails customerDetails;

    @JsonProperty("order_note")
    private String orderNote;

    @JsonProperty("order_tags")
    private Object orderTags;

    @Data
    public static class CustomerDetails {
        @JsonProperty("customer_id")
        private String customerId;

        @JsonProperty("customer_phone")
        private String customerPhone;

        @JsonProperty("customer_name")
        private String customerName;

        @JsonProperty("customer_email")
        private String customerEmail;
    }
}