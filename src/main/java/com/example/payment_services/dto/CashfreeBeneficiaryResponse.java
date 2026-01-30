package com.example.payment_services.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CashfreeBeneficiaryResponse {
    @JsonProperty("beneficiary_id")
    private String beneficiaryId;

    @JsonProperty("beneficiary_name")
    private String beneficiaryName;

    @JsonProperty("beneficiary_instrument_details")
    private BeneficiaryInstrumentDetails beneficiaryInstrumentDetails;

    @JsonProperty("beneficiary_contact_details")
    private BeneficiaryContactDetails beneficiaryContactDetails;

    @JsonProperty("beneficiary_status")
    private String beneficiaryStatus;

    @JsonProperty("added_on")
    private String addedOn;

    @Data
    public static class BeneficiaryInstrumentDetails {
        @JsonProperty("bank_account_number")
        private String bankAccountNumber;

        @JsonProperty("bank_ifsc")
        private String bankIfsc;
    }

    @Data
    public static class BeneficiaryContactDetails {
        private String email;
        private String phone;
    }
}