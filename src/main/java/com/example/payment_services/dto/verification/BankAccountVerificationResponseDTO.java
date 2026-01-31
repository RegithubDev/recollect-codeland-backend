package com.example.payment_services.dto.verification;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class BankAccountVerificationResponseDTO {
    @JsonProperty("reference_id")
    private Long referenceId;

    @JsonProperty("name_at_bank")
    private String nameAtBank;

    @JsonProperty("bank_name")
    private String bankName;

    private String city;
    private String branch;
    private Long micr;

    @JsonProperty("name_match_score")
    private String nameMatchScore;

    @JsonProperty("name_match_result")
    private String nameMatchResult;

    @JsonProperty("account_status")
    private String accountStatus;

    @JsonProperty("account_status_code")
    private String accountStatusCode;

    private String utr;

    @JsonProperty("ifsc_details")
    private IfscDetails ifscDetails;

    @Data
    public static class IfscDetails {
        private String bank;
        private String ifsc;

        @JsonProperty("ifsc_subcode")
        private String ifscSubcode;

        private String address;
        private String city;
        private String state;
        private String branch;
        private String category;

        @JsonProperty("swift_code")
        private String swiftCode;

        private Long micr;
        private Long nbin;
    }
}