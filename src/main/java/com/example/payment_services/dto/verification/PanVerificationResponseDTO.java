package com.example.payment_services.dto.verification;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PanVerificationResponseDTO {

    @JsonProperty("reference_id")
    private Long referenceId;

    @JsonProperty("pan")
    private String pan;

    @JsonProperty("name_provided")
    private String nameProvided;

    @JsonProperty("registered_name")
    private String registeredName;

    @JsonProperty("name_match_score")
    private Double nameMatchScore;

    @JsonProperty("name_match_result")
    private String nameMatchResult;

    @JsonProperty("aadhaar_seeding_status")
    private String aadhaarSeedingStatus;

    @JsonProperty("aadhaar_seeding_status_desc")
    private String aadhaarSeedingStatusDesc;

    @JsonProperty("pan_status")
    private String panStatus;

    @JsonProperty("pan_status_desc")
    private String panStatusDesc;

    @JsonProperty("valid")
    private Boolean valid;

    @JsonProperty("message")
    private String message;

    @JsonProperty("pan_info")
    private Map<String, Object> panInfo;

    // Helper methods
    public boolean isValid() {
        return valid != null && valid;
    }

    public boolean isNameMatched() {
        return nameMatchResult != null &&
                (nameMatchResult.contains("DIRECT_MATCH") ||
                        nameMatchResult.contains("PARTIAL_MATCH"));
    }

    public boolean isAadhaarLinked() {
        return "Y".equals(aadhaarSeedingStatus);
    }
}