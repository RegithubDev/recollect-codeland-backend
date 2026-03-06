package com.example.payment_services.service.http;

import com.example.payment_services.config.CashfreeConfig;
import com.example.payment_services.dto.verification.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CashfreeValidationHttpService {

    private final RestTemplate restTemplate;
    private final CashfreeConfig cashfreeConfig;
    private final ObjectMapper objectMapper;

    // ========== BANK ACCOUNT VERIFICATION (Sync) ==========
    public BankAccountVerificationResponseDTO verifyBankAccountSync(BankAccountVerificationRequestDTO request) {
        try {
            String url = cashfreeConfig.getVerificationBaseUrl() + "/bank-account/sync";

            log.info("Verifying bank account: {} @ {}", request.getBankAccount(), request.getIfsc());

            HttpHeaders headers = createVerificationHeaders();
            HttpEntity<BankAccountVerificationRequestDTO> entity = new HttpEntity<>(request, headers);

            ResponseEntity<BankAccountVerificationResponseDTO> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, BankAccountVerificationResponseDTO.class);

            log.info("Bank account verification successful for reference ID: {}",
                    response.getBody() != null ? response.getBody().getReferenceId() : null);

            return response.getBody();

        } catch (HttpClientErrorException e) {
            log.error("Bank account verification failed with status: {}, response: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());

            return handleBankAccountError(e);

        } catch (Exception e) {
            log.error("Bank account verification error", e);
            throw new RuntimeException("Bank account verification failed: " + e.getMessage());
        }
    }

    private BankAccountVerificationResponseDTO handleBankAccountError(HttpClientErrorException e) {
        try {
            BankAccountVerificationResponseDTO errorResponse = new BankAccountVerificationResponseDTO();
            errorResponse.setAccountStatus("FAILED");
            errorResponse.setAccountStatusCode("VERIFICATION_FAILED");

            Map<String, Object> errorMap = objectMapper.readValue(
                    e.getResponseBodyAsString(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}
            );

            errorResponse.setType((String) errorMap.get("type"));
            errorResponse.setCode((String) errorMap.get("code"));
            errorResponse.setMessage((String) errorMap.get("message"));

            if (errorMap.containsKey("error")) {
                errorResponse.setError((Map<String, Object>) errorMap.get("error"));
            }

            return errorResponse;

        } catch (Exception ex) {
            log.error("Failed to parse error response", ex);
            BankAccountVerificationResponseDTO errorResponse = new BankAccountVerificationResponseDTO();
            errorResponse.setAccountStatus("FAILED");
            errorResponse.setMessage(e.getMessage());
            return errorResponse;
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

            return response.getBody();

        } catch (HttpClientErrorException e) {
            log.error("Get bank account verification failed: {}", e.getResponseBodyAsString());
            return handleBankAccountError(e);
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

            return response.getBody();

        } catch (HttpClientErrorException e) {
            log.error("PAN verification failed: {}", e.getResponseBodyAsString());
            return handlePanError(e);
        } catch (Exception e) {
            log.error("PAN verification error", e);
            throw new RuntimeException("PAN verification failed: " + e.getMessage());
        }
    }

    private PanVerificationResponseDTO handlePanError(HttpClientErrorException e) {
        try {
            PanVerificationResponseDTO errorResponse = new PanVerificationResponseDTO();
            errorResponse.setValid(false);

            Map<String, Object> errorMap = objectMapper.readValue(
                    e.getResponseBodyAsString(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}
            );

            errorResponse.setMessage((String) errorMap.get("message"));
            return errorResponse;

        } catch (Exception ex) {
            PanVerificationResponseDTO errorResponse = new PanVerificationResponseDTO();
            errorResponse.setValid(false);
            errorResponse.setMessage(e.getMessage());
            return errorResponse;
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

            return response.getBody();

        } catch (HttpClientErrorException e) {
            log.error("Get PAN verification failed: {}", e.getResponseBodyAsString());
            return handlePanError(e);
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
        headers.set("x-api-version", "2023-12-01");
        return headers;
    }
}