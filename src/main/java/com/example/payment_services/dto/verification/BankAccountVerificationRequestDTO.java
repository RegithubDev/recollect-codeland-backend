package com.example.payment_services.dto.verification;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class BankAccountVerificationRequestDTO {
    @JsonProperty("bank_account")
    private String bankAccount;

    private String ifsc;
    private String name;
    private String phone;
}