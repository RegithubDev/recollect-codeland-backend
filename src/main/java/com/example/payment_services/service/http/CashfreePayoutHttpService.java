package com.example.payment_services.service.http;

import com.example.payment_services.config.CashfreeConfig;
import com.example.payment_services.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;
import static org.springframework.web.util.UriComponentsBuilder.fromHttpUrl;

@Service
@RequiredArgsConstructor
@Slf4j
public class CashfreePayoutHttpService {

    private final RestTemplate restTemplate;
    private final CashfreeConfig cashfreeConfig;

    // ========== CREATE BENEFICIARY V2 ==========
    public CashfreeBeneficiaryResponse createBeneficiary(CashfreeBeneficiaryRequest request) {
        try {
            String url = cashfreeConfig.getPayoutBaseUrl() + "/beneficiary";
            log.info("Creating beneficiary request: {}", request);

            HttpHeaders headers = createHeaders();
            HttpEntity<CashfreeBeneficiaryRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<CashfreeBeneficiaryResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, CashfreeBeneficiaryResponse.class);

            log.info("Create beneficiary response status: {}", response.getStatusCode());

            if (response.getStatusCode() == HttpStatus.CREATED) {
                return response.getBody();
            } else {
                log.error("Create beneficiary failed. Response: {}", response.getBody());
                throw new RuntimeException("Failed to create beneficiary. Status: " +
                        response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Create beneficiary error", e);
            throw new RuntimeException("Beneficiary creation failed: " + e.getMessage());
        }
    }

    // ========== STANDARD TRANSFER V2 ========== (CORRECTED)
    public CashfreeTransferResponse initiateTransfer(CashfreeTransferRequest request) {
        try {
            String url = cashfreeConfig.getPayoutBaseUrl() + "/transfers";

            log.info("Initiate transfer request: {}", request);

            HttpHeaders headers = createHeaders();
            HttpEntity<CashfreeTransferRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<CashfreeTransferResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, CashfreeTransferResponse.class);

            log.info("Initiate transfer response status: {}", response.getStatusCode());

            if (response.getStatusCode() == HttpStatus.OK ||
                    response.getStatusCode() == HttpStatus.ACCEPTED) {
                return response.getBody();
            } else {
                log.error("Initiate transfer failed. Status: {}", response.getStatusCode());
                throw new RuntimeException("Failed to initiate transfer. Status: " +
                        response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Initiate transfer error", e);
            throw new RuntimeException("Transfer failed: " + e.getMessage());
        }
    }

    public TransferStatusResponseDTO getTransferStatusByTransferId(String cfTransfer_id) {
        try {
            String url = cashfreeConfig.getPayoutBaseUrl() + "/transfers";

            UriComponentsBuilder builder = fromHttpUrl(url)
                    .queryParam("cf_transfer_id", cfTransfer_id);

            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            log.info("Getting transfer status for transfer ID: {}", cfTransfer_id);

            ResponseEntity<TransferStatusResponseDTO> response = restTemplate.exchange(
                    builder.toUriString(), HttpMethod.GET, entity, TransferStatusResponseDTO.class);

            log.info("Get transfer status by ID response status: {}", response.getStatusCode());

            return response.getBody();

        } catch (Exception e) {
            log.error("Get transfer status by ID error", e);
            throw new RuntimeException("Status check failed: " + e.getMessage());
        }
    }

    // ========== HEADER CREATION ==========
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-client-id", cashfreeConfig.getClientId());
        headers.set("x-client-secret", cashfreeConfig.getClientSecret());
        headers.set("x-api-version", "2024-01-01");
        headers.set("x-request-id", generateRequestId());

        log.debug("Created headers with client-id: {}", cashfreeConfig.getClientId());
        return headers;
    }

    private String generateRequestId() {
        return "REQ_" + System.currentTimeMillis() + "_" +
                UUID.randomUUID().toString().substring(0, 8);
    }
}