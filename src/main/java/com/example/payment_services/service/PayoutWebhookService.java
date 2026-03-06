package com.example.payment_services.service;

import com.example.payment_services.config.CashfreeConfig;
import com.example.payment_services.entity.PayoutTransaction;
import com.example.payment_services.repository.PayoutTransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
        REJECTED("rejected");

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
     */
    public boolean verifyPayoutSignature(String rawBody, String signature) {
        try {
            String payoutWebhookSecret = cashfreeConfig.getClientSecret();
            if (payoutWebhookSecret == null || payoutWebhookSecret.isEmpty()) {
                log.error("Payout webhook secret not configured");
                return false;
            }

            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    payoutWebhookSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            sha256_HMAC.init(secretKeySpec);

            String computedSignature = Base64.getEncoder().encodeToString(
                    sha256_HMAC.doFinal(rawBody.getBytes(StandardCharsets.UTF_8))
            );

            boolean isValid = constantTimeEquals(computedSignature, signature);

            if (isValid) {
                log.info("Signature verification successful");
            } else {
                log.warn("Signature verification failed");
                log.debug("Computed: {}, Received: {}", computedSignature, signature);
            }

            return isValid;

        } catch (Exception e) {
            log.error("Error verifying payout webhook signature", e);
            return false;
        }
    }

    /**
     * Parse Cashfree Payout webhook payload - UPDATED to match actual Cashfree format
     */
    public Map<String, Object> parsePayoutWebhookPayload(String rawBody) throws Exception {
        Map<String, Object> parsedData = new HashMap<>();

        // Handle empty or test payload
        if (rawBody == null || rawBody.trim().isEmpty()) {
            log.info("Empty payout webhook payload - test request");
            parsedData.put("isTest", true);
            return parsedData;
        }

        JsonNode rootNode = objectMapper.readTree(rawBody);

        // Check if this is a test webhook
        if (isTestWebhook(rootNode)) {
            log.info("Test payout webhook detected");
            parsedData.put("isTest", true);
            return parsedData;
        }

        log.info("Parsing payout webhook payload");

        // Extract event type (using "type" field)
        if (rootNode.has("type") && !rootNode.get("type").isNull()) {
            String eventType = rootNode.get("type").asText();
            parsedData.put("eventType", eventType);
            log.info("Event type: {}", eventType);
        }

        // Extract event time (using "event_time" field)
        if (rootNode.has("event_time") && !rootNode.get("event_time").isNull()) {
            parsedData.put("eventTime", rootNode.get("event_time").asText());
        }

        // Extract data node
        JsonNode dataNode = rootNode.get("data");
        if (dataNode != null && !dataNode.isNull()) {

            // Transfer ID (transfer_id)
            if (dataNode.has("transfer_id") && !dataNode.get("transfer_id").isNull()) {
                String transferId = dataNode.get("transfer_id").asText();
                parsedData.put("transferId", transferId);
                log.info("Transfer ID: {}", transferId);
            }

            // CF Transfer ID (cf_transfer_id)
            if (dataNode.has("cf_transfer_id") && !dataNode.get("cf_transfer_id").isNull()) {
                parsedData.put("cfTransferId", dataNode.get("cf_transfer_id").asText());
            }

            // Status
            if (dataNode.has("status") && !dataNode.get("status").isNull()) {
                parsedData.put("status", dataNode.get("status").asText());
            }

            // Status Code
            if (dataNode.has("status_code") && !dataNode.get("status_code").isNull()) {
                parsedData.put("statusCode", dataNode.get("status_code").asText());
            }

            // Status Description
            if (dataNode.has("status_description") && !dataNode.get("status_description").isNull()) {
                parsedData.put("statusDescription", dataNode.get("status_description").asText());
            }

            // Transfer Amount
            if (dataNode.has("transfer_amount") && !dataNode.get("transfer_amount").isNull()) {
                try {
                    BigDecimal amount = new BigDecimal(dataNode.get("transfer_amount").asText());
                    parsedData.put("amount", amount);
                    log.info("Amount: {}", amount);
                } catch (Exception e) {
                    log.warn("Could not parse transfer_amount: {}", dataNode.get("transfer_amount").asText());
                }
            }

            // Transfer UTR
            if (dataNode.has("transfer_utr") && !dataNode.get("transfer_utr").isNull()) {
                parsedData.put("utr", dataNode.get("transfer_utr").asText());
                log.info("UTR: {}", dataNode.get("transfer_utr").asText());
            }

            // Transfer Service Charge (fees)
            if (dataNode.has("transfer_service_charge") && !dataNode.get("transfer_service_charge").isNull()) {
                try {
                    BigDecimal fees = new BigDecimal(dataNode.get("transfer_service_charge").asText());
                    parsedData.put("fees", fees);
                    log.info("Fees: {}", fees);
                } catch (Exception e) {
                    log.warn("Could not parse transfer_service_charge");
                }
            }

            // Transfer Service Tax
            if (dataNode.has("transfer_service_tax") && !dataNode.get("transfer_service_tax").isNull()) {
                try {
                    BigDecimal tax = new BigDecimal(dataNode.get("transfer_service_tax").asText());
                    parsedData.put("tax", tax);
                    log.info("Tax: {}", tax);
                } catch (Exception e) {
                    log.warn("Could not parse transfer_service_tax");
                }
            }

            // Transfer Mode
            if (dataNode.has("transfer_mode") && !dataNode.get("transfer_mode").isNull()) {
                parsedData.put("transferMode", dataNode.get("transfer_mode").asText());
            }

            // Beneficiary Details
            if (dataNode.has("beneficiary_details") && !dataNode.get("beneficiary_details").isNull()) {
                JsonNode benNode = dataNode.get("beneficiary_details");
                if (benNode.has("beneficiary_id") && !benNode.get("beneficiary_id").isNull()) {
                    parsedData.put("beneficiaryId", benNode.get("beneficiary_id").asText());
                }

                // Store full beneficiary details as JSON for metadata
                try {
                    parsedData.put("beneficiaryDetails", objectMapper.writeValueAsString(benNode));
                } catch (Exception e) {
                    log.warn("Could not serialize beneficiary details");
                }
            }

            // Added on / Updated on
            if (dataNode.has("added_on") && !dataNode.get("added_on").isNull()) {
                parsedData.put("addedOn", dataNode.get("added_on").asText());
            }

            if (dataNode.has("updated_on") && !dataNode.get("updated_on").isNull()) {
                parsedData.put("updatedOn", dataNode.get("updated_on").asText());
            }
        }

        // Store raw payload for debugging
        parsedData.put("rawPayload", rawBody);

        log.info("Parsed payout webhook data: {}", parsedData);
        return parsedData;
    }

    /**
     * Check if webhook is a test
     */
    private boolean isTestWebhook(JsonNode rootNode) {
        // Check for test indicators in the payload
        if (rootNode.has("test") || rootNode.has("isTest")) {
            return true;
        }

        // Check event type for test indicators
        if (rootNode.has("type")) {
            String type = rootNode.get("type").asText();
            if (type.contains("TEST") || type.contains("test")) {
                return true;
            }
        }

        // Check data for test indicators
        JsonNode dataNode = rootNode.get("data");
        if (dataNode != null) {
            if (dataNode.has("transfer_id")) {
                String transferId = dataNode.get("transfer_id").asText();
                if (transferId.contains("test") || transferId.contains("TEST")) {
                    return true;
                }
            }
        }

        return false;
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
        String cfTransferId = (String) webhookData.get("cfTransferId");
        String status = (String) webhookData.get("status");

        log.info("Event Type: {}", eventType);
        log.info("Transfer ID: {}", transferId);
        log.info("CF Transfer ID: {}", cfTransferId);
        log.info("Status: {}", status);

        // If no transferId, this might be a test
        if (transferId == null || transferId.isEmpty()) {
            log.warn("Payout webhook without transfer_id - treating as test");
            return;
        }

        // Find payout transaction by transferId
        log.info("Looking for transaction with transferId: {}", transferId);
        Optional<PayoutTransaction> transactionOpt = payoutTransactionRepository.findByTransferId(transferId);

        if (transactionOpt.isEmpty()) {
            log.error("Payout transaction not found for transferId: {}", transferId);

            // Try to find by cfTransferId as fallback
            if (cfTransferId != null && !cfTransferId.isEmpty()) {
                log.info("Trying to find by cfTransferId: {}", cfTransferId);
                transactionOpt = payoutTransactionRepository.findByCfTransferId(cfTransferId);
            }

            if (transactionOpt.isEmpty()) {
                log.error("Payout transaction not found for cfTransferId either");
                return;
            }
        }

        PayoutTransaction transaction = transactionOpt.get();
        log.info("Found transaction - ID: {}, Current Status: {}",
                transaction.getId(), transaction.getStatus());

        // Log before update
        log.info("Before update - Status: {}, StatusCode: {}",
                transaction.getStatus(), transaction.getStatusCode());

        // Update transaction based on event type
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
        String cfTransferId = (String) webhookData.get("cfTransferId");
        String utr = (String) webhookData.get("utr");
        String statusCode = (String) webhookData.get("statusCode");
        String statusDescription = (String) webhookData.get("statusDescription");
        String transferMode = (String) webhookData.get("transferMode");

        log.info("Updating transaction {} with event: {}, status: {}",
                transaction.getTransferId(), eventType, status);

        // Update basic fields
        transaction.setUpdatedAt(LocalDateTime.now());

        // Set CF Transfer ID if available and not already set
        if (cfTransferId != null && !cfTransferId.isEmpty() && transaction.getCfTransferId() == null) {
            transaction.setCfTransferId(cfTransferId);
            log.info("Set cfTransferId: {}", cfTransferId);
        }

        // Map Cashfree status to your entity status
        String entityStatus = mapToEntityStatus(eventType, status);
        transaction.setStatus(entityStatus);

        // Set status code and description
        if (statusCode != null) {
            transaction.setStatusCode(statusCode);
        }

        if (statusDescription != null) {
            transaction.setStatusDescription(statusDescription);
        }

        // Update specific fields based on event type
        if ("TRANSFER_SUCCESS".equals(eventType)) {
            if (utr != null) {
                // Store UTR in reference_id or metadata
                transaction.setReferenceId(utr);
                log.info("Set UTR/ReferenceId: {}", utr);
            }
        }

        // Update transfer mode if provided
        if (transferMode != null) {
            transaction.setTransferMode(transferMode);
        }

        // Update fees and tax if available
        if (webhookData.containsKey("fees") && webhookData.get("fees") != null) {
            transaction.setFees((BigDecimal) webhookData.get("fees"));
            log.info("Set fees: {}", webhookData.get("fees"));
        }

        if (webhookData.containsKey("tax") && webhookData.get("tax") != null) {
            transaction.setTax((BigDecimal) webhookData.get("tax"));
            log.info("Set tax: {}", webhookData.get("tax"));
        }

        // Build metadata with additional info
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
     * Map Cashfree status to your entity status
     */
    private String mapToEntityStatus(String eventType, String cashfreeStatus) {
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
            case "TRANSFER_PROCESSING":
                return "processing";
            default:
                // Map from Cashfree status string
                if (cashfreeStatus != null) {
                    switch (cashfreeStatus.toUpperCase()) {
                        case "SUCCESS":
                        case "SUCCESSFUL":
                            return "success";
                        case "FAILED":
                        case "FAILURE":
                            return "failed";
                        case "PENDING":
                        case "PROCESSING":
                            return "processing";
                        case "REVERSED":
                            return "reversed";
                        case "REJECTED":
                            return "rejected";
                        default:
                            return "pending";
                    }
                }
                return "pending";
        }
    }

    /**
     * Trigger actions after payout processing
     */
    private void triggerPostPayoutActions(PayoutTransaction transaction, String eventType) {
        try {
            switch (eventType) {
                case "TRANSFER_SUCCESS":
                    sendPayoutSuccessNotification(transaction);
                    updateAccountingForSuccessfulPayout(transaction);
                    break;

                case "TRANSFER_FAILED":
                    sendPayoutFailureNotification(transaction);
                    break;

                case "TRANSFER_REVERSED":
                    handlePayoutReversal(transaction);
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
                "TRANSFER_PROCESSING"
        });
        return status;
    }
}