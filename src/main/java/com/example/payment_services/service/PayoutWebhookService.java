package com.example.payment_services.service;

import com.example.payment_services.config.CashfreeConfig;
import com.example.payment_services.entity.PayoutTransaction;
import com.example.payment_services.repository.PayoutTransactionRepository;
import com.example.payment_services.util.ReslApiUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

import static com.example.payment_services.util.SecurityUtil.getCurrentUserId;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutWebhookService {

    private final PayoutTransactionRepository payoutTransactionRepository;
    private final ObjectMapper objectMapper;
    private final CashfreeConfig cashfreeConfig;
    private final LedgerService ledgerService;
    private final ReslApiUtil reslApiUtil;

    /**
     * Enum for payout status
     */
    public enum PayoutStatus {
        PENDING("pending"),
        PROCESSING("processing"),
        SUCCESS("success"),
        FAILED("failed"),
        REVERSED("reversed"),
        REJECTED("rejected"),
        ACKNOWLEDGED("acknowledged");

        private final String value;

        PayoutStatus(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static PayoutStatus fromString(String status) {
            for (PayoutStatus s : PayoutStatus.values()) {
                if (s.value.equalsIgnoreCase(status)) {
                    return s;
                }
            }
            return PayoutStatus.PENDING;
        }
    }

    /**
     * Verify Cashfree Payout V2 webhook signature
     * For V2 webhooks, the signature is calculated as:
     * HMAC-SHA256(webhook_secret, timestamp + rawBody)
     */
    public boolean verifyPayoutSignature(String rawBody, String signature, String timestamp) {
        try {
            String webhookSecret = cashfreeConfig.getClientSecret();

            // For V2 webhooks, the signature is calculated on timestamp + body
            String dataToSign = timestamp + rawBody;

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey =
                    new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

            mac.init(secretKey);
            byte[] hash = mac.doFinal(dataToSign.getBytes(StandardCharsets.UTF_8));

            String computedSignature = Base64.getEncoder().encodeToString(hash);

            log.info("Computed signature (V2 method): {}", computedSignature);
            log.info("Received signature: {}", signature);
            log.info("Timestamp used: {}", timestamp);

            return constantTimeEquals(computedSignature, signature);

        } catch (Exception e) {
            log.error("Webhook signature verification failed", e);
            return false;
        }
    }

    /**
     * Parse Cashfree Payout V2 webhook payload
     * Updated to handle the correct V2 webhook structure:
     * {
     *   "data": {
     *     "transfer_id": "...",
     *     "cf_transfer_id": "...",
     *     "status": "SUCCESS",
     *     "status_code": "COMPLETED",
     *     "status_description": "...",
     *     "beneficiary_details": {
     *       "beneficiary_id": "...",
     *       "beneficiary_instrument_details": {
     *         "bank_account_number": "...",
     *         "bank_ifsc": "..."
     *       }
     *     },
     *     "transfer_amount": 2,
     *     "transfer_service_charge": 6,
     *     "transfer_service_tax": 1.08,
     *     "transfer_mode": "imps",
     *     "transfer_utr": "...",
     *     "fundsource_id": "CF Wallet",
     *     "added_on": "2026-03-06T18:18:12",
     *     "updated_on": "2026-03-06T18:18:14"
     *   },
     *   "event_time": "2026-03-06T18:18:14",
     *   "type": "TRANSFER_SUCCESS"
     * }
     */
    public Map<String, Object> parsePayoutWebhookPayload(String rawBody) throws Exception {
        Map<String, Object> parsedData = new HashMap<>();

        if (rawBody == null || rawBody.trim().isEmpty()) {
            log.info("Empty payout webhook payload - test request");
            parsedData.put("isTest", true);
            return parsedData;
        }

        Map<String, Object> rootMap = objectMapper.readValue(
                rawBody,
                new TypeReference<Map<String, Object>>() {}
        );

        log.info("Raw webhook data: {}", rootMap);

        // Extract event type from root (V2 webhook)
        if (rootMap.containsKey("type")) {
            String eventType = rootMap.get("type").toString();
            parsedData.put("eventType", eventType);
            log.info("Event type: {}", eventType);
        }

        // Extract event time from root
        if (rootMap.containsKey("event_time")) {
            parsedData.put("eventTime", rootMap.get("event_time").toString());
        }

        // Extract the nested data object (contains all transaction details)
        if (rootMap.containsKey("data")) {
            Map<String, Object> dataMap = (Map<String, Object>) rootMap.get("data");

            // Map V2 field names to your expected field names
            if (dataMap.containsKey("transfer_id")) {
                parsedData.put("transferId", dataMap.get("transfer_id").toString());
            }

            if (dataMap.containsKey("cf_transfer_id")) {
                parsedData.put("referenceId", dataMap.get("cf_transfer_id").toString());
                parsedData.put("cfTransferId", dataMap.get("cf_transfer_id").toString());
            }

            if (dataMap.containsKey("transfer_utr")) {
                parsedData.put("utr", dataMap.get("transfer_utr").toString());
            }

            if (dataMap.containsKey("status")) {
                parsedData.put("status", dataMap.get("status").toString());
            }

            if (dataMap.containsKey("status_code")) {
                parsedData.put("statusCode", dataMap.get("status_code").toString());
            }

            if (dataMap.containsKey("status_description")) {
                parsedData.put("statusDescription", dataMap.get("status_description").toString());
            }

            if (dataMap.containsKey("transfer_amount")) {
                try {
                    parsedData.put("amount", new BigDecimal(dataMap.get("transfer_amount").toString()));
                    parsedData.put("transferAmount", new BigDecimal(dataMap.get("transfer_amount").toString()));
                } catch (Exception e) {
                    log.warn("Could not parse transfer_amount");
                }
            }

            if (dataMap.containsKey("transfer_service_charge")) {
                try {
                    parsedData.put("fees", new BigDecimal(dataMap.get("transfer_service_charge").toString()));
                } catch (Exception e) {
                    log.warn("Could not parse transfer_service_charge");
                }
            }

            if (dataMap.containsKey("transfer_service_tax")) {
                try {
                    parsedData.put("tax", new BigDecimal(dataMap.get("transfer_service_tax").toString()));
                } catch (Exception e) {
                    log.warn("Could not parse transfer_service_tax");
                }
            }

            if (dataMap.containsKey("transfer_mode")) {
                parsedData.put("transferMode", dataMap.get("transfer_mode").toString());
            }

            if (dataMap.containsKey("fundsource_id")) {
                parsedData.put("fundSource", dataMap.get("fundsource_id").toString());
            }

            if (dataMap.containsKey("added_on")) {
                parsedData.put("addedOn", dataMap.get("added_on").toString());
            }

            if (dataMap.containsKey("updated_on")) {
                parsedData.put("updatedOn", dataMap.get("updated_on").toString());
            }

            // Extract beneficiary details
            if (dataMap.containsKey("beneficiary_details")) {
                Map<String, Object> beneficiaryMap = (Map<String, Object>) dataMap.get("beneficiary_details");

                if (beneficiaryMap.containsKey("beneficiary_id")) {
                    parsedData.put("beneficiaryId", beneficiaryMap.get("beneficiary_id").toString());
                }

                // Extract beneficiary instrument details
                if (beneficiaryMap.containsKey("beneficiary_instrument_details")) {
                    Map<String, Object> instrumentMap = (Map<String, Object>) beneficiaryMap.get("beneficiary_instrument_details");

                    if (instrumentMap.containsKey("bank_account_number")) {
                        parsedData.put("bankAccountNumber", instrumentMap.get("bank_account_number").toString());
                    }

                    if (instrumentMap.containsKey("bank_ifsc")) {
                        parsedData.put("bankIfsc", instrumentMap.get("bank_ifsc").toString());
                    }
                }
            }

            // Handle failure reason if present
            if (dataMap.containsKey("failure_reason")) {
                parsedData.put("failureReason", dataMap.get("failure_reason").toString());
            } else if (dataMap.containsKey("reason")) {
                parsedData.put("failureReason", dataMap.get("reason").toString());
            }
        }

        // Store raw payload
        parsedData.put("rawPayload", rawBody);

        log.info("Parsed payout webhook data: {}", parsedData);
        return parsedData;
    }

    /**
     * Process payout webhook and update transaction
     */
    @Transactional
    public void processPayoutWebhook(Map<String, Object> webhookData) {
        log.info("========== PROCESSING PAYOUT WEBHOOK ==========");

        // Check if this is a test webhook
        if (webhookData.containsKey("isTest") && (Boolean) webhookData.get("isTest")) {
            log.info("Test payout webhook received - skipping processing");
            return;
        }

        String eventType = (String) webhookData.get("eventType");
        String transferId = (String) webhookData.get("transferId");
        String referenceId = (String) webhookData.get("referenceId");
        String cfTransferId = (String) webhookData.get("cfTransferId");
        String status = (String) webhookData.get("status");
        String utr = (String) webhookData.get("utr");
        String failureReason = (String) webhookData.get("failureReason");
        BigDecimal amount = (BigDecimal) webhookData.get("amount");
        BigDecimal fees = (BigDecimal) webhookData.get("fees");
        BigDecimal tax = (BigDecimal) webhookData.get("tax");

        log.info("Event Type: {}", eventType);
        log.info("Transfer ID: {}", transferId);
        log.info("Reference ID: {}", referenceId);
        log.info("CF Transfer ID: {}", cfTransferId);
        log.info("Status: {}", status);
        log.info("UTR: {}", utr);
        log.info("Amount: {}", amount);
        log.info("Fees: {}", fees);
        log.info("Tax: {}", tax);

        // Find payout transaction by various identifiers
        PayoutTransaction transaction = null;

        // Try by transferId (merchant's ID)
        if (transferId != null && !transferId.isEmpty()) {
            log.info("Looking for transaction by transferId: {}", transferId);
            Optional<PayoutTransaction> byTransferId = payoutTransactionRepository
                    .findByTransferId(transferId);
            if (byTransferId.isPresent()) {
                transaction = byTransferId.get();
                log.info("Found transaction by transferId");
            }
        }

        // Try by referenceId (Cashfree's ID)
        if (transaction == null && referenceId != null && !referenceId.isEmpty()) {
            log.info("Looking for transaction by referenceId: {}", referenceId);
            Optional<PayoutTransaction> byReferenceId = payoutTransactionRepository
                    .findByReferenceId(referenceId);
            if (byReferenceId.isPresent()) {
                transaction = byReferenceId.get();
                log.info("Found transaction by referenceId");
                // Set cfTransferId if not already set
                if (transaction.getCfTransferId() == null) {
                    transaction.setCfTransferId(referenceId);
                }
            }
        }

        // Try by cfTransferId
        if (transaction == null && cfTransferId != null && !cfTransferId.isEmpty()) {
            log.info("Looking for transaction by cfTransferId: {}", cfTransferId);
            Optional<PayoutTransaction> byCfTransferId = payoutTransactionRepository
                    .findByCfTransferId(cfTransferId);
            if (byCfTransferId.isPresent()) {
                transaction = byCfTransferId.get();
                log.info("Found transaction by cfTransferId");
            }
        }

        // Try by UTR if other methods failed
        if (transaction == null && utr != null && !utr.isEmpty()) {
            log.info("Looking for transaction by UTR: {}", utr);
            Optional<PayoutTransaction> byUtr = payoutTransactionRepository
                    .findByReferenceId(utr); // Assuming UTR is stored in referenceId
            if (byUtr.isPresent()) {
                transaction = byUtr.get();
                log.info("Found transaction by UTR");
            }
        }

        if (transaction == null) {
            log.error("Payout transaction not found for any identifier");
            log.error("Searched by - transferId: {}, referenceId: {}, cfTransferId: {}, utr: {}",
                    transferId, referenceId, cfTransferId, utr);
            return;
        }

        log.info("Found transaction - ID: {}, Current Status: {}",
                transaction.getId(), transaction.getStatus());

        // Update transaction
        updatePayoutTransaction(transaction, webhookData);

        // Save the updated transaction
        PayoutTransaction saved = payoutTransactionRepository.save(transaction);
        log.info("Transaction saved with ID: {}, New Status: {}", saved.getId(), saved.getStatus());

        // Trigger post-processing actions
        triggerPostPayoutActions(transaction, eventType);
        //calling resl api
        reslApiUtil.updatePayoutStatus(transaction.getTransferId(),transaction.getStatus(), transaction.getCfTransferId(),
                transaction.getTransferAmount(), transaction.getStatusDescription());

        log.info("========== PAYOUT WEBHOOK PROCESSING COMPLETE ==========");
    }

    /**
     * Update payout transaction with webhook data
     */
    private void updatePayoutTransaction(PayoutTransaction transaction,
                                         Map<String, Object> webhookData) {

        String eventType = (String) webhookData.get("eventType");
        String status = (String) webhookData.get("status");
        String referenceId = (String) webhookData.get("referenceId");
        String cfTransferId = (String) webhookData.get("cfTransferId");
        String utr = (String) webhookData.get("utr");
        String failureReason = (String) webhookData.get("failureReason");
        String statusDescription = (String) webhookData.get("statusDescription");
        BigDecimal amount = (BigDecimal) webhookData.get("amount");
        BigDecimal fees = (BigDecimal) webhookData.get("fees");
        BigDecimal tax = (BigDecimal) webhookData.get("tax");

        log.info("Updating transaction {} with event: {}",
                transaction.getTransferId(), eventType);

        // Update basic fields
        transaction.setUpdatedAt(LocalDateTime.now());

        // Set CF Transfer ID if available and not already set
        if (cfTransferId != null && !cfTransferId.isEmpty() && transaction.getCfTransferId() == null) {
            transaction.setCfTransferId(cfTransferId);
        }

        // Set reference ID if available
        if (referenceId != null && !referenceId.isEmpty() && transaction.getReferenceId() == null) {
            transaction.setReferenceId(referenceId);
        }

        // Update amount if not set
        if (amount != null && transaction.getTransferAmount() == null) {
            transaction.setTransferAmount(amount);
        }

        // Update fees and tax
        if (fees != null) {
            transaction.setFees(fees);
        }

        if (tax != null) {
            transaction.setTax(tax);
        }

        // Map status
        String entityStatus = mapToEntityStatus(eventType, status);
        transaction.setStatus(entityStatus);

        // Set status description
        if (statusDescription != null) {
            transaction.setStatusDescription(statusDescription);
        } else {
            transaction.setStatusDescription(getStatusDescription(eventType, status, failureReason));
        }

        // Set UTR if available
//        if (utr != null && !utr.isEmpty()) {
//            transaction.se(utr);
//        }

        // Build metadata
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("eventType", eventType);
            metadata.put("processedAt", LocalDateTime.now().toString());
            metadata.put("utr", utr);
            metadata.put("webhookData", webhookData);

            // Merge with existing metadata if any
            if (transaction.getMetadata() != null) {
                try {
                    Map<String, Object> existingMetadata = objectMapper.readValue(
                            transaction.getMetadata(),
                            new TypeReference<Map<String, Object>>() {}
                    );
                    existingMetadata.putAll(metadata);
                    transaction.setMetadata(objectMapper.writeValueAsString(existingMetadata));
                } catch (Exception e) {
                    transaction.setMetadata(objectMapper.writeValueAsString(metadata));
                }
            } else {
                transaction.setMetadata(objectMapper.writeValueAsString(metadata));
            }
        } catch (Exception e) {
            log.warn("Could not serialize metadata", e);
        }
    }

    /**
     * Map Cashfree event to entity status
     */
    private String mapToEntityStatus(String eventType, String status) {
        if (eventType == null) {
            return "pending";
        }

        switch (eventType) {
            case "TRANSFER_SUCCESS":
                return "success";
            case "TRANSFER_FAILED":
                return "failed";
            case "TRANSFER_REVERSED":
                return "reversed";
            case "TRANSFER_ACKNOWLEDGED":
                return "acknowledged";
            case "TRANSFER_REJECTED":
                return "rejected";
            case "CREDIT_CONFIRMATION":
                return "success";
            case "LOW_BALANCE_ALERT":
                return "low_balance";
            default:
                // If event type doesn't match, try to use the status field
                if (status != null) {
                    switch (status.toUpperCase()) {
                        case "SUCCESS":
                        case "COMPLETED":
                            return "success";
                        case "FAILED":
                            return "failed";
                        case "REVERSED":
                            return "reversed";
                        case "REJECTED":
                            return "rejected";
                        case "PENDING":
                            return "pending";
                        case "PROCESSING":
                            return "processing";
                        default:
                            return status.toLowerCase();
                    }
                }
                return "pending";
        }
    }

    /**
     * Get human-readable status description
     */
    private String getStatusDescription(String eventType, String status, String failureReason) {
        if (eventType == null) {
            if (status != null) {
                return "Transfer " + status.toLowerCase();
            }
            return "Unknown status";
        }

        switch (eventType) {
            case "TRANSFER_SUCCESS":
                return "Transfer completed successfully";
            case "TRANSFER_FAILED":
                return "Transfer failed: " + (failureReason != null ? failureReason : "Unknown reason");
            case "TRANSFER_REVERSED":
                return "Transfer reversed: " + (failureReason != null ? failureReason : "Unknown reason");
            case "TRANSFER_ACKNOWLEDGED":
                return "Bank acknowledged the transfer";
            case "TRANSFER_REJECTED":
                return "Transfer rejected: " + (failureReason != null ? failureReason : "Unknown reason");
            case "CREDIT_CONFIRMATION":
                return "Credit confirmed";
            case "LOW_BALANCE_ALERT":
                return "Low balance alert";
            default:
                return status != null ? status : "Unknown";
        }
    }

    /**
     * Trigger actions after payout processing
     */
    private void triggerPostPayoutActions(PayoutTransaction transaction, String eventType) {
        try {
            switch (eventType) {
                case "TRANSFER_SUCCESS":
                case "TRANSFER_ACKNOWLEDGED":
                    ledgerService.recordWithdrawalProcessedSuccess(transaction.getCfTransferId(), transaction.getTransferId(),
                            transaction.getCustomerId(), transaction.getReferenceId(), transaction.getTransferAmount(), getCurrentUserId());
                    log.info("Wallet Payout Successful added to ledger: {}", eventType);
                    break;
                case "TRANSFER_FAILED":
                case "TRANSFER_REJECTED":
                case "TRANSFER_REVERSED":
                    ledgerService.recordWithdrawalFailed(transaction.getCfTransferId(), transaction.getTransferId(),
                            transaction.getCustomerId(), transaction.getReferenceId(), transaction.getTransferAmount(), getCurrentUserId());
                    log.info("Wallet Payout Failed added to ledger: {}", eventType);
                    break;
                default:
                    log.debug("No post-payout actions for event: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Error in post-payout actions for transaction ID: {}",
                    transaction.getId(), e);
        }
    }

    // ========== HELPER METHODS ==========

    private void sendPayoutSuccessNotification(PayoutTransaction transaction) {
        log.info("Sending payout success notification for transfer: {}", transaction.getTransferId());
        // Implement your notification logic here
    }

    private void updateAccountingForSuccessfulPayout(PayoutTransaction transaction) {
        log.info("Updating accounting for successful payout: {}", transaction.getTransferId());
        // Implement your accounting logic here
    }

    private void sendPayoutFailureNotification(PayoutTransaction transaction) {
        log.info("Sending payout failure notification for transfer: {}", transaction.getTransferId());
        // Implement your notification logic here
    }

    private void handlePayoutReversal(PayoutTransaction transaction) {
        log.info("Handling payout reversal for: {}", transaction.getTransferId());
        // Implement your reversal logic here
    }

    private void handleLowBalanceAlert(PayoutTransaction transaction) {
        log.warn("Low balance alert for account. Current balance in transaction: {}",
                transaction.getTransferAmount());
        // Implement your low balance handling logic here
        // e.g., notify admin, trigger recharge, etc.
    }

    /**
     * Constant-time string comparison (prevents timing attacks)
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    /**
     * Get webhook configuration status
     */
    public Map<String, Object> getWebhookConfigStatus() {
        String payoutWebhookSecret = cashfreeConfig.getClientSecret();
        Map<String, Object> status = new HashMap<>();
        status.put("webhookSecretConfigured",
                payoutWebhookSecret != null && !payoutWebhookSecret.isEmpty());
        status.put("timestamp", LocalDateTime.now().toString());
        status.put("service", "PayoutWebhookService");
        status.put("supportedEvents", new String[]{
                "TRANSFER_SUCCESS",
                "TRANSFER_FAILED",
                "TRANSFER_REVERSED",
                "TRANSFER_ACKNOWLEDGED",
                "TRANSFER_REJECTED",
                "CREDIT_CONFIRMATION",
                "LOW_BALANCE_ALERT"
        });
        return status;
    }
}