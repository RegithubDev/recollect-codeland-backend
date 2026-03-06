package com.example.payment_services.service.http;

import com.example.payment_services.config.CashfreeConfig;
import com.example.payment_services.dto.payout.*;
import com.example.payment_services.entity.PayoutTransaction;
import com.example.payment_services.repository.PayoutTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static com.example.payment_services.util.SecurityUtil.getCurrentUserId;

@Service
@RequiredArgsConstructor
@Slf4j
public class CashfreePayoutHttpService {

    private final RestTemplate restTemplate;
    private final CashfreeConfig cashfreeConfig;
    private final PayoutTransactionRepository payoutTransactionRepository;

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

    // ========== DELETE BENEFICIARY ==========
    public CashfreeBeneficiaryDeleteResponse deleteBeneficiary(String beneficiaryId) {
        try {
            String url = cashfreeConfig.getPayoutBaseUrl() + "/beneficiary?beneficiary_id=" + beneficiaryId;

            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            log.info("Deleting beneficiary with ID: {}", beneficiaryId);

            ResponseEntity<CashfreeBeneficiaryDeleteResponse> response = restTemplate.exchange(
                    url, HttpMethod.DELETE, entity, CashfreeBeneficiaryDeleteResponse.class);

            log.info("Delete beneficiary response status: {}", response.getStatusCode());

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            } else {
                log.error("Delete beneficiary failed. Status: {}", response.getStatusCode());
                throw new RuntimeException("Failed to delete beneficiary. Status: " + response.getStatusCode());
            }

        } catch (HttpClientErrorException e) {
            log.error("HTTP error deleting beneficiary: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            log.error("Delete beneficiary error", e);
            throw new RuntimeException("Beneficiary deletion failed: " + e.getMessage());
        }
    }

