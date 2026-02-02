package com.example.payment_services.controller;

import com.example.payment_services.dto.verification.*;
import com.example.payment_services.service.ValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/verification")
@RequiredArgsConstructor
@Slf4j
public class ValidationController {

    private final ValidationService validationService;

    // ========== BANK ACCOUNT VERIFICATION ==========

    @PostMapping("/bank-account")
    public ResponseEntity<ValidationService.BankAccountValidationResult> verifyBankAccount(
            @RequestBody BankAccountVerificationRequestDTO request) {

        log.info("Verifying bank account: {}", request.getBankAccount());

        ValidationService.BankAccountValidationResult result =
                validationService.validateBankAccount(request);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/get/bank-account/{referenceId}")
    public ResponseEntity<BankAccountVerificationResponseDTO> getBankAccountVerification(
            @PathVariable Long referenceId) {

        log.info("Getting bank account verification: {}", referenceId);

        BankAccountVerificationResponseDTO response =
                validationService.getBankAccountVerificationStatus(referenceId);

        return ResponseEntity.ok(response);
    }

    // ========== PAN VERIFICATION ==========

    @PostMapping("/pan")
    public ResponseEntity<ValidationService.PanValidationResult> verifyPan(
            @RequestBody PanVerificationRequestDTO request) {

        log.info("Verifying PAN: {}", request.getPan());

        ValidationService.PanValidationResult result =
                validationService.validatePan(request);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/get/pan/{referenceId}")
    public ResponseEntity<PanVerificationResponseDTO> getPanVerification(
            @PathVariable Long referenceId) {

        log.info("Getting PAN verification: {}", referenceId);

        PanVerificationResponseDTO response =
                validationService.getPanVerificationStatus(referenceId);

        return ResponseEntity.ok(response);
    }

    // ========== HEALTH CHECK ==========

    @GetMapping("/health/check")
    public ResponseEntity<?> healthCheck() {
        return ResponseEntity.ok().body(
                new HealthResponse("UP", "Verification API",
                        "Bank Account & PAN Verification")
        );
    }

    private record HealthResponse(String status, String service, String features) {}
}