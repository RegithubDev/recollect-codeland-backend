package com.example.payment_services.controller;

import com.example.payment_services.dto.payout.*;
import com.example.payment_services.service.http.CashfreePayoutHttpService;
import com.example.payment_services.service.PayoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payouts")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Payout Operations", description = "APIs for managing beneficiary creation and fund transfers")
public class PayoutController {

    private final PayoutService payoutService;
    private final CashfreePayoutHttpService cashfreePayoutHttpService;

    @Operation(
            summary = "Create Beneficiary",
            description = "Adds a new beneficiary for payouts with bank account/UPI details" +
                    "mode:(banktransfer, imps, neft, rtgs, upi, paytm, amazonpay, card, cardupi)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Beneficiary created successfully",
                    content = @Content(schema = @Schema(implementation = Map.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid beneficiary data or validation failed"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Beneficiary already exists"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error or Cashfree API failure"
            )
    })
    @PostMapping("/add/beneficiary")
    public ResponseEntity<Map<String, Object>> createBeneficiary(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Beneficiary details including bank/UPI information",
                    required = true
            )
            @Valid @RequestBody CashfreeBeneficiaryRequest request) {

        log.info("Creating beneficiary: {}", request.getBeneficiaryId());

        CashfreeBeneficiaryResponse response = cashfreePayoutHttpService.createBeneficiary(request);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("beneficiaryId", response.getBeneficiaryId());
        result.put("status", response.getBeneficiaryStatus());
        result.put("message", "Beneficiary created successfully");

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @Operation(
            summary = "Initiate Payout Transfer",
            description = "Initiates a fund transfer to a registered beneficiary"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "202",
                    description = "Transfer initiated and accepted for processing",
                    content = @Content(schema = @Schema(implementation = CashfreeTransferResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid transfer request or insufficient funds"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Beneficiary not found"
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "Transfer validation failed"
            )
    })
    @PostMapping("/initiate/transfer")
    public ResponseEntity<CashfreeTransferResponse> initiateTransfer(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Transfer request with amount, beneficiary, and reference details",
                    required = true
            )
            @Valid @RequestBody CashfreeTransferRequest request) {

        log.info("Initiating transfer: {}", request.getTransferId());

        CashfreeTransferResponse response = payoutService.processPayout(request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @Operation(
            summary = "Get Transfer Status",
            description = "Retrieves the current status of a payout transfer"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Transfer status retrieved successfully",
                    content = @Content(schema = @Schema(implementation = TransferStatusResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Missing transfer identifier"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Transfer not found"
            )
    })
    @GetMapping("/get/{cfTransferId}/status")
    public ResponseEntity<TransferStatusResponseDTO> getTransferStatus(
            @Parameter(
                    description = "Cashfree transfer reference ID",
                    example = "CF_TRANS_123456789",
                    required = false
            )
            @RequestParam(required = false) String cfTransferId) {

        TransferStatusResponseDTO response;

        if (cfTransferId != null) {
            log.info("Getting status by reference: {}", cfTransferId);
            response = payoutService.checkPayoutStatus(cfTransferId);
        } else {
            throw new IllegalArgumentException("Provide referenceId or cfTransferId");
        }

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Payout Service Health Check",
            description = "Verifies the availability and operational status of payout services"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Service is operational",
                    content = @Content(schema = @Schema(implementation = Map.class))
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Service is unavailable"
            )
    })
    @GetMapping("/health/check")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Cashfree Payout HTTP API");
        response.put("method", "Direct HTTP Calls (No SDK)");
        return ResponseEntity.ok(response);
    }

    private boolean isSuccessStatus(String status) {
        return "SUCCESS".equalsIgnoreCase(status) ||
                "SUCCESSFUL".equalsIgnoreCase(status);
    }
}