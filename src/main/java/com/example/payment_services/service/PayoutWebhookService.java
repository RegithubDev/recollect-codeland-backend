package com.example.payment_services.service;

import com.example.payment_services.config.CashfreeConfig;
import com.example.payment_services.entity.PayoutTransaction;
import com.example.payment_services.repository.PayoutTransactionRepository;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutWebhookService {

    private final PayoutTransactionRepository payoutTransactionRepository;
    private final ObjectMapper objectMapper;
    private final CashfreeConfig cashfreeConfig;

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
     * Verify Cashfree Payout webhook signature
     * Based on Cashfree V1 webhook documentation:
     * 1. Get all POST parameters except 'signature'
     * 2. Sort the array based on keys
     * 3. Concatenate all values in sorted order
     * 4. Encrypt using SHA-256 and base64 encode
     * 5. Compare with received signature
     */
    public boolean verifyPayoutSignature(String rawBody, String signature) {
        try {
            String payoutWebhookSecret = cashfreeConfig.getClientSecret();
            if (payoutWebhookSecret == null || payoutWebhookSecret.isEmpty()) {
                log.error("Payout webhook secret not configured");
                return false;
            }

            log.info("Verifying payout signature with secret length: {}", payoutWebhookSecret.length());
            log.debug("Raw body: {}", rawBody);
            log.debug("Received signature from header: {}", signature);

            // Parse JSON to Map
            Map<String, Object> jsonMap = objectMapper.readValue(
                    rawBody,
                    new TypeReference<Map<String, Object>>() {}
            );

            log.info("Payload keys: {}", jsonMap.keySet());

            // Remove signature field if present
            jsonMap.remove("signature");

            // Sort map by keys and recursively process values
            String postDataString = buildStringFromMap(jsonMap);

            log.debug("Concatenated string length: {}", postDataString.length());
            log.debug("Concatenated string: {}", postDataString);

            // Generate HMAC-SHA256
            Mac sha256HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    payoutWebhookSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            sha256HMAC.init(secretKey);

            byte[] hashBytes = sha256HMAC.doFinal(postDataString.getBytes(StandardCharsets.UTF_8));
            String computedSignature = java.util.Base64.getEncoder().encodeToString(hashBytes);

            log.info("Computed signature: {}", computedSignature);
            log.info("Received signature: {}", signature);

            boolean isValid = computedSignature.equals(signature);

            if (isValid) {
                log.info("Payout signature verification successful");
            } else {
                log.warn("Payout signature verification failed");
                log.warn("Computed: {}", computedSignature);
                log.warn("Received: {}", signature);
            }

            return isValid;

        } catch (Exception e) {
            log.error("Error verifying payout webhook signature", e);
            return false;
        }
    }

    /**
     * Recursively builds a string from a map by:
     * 1. Sorting keys alphabetically
     * 2. Concatenating all values (recursively for nested maps)
     * 3. No quotes, no formatting - just raw values
     */
    private String buildStringFromMap(Map<String, Object> map) {
        if (map == null) return "";

        return map.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> valueToString(entry.getValue()))
                .collect(Collectors.joining());
    }

    /**
     * Converts any value to string, handling nested maps and lists
     */
    private String valueToString(Object value) {
        if (value == null) return "";

        if (value instanceof Map) {
            // Recursively process nested maps
            @SuppressWarnings("unchecked")
            Map<String, Object> nestedMap = (Map<String, Object>) value;
            return buildStringFromMap(nestedMap);
        }

        if (value instanceof List) {
            // Process lists
            List<?> list = (List<?>) value;
            return list.stream()
                    .map(this::valueToString)
                    .collect(Collectors.joining());
        }

        // For primitive types, just return the string value
        return value.toString();
    }
    /**
     * Parse Cashfree Payout webhook payload
     * Supports all webhook events:
     * - TRANSFER_SUCCESS
     * - TRANSFER_FAILED
     * - TRANSFER_REVERSED
     * - CREDIT_CONFIRMATION
     * - TRANSFER_ACKNOWLEDGED
     * - TRANSFER_REJECTED
     * - BENEFICIARY_INCIDENT
     * - LOW_BALANCE_ALERT
     * - BULK_TRANSFER_REJECTED
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

        // Extract signature for logging
        if (rootMap.containsKey("signature")) {
            parsedData.put("signature", rootMap.get("signature").toString());
        }

        // Extract event type (common for all webhooks)
        if (rootMap.containsKey("event")) {
            String eventType = rootMap.get("event").toString();
            parsedData.put("eventType", eventType);
            log.info("Event type: {}", eventType);
        }

        // Extract event time if present
        if (rootMap.containsKey("eventTime")) {
            parsedData.put("eventTime", rootMap.get("eventTime").toString());
        }

        // Process based on event type
        String eventType = parsedData.getOrDefault("eventType", "").toString();

        switch (eventType) {
            case "TRANSFER_SUCCESS":
                parseTransferSuccess(rootMap, parsedData);
                break;

            case "TRANSFER_FAILED":
                parseTransferFailed(rootMap, parsedData);
                break;

            case "TRANSFER_REVERSED":
                parseTransferReversed(rootMap, parsedData);
                break;

            case "TRANSFER_ACKNOWLEDGED":
                parseTransferAcknowledged(rootMap, parsedData);
                break;

            case "TRANSFER_REJECTED":
                parseTransferRejected(rootMap, parsedData);
                break;

            case "CREDIT_CONFIRMATION":
                parseCreditConfirmation(rootMap, parsedData);
                break;

            case "LOW_BALANCE_ALERT":
                parseLowBalanceAlert(rootMap, parsedData);
                break;

            default:
                log.warn("Unknown event type: {}", eventType);
                // Try to extract common fields anyway
                extractCommonFields(rootMap, parsedData);
        }

        // Store raw payload
        parsedData.put("rawPayload", rawBody);

        log.info("Parsed payout webhook data: {}", parsedData);
        return parsedData;
    }

    private void parseTransferSuccess(Map<String, Object> rootMap, Map<String, Object> parsedData) {
        // TRANSFER_SUCCESS parameters:
        // - transferId: Id of the transfer passed by the merchant
        // - referenceId: Id of the transfer generated by Cashfree Payments
        // - acknowledged: Flag if beneficiary bank has acknowledged the transfer
        // - eventTime: Transfer initiation time
        // - utr: Unique transaction reference number provided by the bank

        if (rootMap.containsKey("transferId")) {
            parsedData.put("transferId", rootMap.get("transferId").toString());
        }
        if (rootMap.containsKey("referenceId")) {
            parsedData.put("referenceId", rootMap.get("referenceId").toString());
            parsedData.put("cfTransferId", rootMap.get("referenceId").toString()); // Map to cfTransferId
        }
        if (rootMap.containsKey("acknowledged")) {
            parsedData.put("acknowledged", rootMap.get("acknowledged"));
        }
        if (rootMap.containsKey("utr")) {
            parsedData.put("utr", rootMap.get("utr").toString());
        }

        // Set status based on acknowledged flag
        Object ack = rootMap.get("acknowledged");
        if (ack != null) {
            if ("1".equals(ack.toString()) || Boolean.TRUE.equals(ack)) {
                parsedData.put("status", "SUCCESS");
                parsedData.put("statusDescription", "Transfer completed and credited to beneficiary");
            } else {
                parsedData.put("status", "PROCESSING");
                parsedData.put("statusDescription", "Transfer initiated, awaiting bank acknowledgment");
            }
        } else {
            parsedData.put("status", "SUCCESS");
        }
    }

    private void parseTransferFailed(Map<String, Object> rootMap, Map<String, Object> parsedData) {
        // TRANSFER_FAILED parameters:
        // - transferId: Id of the transfer passed by the merchant
        // - referenceId: Id of the transfer generated by Cashfree Payments
        // - reason: Reason for failure

        if (rootMap.containsKey("transferId")) {
            parsedData.put("transferId", rootMap.get("transferId").toString());
        }
        if (rootMap.containsKey("referenceId")) {
            parsedData.put("referenceId", rootMap.get("referenceId").toString());
            parsedData.put("cfTransferId", rootMap.get("referenceId").toString());
        }
        if (rootMap.containsKey("reason")) {
            parsedData.put("failureReason", rootMap.get("reason").toString());
        }
        parsedData.put("status", "FAILED");
        parsedData.put("statusDescription", "Transfer failed: " + rootMap.getOrDefault("reason", "Unknown reason"));
    }

    private void parseTransferReversed(Map<String, Object> rootMap, Map<String, Object> parsedData) {
        // TRANSFER_REVERSED parameters:
        // - transferId: Id of the transfer passed by the merchant
        // - referenceId: Id of the transfer generated by Cashfree Payments
        // - eventTime: Time at which the transfer was reversed
        // - reason: Reason for reversal

        if (rootMap.containsKey("transferId")) {
            parsedData.put("transferId", rootMap.get("transferId").toString());
        }
        if (rootMap.containsKey("referenceId")) {
            parsedData.put("referenceId", rootMap.get("referenceId").toString());
            parsedData.put("cfTransferId", rootMap.get("referenceId").toString());
        }
        if (rootMap.containsKey("reason")) {
            parsedData.put("failureReason", rootMap.get("reason").toString());
        }
        parsedData.put("status", "REVERSED");
        parsedData.put("statusDescription", "Transfer reversed: " + rootMap.getOrDefault("reason", "Unknown reason"));
    }

    private void parseTransferAcknowledged(Map<String, Object> rootMap, Map<String, Object> parsedData) {
        // TRANSFER_ACKNOWLEDGED parameters:
        // - transferId: Id of the transfer passed by the merchant
        // - referenceId: Id of the transfer generated by Cashfree Payments
        // - acknowledged: Flag if beneficiary bank acknowledges the transfer

        if (rootMap.containsKey("transferId")) {
            parsedData.put("transferId", rootMap.get("transferId").toString());
        }
        if (rootMap.containsKey("referenceId")) {
            parsedData.put("referenceId", rootMap.get("referenceId").toString());
            parsedData.put("cfTransferId", rootMap.get("referenceId").toString());
        }
        if (rootMap.containsKey("acknowledged")) {
            parsedData.put("acknowledged", rootMap.get("acknowledged"));
        }
        parsedData.put("status", "ACKNOWLEDGED");
        parsedData.put("statusDescription", "Bank has acknowledged the transfer");
    }

    private void parseTransferRejected(Map<String, Object> rootMap, Map<String, Object> parsedData) {
        // TRANSFER_REJECTED parameters:
        // - transferId: Id of the transfer passed by the merchant
        // - referenceId: Id of the transfer generated by Cashfree Payments
        // - reason: Reason for rejection

        if (rootMap.containsKey("transferId")) {
            parsedData.put("transferId", rootMap.get("transferId").toString());
        }
        if (rootMap.containsKey("referenceId")) {
            parsedData.put("referenceId", rootMap.get("referenceId").toString());
            parsedData.put("cfTransferId", rootMap.get("referenceId").toString());
        }
        if (rootMap.containsKey("reason")) {
            parsedData.put("failureReason", rootMap.get("reason").toString());
        }
        parsedData.put("status", "REJECTED");
        parsedData.put("statusDescription", "Transfer rejected: " + rootMap.getOrDefault("reason", "Unknown reason"));
    }

    private void parseCreditConfirmation(Map<String, Object> rootMap, Map<String, Object> parsedData) {
        // CREDIT_CONFIRMATION parameters:
        // - ledgerBalance: The overall balance of ledger
        // - amount: Amount deposited
        // - utr: Unique transaction reference number

        if (rootMap.containsKey("amount")) {
            try {
                parsedData.put("amount", new BigDecimal(rootMap.get("amount").toString()));
            } catch (Exception e) {
                log.warn("Could not parse amount");
            }
        }
        if (rootMap.containsKey("ledgerBalance")) {
            parsedData.put("ledgerBalance", rootMap.get("ledgerBalance").toString());
        }
        if (rootMap.containsKey("utr")) {
            parsedData.put("utr", rootMap.get("utr").toString());
        }
        parsedData.put("status", "SUCCESS");
    }

    private void parseLowBalanceAlert(Map<String, Object> rootMap, Map<String, Object> parsedData) {
        // LOW_BALANCE_ALERT parameters:
        // - currentBalance: The current balance of the beneficiary account
        // - alertTime: Alert initiation time

        if (rootMap.containsKey("currentBalance")) {
            try {
                parsedData.put("currentBalance", new BigDecimal(rootMap.get("currentBalance").toString()));
            } catch (Exception e) {
                log.warn("Could not parse currentBalance");
            }
        }
        if (rootMap.containsKey("alertTime")) {
            parsedData.put("alertTime", rootMap.get("alertTime").toString());
        }
        parsedData.put("status", "LOW_BALANCE");
    }

    private void extractCommonFields(Map<String, Object> rootMap, Map<String, Object> parsedData) {
        // Try to extract common fields if event type is unknown
        if (rootMap.containsKey("transferId")) {
            parsedData.put("transferId", rootMap.get("transferId").toString());
        }
        if (rootMap.containsKey("referenceId")) {
            parsedData.put("referenceId", rootMap.get("referenceId").toString());
            parsedData.put("cfTransferId", rootMap.get("referenceId").toString());
        }
        if (rootMap.containsKey("utr")) {
            parsedData.put("utr", rootMap.get("utr").toString());
        }
        if (rootMap.containsKey("reason")) {
            parsedData.put("failureReason", rootMap.get("reason").toString());
        }
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

        log.info("Event Type: {}", eventType);
        log.info("Transfer ID: {}", transferId);
        log.info("Reference ID: {}", referenceId);
        log.info("CF Transfer ID: {}", cfTransferId);
        log.info("Status: {}", status);
        log.info("UTR: {}", utr);

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

        if (transaction == null) {
            log.error("Payout transaction not found for any identifier");
            log.error("Searched by - transferId: {}, referenceId: {}, cfTransferId: {}",
                    transferId, referenceId, cfTransferId);
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
        if (utr != null && !utr.isEmpty()) {
            transaction.setReferenceId(utr); // Store UTR in referenceId
        }

        // Update fees and tax if available
        if (webhookData.containsKey("fees") && webhookData.get("fees") != null) {
            transaction.setFees((BigDecimal) webhookData.get("fees"));
        }

        if (webhookData.containsKey("tax") && webhookData.get("tax") != null) {
            transaction.setTax((BigDecimal) webhookData.get("tax"));
        }

        // Build metadata
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("eventType", eventType);
            metadata.put("processedAt", LocalDateTime.now().toString());
            metadata.put("utr", utr);
            metadata.put("webhookData", webhookData);

            transaction.setMetadata(objectMapper.writeValueAsString(metadata));
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
                return status != null ? status.toLowerCase() : "pending";
        }
    }

    /**
     * Get human-readable status description
     */
    private String getStatusDescription(String eventType, String status, String failureReason) {
        if (eventType == null) return "Unknown status";

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
                case "CREDIT_CONFIRMATION":
                    sendPayoutSuccessNotification(transaction);
                    updateAccountingForSuccessfulPayout(transaction);
                    break;

                case "TRANSFER_FAILED":
                case "TRANSFER_REJECTED":
                    sendPayoutFailureNotification(transaction);
                    break;

                case "TRANSFER_REVERSED":
                    handlePayoutReversal(transaction);
                    break;

                case "LOW_BALANCE_ALERT":
                    handleLowBalanceAlert(transaction);
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