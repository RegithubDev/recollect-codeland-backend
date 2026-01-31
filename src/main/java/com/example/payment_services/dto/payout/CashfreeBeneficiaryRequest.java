package com.example.payment_services.dto.payout;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CashfreeBeneficiaryRequest {
    @JsonProperty("beneficiary_id")
    private String beneficiaryId;

    @JsonProperty("beneficiary_name")
    private String beneficiaryName;

    @JsonProperty("beneficiary_instrument_details")
    private InstrumentDetails instrumentDetails;

    @JsonProperty("beneficiary_contact_details")
    private ContactDetails contactDetails;

    @Data
    public static class InstrumentDetails {
        @JsonProperty("bank_account_number")
        private String bankAccountNumber;

        @JsonProperty("bank_ifsc")
        private String bankIfsc;

        @JsonProperty("vpa")
        private String vpa;
    }

    @Data
    public static class ContactDetails {
        @JsonProperty("beneficiary_email")
        private String beneficiaryEmail;

        @JsonProperty("beneficiary_phone")
        private String beneficiaryPhone;

        @JsonProperty("beneficiary_country_code")
        private String beneficiaryCountryCode;

        @JsonProperty("beneficiary_address")
        private String beneficiaryAddress;

        @JsonProperty("beneficiary_city")
        private String beneficiaryCity;

        @JsonProperty("beneficiary_state")
        private String beneficiaryState;

        @JsonProperty("beneficiary_postal_code")
        private String beneficiaryPostalCode;
    }
}