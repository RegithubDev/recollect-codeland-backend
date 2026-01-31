package com.example.payment_services.service;

import com.example.payment_services.dto.verification.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationService {

    private final CashfreeValidationHttpService cashfreeValidationHttpService;

    // ========== BANK ACCOUNT VALIDATION ==========
    public BankAccountValidationResult validateBankAccount(BankAccountVerificationRequestDTO request) {
        try {
            // Validate input
            validateBankAccountRequest(request);

            // Call Cashfree API
            BankAccountVerificationResponseDTO response =
                    cashfreeValidationHttpService.verifyBankAccountSync(request);

            // Create result with business logic
            BankAccountValidationResult result = new BankAccountValidationResult();
            result.setVerificationResponse(response);
            result.setValid("VALID".equals(response.getAccountStatus()));
            result.setNameMatchGood(isGoodNameMatch(response.getNameMatchResult()));
            result.setBankDetailsAvailable(response.getBankName() != null);

            log.info("Bank account validation result: valid={}, nameMatch={}",
                    result.isValid(), result.isNameMatchGood());

            return result;

        } catch (Exception e) {
            log.error("Bank account validation error", e);
            throw new RuntimeException("Bank account validation failed: " + e.getMessage());
        }
    }

    public BankAccountVerificationResponseDTO getBankAccountVerificationStatus(Long referenceId) {
        try {
            return cashfreeValidationHttpService.getBankAccountVerification(referenceId);
        } catch (Exception e) {
            log.error("Get bank account verification error", e);
            throw new RuntimeException("Get bank account verification failed: " + e.getMessage());
        }
    }

    // ========== PAN VALIDATION ==========
    public PanValidationResult validatePan(PanVerificationRequestDTO request) {
        try {
            // Validate input
            validatePanRequest(request);

            // Call Cashfree API
            PanVerificationResponseDTO response = cashfreeValidationHttpService.verifyPan(request);

            // Create result with business logic
            PanValidationResult result = new PanValidationResult();
            result.setVerificationResponse(response);
            result.setValid(response.getValid() != null && response.getValid());
            result.setNameMatchGood(isGoodPanNameMatch(response.getNameMatchResult()));
            result.setAadhaarLinked("Y".equals(response.getAadhaarSeedingStatus()));

            log.info("PAN validation result: valid={}, nameMatch={}",
                    result.isValid(), result.isNameMatchGood());

            return result;

        } catch (Exception e) {
            log.error("PAN validation error", e);
            throw new RuntimeException("PAN validation failed: " + e.getMessage());
        }
    }

    public PanVerificationResponseDTO getPanVerificationStatus(Long referenceId) {
        try {
            return cashfreeValidationHttpService.getPanVerification(referenceId);
        } catch (Exception e) {
            log.error("Get PAN verification error", e);
            throw new RuntimeException("Get PAN verification failed: " + e.getMessage());
        }
    }

    // ========== VALIDATION METHODS ==========
    private void validateBankAccountRequest(BankAccountVerificationRequestDTO request) {
        if (request.getBankAccount() == null || request.getBankAccount().trim().isEmpty()) {
            throw new IllegalArgumentException("Bank account number is required");
        }

        if (request.getIfsc() == null || request.getIfsc().trim().isEmpty()) {
            throw new IllegalArgumentException("IFSC code is required");
        }

        if (request.getBankAccount().length() < 9 || request.getBankAccount().length() > 18) {
            throw new IllegalArgumentException("Bank account number must be 9-18 digits");
        }

        if (request.getIfsc().length() != 11) {
            throw new IllegalArgumentException("IFSC must be 11 characters");
        }
    }

    private void validatePanRequest(PanVerificationRequestDTO request) {
        if (request.getPan() == null || request.getPan().trim().isEmpty()) {
            throw new IllegalArgumentException("PAN is required");
        }

        if (!request.getPan().matches("[A-Z]{5}[0-9]{4}[A-Z]{1}")) {
            throw new IllegalArgumentException("Invalid PAN format");
        }

        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Name is required for PAN verification");
        }
    }

    private boolean isGoodNameMatch(String matchResult) {
        if (matchResult == null) return false;

        return matchResult.contains("DIRECT_MATCH") ||
                matchResult.contains("GOOD_PARTIAL_MATCH");
    }

    private boolean isGoodPanNameMatch(String matchResult) {
        if (matchResult == null) return false;

        return matchResult.contains("DIRECT_MATCH") ||
                matchResult.contains("PARTIAL_MATCH");
    }

    // ========== RESULT CLASSES ==========
    @Data
    public static class BankAccountValidationResult {
        private BankAccountVerificationResponseDTO verificationResponse;
        private boolean valid;
        private boolean nameMatchGood;
        private boolean bankDetailsAvailable;
        private String message;
    }

    @Data
    public static class PanValidationResult {
        private PanVerificationResponseDTO verificationResponse;
        private boolean valid;
        private boolean nameMatchGood;
        private boolean aadhaarLinked;
        private String message;
    }
}