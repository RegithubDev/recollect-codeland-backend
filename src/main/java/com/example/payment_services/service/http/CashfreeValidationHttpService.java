package com.example.payment_services.service.http;

import com.example.payment_services.config.CashfreeConfig;
import com.example.payment_services.dto.verification.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class CashfreeValidationHttpService {

    private final RestTemplate restTemplate;
    private final CashfreeConfig cashfreeConfig;

    // ========== BANK ACCOUNT VERIFICATION (Sync) ==========
    public BankAccountVerificationResponseDTO verifyBankAccountSync(BankAccountVerificationRequestDTO request) {
        try {
            String url = cashfreeConfig.getVerificationBaseUrl() + "/bank-account/sync";

            log.info("Verifying bank account: {} @ {}", request.getBankAccount(), request.getIfsc());

            HttpHeaders headers = createVerificationHeaders();
            HttpEntity<BankAccountVerificationRequestDTO> entity = new HttpEntity<>(request, headers);

            ResponseEntity<BankAccountVerificationResponseDTO> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, BankAccountVerificationResponseDTO.class);

            log.info("Bank account verification response status: {}", response.getStatusCode());
            return response.getBody();

        } catch (Exception e) {
            log.error("Bank account verification error", e);
            throw new RuntimeException("Bank account verification failed: " + e.getMessage());
        }
    }

    // ========== GET BANK ACCOUNT VERIFICATION STATUS ==========
    public BankAccountVerificationResponseDTO getBankAccountVerification(Long referenceId) {
        try {
            String url = cashfreeConfig.getVerificationBaseUrl() + "/bank-account";

            log.info("Getting bank account verification status: {}", referenceId);

            HttpHeaders headers = createVerificationHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<BankAccountVerificationResponseDTO> response = restTemplate.exchange(
                    url + "?reference_id=" + referenceId,
                    HttpMethod.GET, entity, BankAccountVerificationResponseDTO.class);

            log.info("Get bank account verification response status: {}", response.getStatusCode());
            return response.getBody();

        } catch (Exception e) {
            log.error("Get bank account verification error", e);
            throw new RuntimeException("Get bank account verification failed: " + e.getMessage());
        }
    }

    // ========== PAN VERIFICATION ==========
    public PanVerificationResponseDTO verifyPan(PanVerificationRequestDTO request) {
        try {
            String url = cashfreeConfig.getVerificationBaseUrl() + "/pan";

            log.info("Verifying PAN: {}", request.getPan());

            HttpHeaders headers = createVerificationHeaders();
            HttpEntity<PanVerificationRequestDTO> entity = new HttpEntity<>(request, headers);

            ResponseEntity<PanVerificationResponseDTO> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, PanVerificationResponseDTO.class);

            log.info("PAN verification response status: {}", response.getStatusCode());
            return response.getBody();

        } catch (Exception e) {
            log.error("PAN verification error", e);
            throw new RuntimeException("PAN verification failed: " + e.getMessage());
        }
    }

    // ========== GET PAN VERIFICATION STATUS ==========
    public PanVerificationResponseDTO getPanVerification(Long referenceId) {
        try {
            String url = cashfreeConfig.getVerificationBaseUrl() + "/pan/" + referenceId;

            log.info("Getting PAN verification status: {}", referenceId);

            HttpHeaders headers = createVerificationHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<PanVerificationResponseDTO> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, PanVerificationResponseDTO.class);

            log.info("Get PAN verification response status: {}", response.getStatusCode());
            return response.getBody();

        } catch (Exception e) {
            log.error("Get PAN verification error", e);
            throw new RuntimeException("Get PAN verification failed: " + e.getMessage());
        }
    }

    // ========== HEADERS FOR VERIFICATION API ==========
    private HttpHeaders createVerificationHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-client-id", cashfreeConfig.getClientId());
        headers.set("x-client-secret", cashfreeConfig.getClientSecret());
        return headers;
    }
}