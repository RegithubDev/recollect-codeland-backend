package com.example.payment_services.dto.verification;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PanVerificationRequestDTO {

    @NotNull(message = "PAN is required")
    @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]{1}$", message = "Invalid PAN format")
    @JsonProperty("pan")
    private String pan;

    @NotNull(message = "Name is required")
    @JsonProperty("name")
    private String name;
}