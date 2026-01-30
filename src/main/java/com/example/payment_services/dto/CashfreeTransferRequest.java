// CashfreeTransferRequest.java - For direct API call
package com.example.payment_services.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CashfreeTransferRequest {
    @JsonProperty("transfer_id")
    private String transferId;

    @JsonProperty("transfer_amount")
    private BigDecimal transferAmount;

    @JsonProperty("beneficiary_details")
    private BeneficiaryDetails beneficiaryDetails;

    @JsonProperty("transfer_mode")
    private String transferMode;

    @JsonProperty("transfer_currency")
    private String transferCurrency = "INR";

    @JsonProperty("remarks")
    private String remarks;

    @JsonProperty("transfer_label")
    private String transferLabel;

    @Data
    public static class BeneficiaryDetails {
        @JsonProperty("beneficiary_id")
        private String beneficiaryId;
    }
}