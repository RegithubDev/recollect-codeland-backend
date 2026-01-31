package com.example.payment_services.dto.verification;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PanVerificationResponseDTO {
    private String pan;
    private String type;

    @JsonProperty("reference_id")
    private Long referenceId;

    @JsonProperty("name_provided")
    private String nameProvided;

    @JsonProperty("registered_name")
    private String registeredName;

    private Boolean valid;
    private String message;

    @JsonProperty("name_match_score")
    private Integer nameMatchScore;

    @JsonProperty("name_match_result")
    private String nameMatchResult;

    @JsonProperty("aadhaar_seeding_status")
    private String aadhaarSeedingStatus;

    @JsonProperty("last_updated_at")
    private String lastUpdatedAt;

    @JsonProperty("name_pan_card")
    private String namePanCard;

    @JsonProperty("pan_status")
    private String panStatus;

    @JsonProperty("aadhaar_seeding_status_desc")
    private String aadhaarSeedingStatusDesc;
}