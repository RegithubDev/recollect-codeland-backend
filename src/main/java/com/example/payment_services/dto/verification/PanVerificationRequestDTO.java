package com.example.payment_services.dto.verification;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PanVerificationRequestDTO {
    private String pan;
    private String name;
}