package com.example.payment_services.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class TransferStatusResponseDTO {
    @JsonProperty("transfer_id")
    private String transferId;

    @JsonProperty("cf_transfer_id")
    private String cfTransferId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("status_code")
    private String statusCode;

    @JsonProperty("status_description")
    private String statusDescription;

    @JsonProperty("beneficiary_details")
    private BeneficiaryDetails beneficiaryDetails;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("transfer_amount")
    private BigDecimal transferAmount;

    @JsonProperty("transfer_mode")
    private String transferMode;

    @JsonProperty("fundsource_id")
    private String fundsourceId;

    @JsonProperty("added_on")
    private String addedOn;

    @JsonProperty("updated_on")
    private String updatedOn;

    @Data
    public static class BeneficiaryDetails {
        @JsonProperty("beneficiary_id")
        private String beneficiaryId;

        @JsonProperty("beneficiary_instrument_details")
        private InstrumentDetails beneficiaryInstrumentDetails;
    }

    @Data
    public static class InstrumentDetails {
        @JsonProperty("bank_account_number")
        private String bankAccountNumber;

        @JsonProperty("bank_ifsc")
        private String bankIfsc;
    }
}