    // ========== STANDARD TRANSFER V2 WITH DATABASE UPDATE ==========
    @Transactional
    public CashfreeTransferResponse initiateTransfer(CashfreeTransferRequest request) {
        try {
            String url = cashfreeConfig.getPayoutBaseUrl() + "/transfers";

            log.info("Initiate transfer request: {}", request);

            // 1. Create initial transaction record in database (PENDING state)
            PayoutTransaction transaction = createInitialTransaction(request);

            HttpHeaders headers = createHeaders();
            HttpEntity<CashfreeTransferRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<CashfreeTransferResponse> responseEntity = restTemplate.exchange(
                    url, HttpMethod.POST, entity, CashfreeTransferResponse.class);

            log.info("Initiate transfer response status: {}", responseEntity.getStatusCode());
            CashfreeTransferResponse response = responseEntity.getBody();

            // 2. Update transaction with response from Cashfree
            updateTransactionWithResponse(transaction, response, responseEntity.getStatusCode());

            // 3. Save updated transaction
            payoutTransactionRepository.save(transaction);

            return response;

        } catch (HttpClientErrorException e) {
            // Handle API errors (4xx)
            log.error("HTTP error initiating transfer: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());

            // Update transaction with error status if we have a transaction ID
            if (request.getTransferId() != null) {
                updateTransactionWithError(request.getTransferId(), e);
            }

            throw e;

        } catch (Exception e) {
            log.error("Initiate transfer error", e);

            // Update transaction with error status if we have a transaction ID
            if (request.getTransferId() != null) {
                updateTransactionWithError(request.getTransferId(), e);
            }

            throw new RuntimeException("Transfer failed: " + e.getMessage());
        }
    }

    /**
     * Create initial transaction record before API call
     */
    private PayoutTransaction createInitialTransaction(CashfreeTransferRequest request) {
        PayoutTransaction transaction = new PayoutTransaction();

        transaction.setTransferId(request.getTransferId());
        transaction.setCustomerId(request.getCustomerId());
        transaction.setTransferAmount(request.getTransferAmount());
        transaction.setCurrency(request.getTransferCurrency() != null ? request.getTransferCurrency() : "INR");
        transaction.setTransferMode(request.getTransferMode());
        transaction.setPurpose(request.getRemarks());
        transaction.setReferenceId(request.getTransferId()); // Use transferId as reference initially

        // Store beneficiary details as JSON
        if (request.getBeneficiaryDetails() != null && request.getBeneficiaryDetails().getBeneficiaryId() != null) {
            String beneficiaryJson = String.format(
                    "{\"beneficiaryId\": \"%s\"}",
                    request.getBeneficiaryDetails().getBeneficiaryId()
            );
            transaction.setBeneficiaryDetails(beneficiaryJson);
        }

        // Set initial status
        transaction.setStatus("PENDING");
        transaction.setStatusCode("INITIATED");
        transaction.setStatusDescription("Transfer initiated, waiting for response");

        // Set audit fields
        transaction.setCreatedUid(getCurrentUserId());
        transaction.setUpdatedUid(getCurrentUserId());
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setUpdatedAt(LocalDateTime.now());

        // Save initial record
        PayoutTransaction saved = payoutTransactionRepository.save(transaction);
        log.info("Created initial payout transaction record with ID: {}", saved.getId());

        return saved;
    }

    /**
     * Update transaction with successful response
     */
    private void updateTransactionWithResponse(PayoutTransaction transaction,
                                               CashfreeTransferResponse response,
                                               HttpStatusCode statusCode) {

        if (response != null) {
            transaction.setCfTransferId(response.getCfTransferId());
            transaction.setReferenceId(response.getReferenceId() != null ?
                    response.getReferenceId() : transaction.getReferenceId());

            // Map status from response
            String cashfreeStatus = response.getStatus() != null ? response.getStatus() : "";
            transaction.setStatus(mapCashfreeStatus(cashfreeStatus));
            transaction.setStatusCode(cashfreeStatus);
            transaction.setStatusDescription(getStatusDescription(cashfreeStatus));

            // Set fees and tax if available
            if (response.getFees() != null) {
                transaction.setFees(response.getFees());
            }

            if (response.getTax() != null) {
                transaction.setTax(response.getTax());
            }

            // Store additional metadata
            String metadata = String.format(
                    "{\"utr\": \"%s\", \"transferMode\": \"%s\", \"statusCode\": \"%s\"}",
                    response.getUtr() != null ? response.getUtr() : "",
                    response.getTransferMode() != null ? response.getTransferMode() : "",
                    response.getStatusCode() != null ? response.getStatusCode() : ""
            );
            transaction.setMetadata(metadata);
        }

        transaction.setUpdatedAt(LocalDateTime.now());

        log.info("Updated transaction {} with status: {}",
                transaction.getTransferId(), transaction.getStatus());
    }

    /**
     * Update transaction with error
     */
    private void updateTransactionWithError(String transferId, Exception e) {
        try {
            Optional<PayoutTransaction> existing = payoutTransactionRepository.findByTransferId(transferId);

            if (existing.isPresent()) {
                PayoutTransaction transaction = existing.get();
                transaction.setStatus("FAILED");
                transaction.setStatusCode("ERROR");
                transaction.setStatusDescription("Transfer failed: " + e.getMessage());
                transaction.setUpdatedAt(LocalDateTime.now());

                payoutTransactionRepository.save(transaction);
                log.info("Updated transaction {} with error status", transferId);
            }
        } catch (Exception ex) {
            log.error("Failed to update transaction with error: {}", transferId, ex);
        }
    }

    /**
     * Update transaction with error using HTTP client error
     */
    private void updateTransactionWithError(String transferId, HttpClientErrorException e) {
        try {
            Optional<PayoutTransaction> existing = payoutTransactionRepository.findByTransferId(transferId);

            if (existing.isPresent()) {
                PayoutTransaction transaction = existing.get();
                transaction.setStatus("FAILED");
                transaction.setStatusCode(e.getStatusCode().toString());
                transaction.setStatusDescription("API Error: " + e.getResponseBodyAsString());
                transaction.setUpdatedAt(LocalDateTime.now());

                // Store error details in metadata
                String metadata = String.format(
                        "{\"error\": \"%s\", \"statusCode\": \"%s\", \"response\": %s}",
                        e.getMessage(),
                        e.getStatusCode(),
                        e.getResponseBodyAsString()
                );
                transaction.setMetadata(metadata);

                payoutTransactionRepository.save(transaction);
                log.info("Updated transaction {} with API error status", transferId);
            }
        } catch (Exception ex) {
            log.error("Failed to update transaction with error: {}", transferId, ex);
        }
    }

    /**
     * Map Cashfree status to internal status
     */
    private String mapCashfreeStatus(String cashfreeStatus) {
        if (cashfreeStatus == null) return "PENDING";

        switch (cashfreeStatus.toUpperCase()) {
            case "SUCCESS":
            case "SUCCESSFUL":
                return "SUCCESS";
            case "PENDING":
                return "PROCESSING";
            case "FAILED":
            case "FAILURE":
                return "FAILED";
            case "REVERSED":
                return "REVERSED";
            case "REJECTED":
                return "REJECTED";
            default:
                return "PROCESSING";
        }
    }

    /**
     * Get human-readable status description
     */
    private String getStatusDescription(String status) {
        if (status == null) return "Unknown status";

        switch (status.toUpperCase()) {
            case "SUCCESS":
                return "Transfer completed successfully";
            case "PENDING":
                return "Transfer is being processed";
            case "FAILED":
                return "Transfer failed";
            case "REVERSED":
                return "Transfer was reversed";
            case "REJECTED":
                return "Transfer was rejected";
            default:
                return "Transfer status: " + status;
        }
    }

    public TransferStatusResponseDTO getTransferStatusByTransferId(String cfTransferId) {
        try {
            String url = cashfreeConfig.getPayoutBaseUrl() + "/transfers?cf_transfer_id=" + cfTransferId;

            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            log.info("Getting transfer status for transfer ID: {}", cfTransferId);

            ResponseEntity<TransferStatusResponseDTO> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, TransferStatusResponseDTO.class);

            log.info("Get transfer status response status: {}", response.getStatusCode());

            // Update local transaction with latest status
            updateTransactionFromStatusCheck(cfTransferId, response.getBody());

            return response.getBody();

        } catch (Exception e) {
            log.error("Get transfer status error", e);
            throw new RuntimeException("Status check failed: " + e.getMessage());
        }
    }

