// PayinOrderUpdateDTO.java
package com.example.payment_services.dto.payin;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PayinOrderUpdateDTO {
    @JsonProperty("order_status")
    private String orderStatus; // "TERMINATED"

    @JsonProperty("order_amount")
    private Double orderAmount;

    @JsonProperty("order_note")
    private String orderNote;
}