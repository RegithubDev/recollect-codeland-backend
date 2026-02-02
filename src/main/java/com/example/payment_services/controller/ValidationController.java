package com.example.payment_services.controller;

import com.example.payment_services.dto.verification.*;
import com.example.payment_services.service.ValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/verification")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Identity Verification", description = "APIs for bank account and PAN card verification services")
public class ValidationController {

    private final ValidationService validationService;

    @Operation(
            summary = "Verify Bank Account",
            description = "Validates bank account details including account number, IFSC code, and account holder name"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Bank account verification completed",
                    content = @Content(schema = @Schema(implementation = ValidationService.BankAccountValidationResult.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid bank account data or missing required fields"
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "Bank account validation failed"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error or external verification service unavailable"
            )
    })
    @PostMapping("/bank-account")
    public ResponseEntity<ValidationService.BankAccountValidationResult> verifyBankAccount(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Bank account verification request with account details",
                    required = true
            )
            @RequestBody BankAccountVerificationRequestDTO request) {

        log.info("Verifying bank account: {}", request.getBankAccount());

        ValidationService.BankAccountValidationResult result =
                validationService.validateBankAccount(request);

        return ResponseEntity.ok(result);
    }

    @Operation(
            summary = "Get Bank Account Verification Status",
            description = "Retrieves the verification status and details for a previously submitted bank account verification request"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Verification status retrieved successfully",
                    content = @Content(schema = @Schema(implementation = BankAccountVerificationResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Verification reference ID not found"
            )
    })
    @GetMapping("/get/bank-account/{referenceId}")
    public ResponseEntity<BankAccountVerificationResponseDTO> getBankAccountVerification(
            @Parameter(
                    description = "Unique reference ID from the verification request",
                    example = "1234567890",
                    required = true
            )
            @PathVariable Long referenceId) {

        log.info("Getting bank account verification: {}", referenceId);

        BankAccountVerificationResponseDTO response =
                validationService.getBankAccountVerificationStatus(referenceId);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Verify PAN Card",
            description = "Validates Permanent Account Number (PAN) details against government records"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "PAN verification completed",
                    content = @Content(schema = @Schema(implementation = ValidationService.PanValidationResult.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid PAN data or incorrect format"
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "PAN validation failed (invalid or fake PAN)"
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Too many verification attempts"
            )
    })
    @PostMapping("/pan")
    public ResponseEntity<ValidationService.PanValidationResult> verifyPan(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "PAN verification request with PAN number and name",
                    required = true
            )
            @RequestBody PanVerificationRequestDTO request) {

        log.info("Verifying PAN: {}", request.getPan());

        ValidationService.PanValidationResult result =
                validationService.validatePan(request);

        return ResponseEntity.ok(result);
    }

    @Operation(
            summary = "Get PAN Verification Status",
            description = "Retrieves the verification status and details for a previously submitted PAN verification request"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "PAN verification status retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PanVerificationResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Verification reference ID not found"
            )
    })
    @GetMapping("/get/pan/{referenceId}")
    public ResponseEntity<PanVerificationResponseDTO> getPanVerification(
            @Parameter(
                    description = "Unique reference ID from the PAN verification request",
                    example = "1234567890",
                    required = true
            )
            @PathVariable Long referenceId) {

        log.info("Getting PAN verification: {}", referenceId);

        PanVerificationResponseDTO response =
                validationService.getPanVerificationStatus(referenceId);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Verification Service Health Check",
            description = "Checks the health and availability of verification services"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Verification services are operational",
                    content = @Content(schema = @Schema(implementation = HealthResponse.class))
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "One or more verification services are unavailable"
            )
    })
    @GetMapping("/health/check")
    public ResponseEntity<?> healthCheck() {
        return ResponseEntity.ok().body(
                new HealthResponse("UP", "Verification API",
                        "Bank Account & PAN Verification")
        );
    }

    @Schema(description = "Health check response for verification services")
    private record HealthResponse(
            @Schema(description = "Service status", example = "UP") String status,
            @Schema(description = "Service name", example = "Verification API") String service,
            @Schema(description = "Available features", example = "Bank Account & PAN Verification") String features
    ) {}
}