    /**
     * Update transaction from status check response
     */
    private void updateTransactionFromStatusCheck(String cfTransferId, TransferStatusResponseDTO statusResponse) {
        try {
            Optional<PayoutTransaction> existing = payoutTransactionRepository.findByCfTransferId(cfTransferId);

            if (existing.isPresent() && statusResponse != null) {
                PayoutTransaction transaction = existing.get();

                // Update status
                String newStatus = mapCashfreeStatus(statusResponse.getStatus());
                transaction.setStatus(newStatus);
                transaction.setStatusCode(statusResponse.getStatus());
                transaction.setStatusDescription(getStatusDescription(statusResponse.getStatus()));

                // Update UTR if available
                if (statusResponse.getUtr() != null) {
                    transaction.setReferenceId(statusResponse.getUtr());
                }

                // Update fees and tax if available
                if (statusResponse.getFees() != null) {
                    transaction.setFees(statusResponse.getFees());
                }

                if (statusResponse.getTax() != null) {
                    transaction.setTax(statusResponse.getTax());
                }

                transaction.setUpdatedAt(LocalDateTime.now());

                payoutTransactionRepository.save(transaction);
                log.info("Updated transaction {} from status check", cfTransferId);
            }
        } catch (Exception e) {
            log.error("Failed to update transaction from status check: {}", cfTransferId, e);
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