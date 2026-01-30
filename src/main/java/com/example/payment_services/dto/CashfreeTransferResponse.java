// CashfreeTransferResponse.java
package com.example.payment_services.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CashfreeTransferResponse {
    @JsonProperty("transfer_id")
    private String transferId;

    @JsonProperty("cf_transfer_id")
    private String cfTransferId;  // ✅ NOT "reference_id"

    @JsonProperty("status")
    private String status;  // ✅ NOT "transfer_status"

    @JsonProperty("status_code")
    private String statusCode;

    @JsonProperty("status_description")
    private String statusDescription;

    @JsonProperty("beneficiary_details")
    private TransferStatusResponseDTO.BeneficiaryDetails beneficiaryDetails;  // ✅ Nested object

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("transfer_amount")
    private BigDecimal transferAmount;  // ✅ NOT "amount"

    @JsonProperty("transfer_mode")
    private String transferMode;

    @JsonProperty("added_on")
    private String addedOn;  // ✅ NOT "initiated_on"

    @JsonProperty("updated_on")
    private String updatedOn;  // ✅ NOT "processed_on"


    @Data
    public static class BeneficiaryDetails {
        @JsonProperty("beneficiary_id")
        private String beneficiaryId;

        @JsonProperty("beneficiary_instrument_details")
        private TransferStatusResponseDTO.InstrumentDetails beneficiaryInstrumentDetails;
    }

    @Data
    public static class InstrumentDetails {
        @JsonProperty("bank_account_number")
        private String bankAccountNumber;

        @JsonProperty("bank_ifsc")
        private String bankIfsc;
    }
}

