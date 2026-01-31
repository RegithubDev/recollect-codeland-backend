package com.example.payment_services.dto.refund;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class RefundRequestDTO {
    @JsonProperty("refund_amount")
    private Double refundAmount;

    @JsonProperty("refund_id")
    private String refundId;

    @JsonProperty("refund_note")
    private String refundNote;

    @JsonProperty("refund_speed")
    private String refundSpeed = "STANDARD";
}