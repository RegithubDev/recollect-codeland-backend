package com.example.payment_services.dto.verification;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BankAccountVerificationRequestDTO {

    @NotNull(message = "Bank account number is required")
    @Pattern(regexp = "^[0-9]{9,18}$", message = "Bank account number must be 9-18 digits")
    @JsonProperty("bank_account")
    private String bankAccount;

    @NotNull(message = "IFSC code is required")
    @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "Invalid IFSC code format")
    @JsonProperty("ifsc")
    private String ifsc;

    @JsonProperty("name")
    private String name;

    @JsonProperty("phone")
    @Pattern(regexp = "^[0-9]{10}$", message = "Phone number must be 10 digits")
    private String phone;
}