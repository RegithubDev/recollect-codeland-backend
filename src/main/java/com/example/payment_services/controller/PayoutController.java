package com.example.payment_services.controller;
import com.example.payment_services.dto.*;
import com.example.payment_services.service.CashfreePayoutHttpService;
import com.example.payment_services.service.PayoutService;
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
public class PayoutController {

    private final PayoutService payoutService;
    private final CashfreePayoutHttpService cashfreePayoutHttpService;

    // Create Beneficiary
    @PostMapping("/beneficiary")
    public ResponseEntity<Map<String, Object>> createBeneficiary(
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

    // Initiate Transfer
    @PostMapping("/transfer")
    public ResponseEntity<CashfreeTransferResponse> initiateTransfer(
            @Valid @RequestBody CashfreeTransferRequest request) {

        log.info("Initiating transfer: {}", request.getTransferId());

        CashfreeTransferResponse response = payoutService.processPayout(request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    // Get Transfer Status
    @GetMapping("/transfer/status")
    public ResponseEntity<TransferStatusResponseDTO> getTransferStatus(
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

    // Health Check
    @GetMapping("/health")
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