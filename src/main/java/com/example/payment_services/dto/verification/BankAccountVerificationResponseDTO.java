package com.example.payment_services.dto.verification;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BankAccountVerificationResponseDTO {

    @JsonProperty("reference_id")
    private Long referenceId;

    @JsonProperty("name_at_bank")
    private String nameAtBank;

    @JsonProperty("bank_name")
    private String bankName;

    @JsonProperty("utr")
    private String utr;

    @JsonProperty("city")
    private String city;

    @JsonProperty("branch")
    private String branch;

    @JsonProperty("micr")
    private String micr;

    @JsonProperty("name_match_score")
    private Double nameMatchScore;

    @JsonProperty("name_match_result")
    private String nameMatchResult;

    @JsonProperty("account_status")
    private String accountStatus;

    @JsonProperty("account_status_code")
    private String accountStatusCode;

    @JsonProperty("ifsc_details")
    private Map<String, Object> ifscDetails;

    // Additional fields for error responses
    private String type;
    private String code;
    private String message;
    private Map<String, Object> error;

    // Helper methods
    public boolean isValid() {
        return "VALID".equals(accountStatus) ||
                "ACCOUNT_IS_VALID".equals(accountStatusCode);
    }

    public boolean isNameMatched() {
        return nameMatchResult != null &&
                (nameMatchResult.contains("DIRECT_MATCH") ||
                        nameMatchResult.contains("PARTIAL_MATCH"));
    }
}