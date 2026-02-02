package com.example.payment_services.dto.coa;

import lombok.Data;

@Data
public class UpdateCoaRequest {
    private String description;
    private Boolean isActive;
}
