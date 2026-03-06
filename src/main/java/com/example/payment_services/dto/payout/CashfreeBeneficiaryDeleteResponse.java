package com.example.payment_services.dto.payout;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CashfreeBeneficiaryDeleteResponse {

    @JsonProperty("beneficiary_id")
    private String beneficiaryId;

    @JsonProperty("beneficiary_name")
    private String beneficiaryName;

    @JsonProperty("beneficiary_instrument_details")
    private BeneficiaryInstrumentDetails instrumentDetails;

    @JsonProperty("beneficiary_contact_details")
    private BeneficiaryContactDetails contactDetails;

    @JsonProperty("beneficiary_status")
    private String beneficiaryStatus;

    @JsonProperty("added_on")
    private String addedOn;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BeneficiaryInstrumentDetails {
        @JsonProperty("bank_account_number")
        private String bankAccountNumber;

        @JsonProperty("bank_ifsc")
        private String bankIfsc;

        @JsonProperty("vpa")
        private String vpa;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BeneficiaryContactDetails {
        @JsonProperty("beneficiary_email")
        private String email;

        @JsonProperty("beneficiary_phone")
        private String phone;

        @JsonProperty("beneficiary_country_code")
        private String countryCode;

        @JsonProperty("beneficiary_address")
        private String address;

        @JsonProperty("beneficiary_city")
        private String city;

        @JsonProperty("beneficiary_state")
        private String state;

        @JsonProperty("beneficiary_postal_code")
        private String postalCode;
    }
}