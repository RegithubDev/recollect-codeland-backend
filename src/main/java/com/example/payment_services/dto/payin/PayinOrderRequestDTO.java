// PayinOrderRequestDTO.java
package com.example.payment_services.dto.payin;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PayinOrderRequestDTO {
    @JsonProperty("order_currency")
    private String orderCurrency = "INR";

    @JsonProperty("order_amount")
    private Double orderAmount;

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