package com.example.payment_services.service;

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

    @Value("${cashfree.payout.webhook.secret:}")
    private String payoutWebhookSecret;

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

            return constantTimeEquals(computedSignature, signature);

        } catch (Exception e) {
            log.error("Error verifying payout webhook signature", e);
            return false;
        }
    }

    /**
     * Parse Cashfree Payout webhook payload with null safety
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

        // Extract event type with null check
        if (rootNode.has("type") && !rootNode.get("type").isNull()) {
            parsedData.put("eventType", rootNode.get("type").asText());
        }

        // Extract event time if available
        if (rootNode.has("eventTime") && !rootNode.get("eventTime").isNull()) {
            parsedData.put("eventTime", rootNode.get("eventTime").asText());
        }

        // Extract data node
        JsonNode dataNode = rootNode.get("data");
        if (dataNode != null && !dataNode.isNull()) {

            // Transfer details with null checks
            if (dataNode.has("transferId") && !dataNode.get("transferId").isNull()) {
                parsedData.put("transferId", dataNode.get("transferId").asText());
            }

            if (dataNode.has("referenceId") && !dataNode.get("referenceId").isNull()) {
                parsedData.put("referenceId", dataNode.get("referenceId").asText());
            }

            if (dataNode.has("utr") && !dataNode.get("utr").isNull()) {
                parsedData.put("utr", dataNode.get("utr").asText());
            }

            if (dataNode.has("amount") && !dataNode.get("amount").isNull()) {
                try {
                    parsedData.put("amount", new BigDecimal(dataNode.get("amount").asText()));
                } catch (Exception e) {
                    log.warn("Could not parse amount: {}", dataNode.get("amount").asText());
                }
            }

            if (dataNode.has("status") && !dataNode.get("status").isNull()) {
                parsedData.put("status", dataNode.get("status").asText());
            }

            if (dataNode.has("processedOn") && !dataNode.get("processedOn").isNull()) {
                parsedData.put("processedOn", dataNode.get("processedOn").asText());
            }

            if (dataNode.has("fees") && !dataNode.get("fees").isNull()) {
                try {
                    parsedData.put("fees", new BigDecimal(dataNode.get("fees").asText()));
                } catch (Exception e) {
                    log.warn("Could not parse fees");
                }
            }

            if (dataNode.has("tax") && !dataNode.get("tax").isNull()) {
                try {
                    parsedData.put("tax", new BigDecimal(dataNode.get("tax").asText()));
                } catch (Exception e) {
                    log.warn("Could not parse tax");
                }
            }

            if (dataNode.has("failureReason") && !dataNode.get("failureReason").isNull()) {
                parsedData.put("failureReason", dataNode.get("failureReason").asText());
            }

            if (dataNode.has("remarks") && !dataNode.get("remarks").isNull()) {
                parsedData.put("remarks", dataNode.get("remarks").asText());
            }
        }

        // Store raw payload for debugging
        parsedData.put("rawPayload", rawBody);

        log.debug("Parsed payout webhook data: {}", parsedData);
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
            if (dataNode.has("transferId")) {
                String transferId = dataNode.get("transferId").asText();
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
        // Check if this is a test webhook
        if (webhookData.containsKey("isTest") && (Boolean) webhookData.get("isTest")) {
            log.info("Test payout webhook received - skipping processing");
            return;
        }

        String eventType = (String) webhookData.get("eventType");
        String transferId = (String) webhookData.get("transferId");
        String referenceId = (String) webhookData.get("referenceId");
        String status = (String) webhookData.get("status");

        // If no transferId or referenceId, this might be a test
        if ((transferId == null || transferId.isEmpty()) &&
                (referenceId == null || referenceId.isEmpty())) {
            log.info("Payout webhook without identifiers - treating as test");
            return;
        }

        log.info("Processing payout webhook - Event: {}, Transfer: {}, Status: {}",
                eventType, transferId, status);

        // Find payout transaction by transferId or referenceId
        PayoutTransaction transaction = null;

        if (transferId != null && !transferId.isEmpty()) {
            Optional<PayoutTransaction> byTransferId = payoutTransactionRepository
                    .findByTransferId(transferId);
            if (byTransferId.isPresent()) {
                transaction = byTransferId.get();
            }
        }

        if (transaction == null && referenceId != null && !referenceId.isEmpty()) {
            Optional<PayoutTransaction> byReferenceId = payoutTransactionRepository
                    .findByReferenceId(referenceId);
            if (byReferenceId.isPresent()) {
                transaction = byReferenceId.get();
            }
        }

        if (transaction == null) {
            log.warn("Payout transaction not found for test webhook. TransferId: {}, ReferenceId: {}",
                    transferId, referenceId);
            return; // Don't throw exception for test webhooks
        }

        // Update transaction based on event type
        updatePayoutTransaction(transaction, webhookData);

        // Save the updated transaction
        payoutTransactionRepository.save(transaction);

        // Trigger post-processing actions
        triggerPostPayoutActions(transaction, eventType);

        log.info("Payout webhook processed successfully. ID: {}, Status: {}",
                transaction.getId(), transaction.getStatus());
    }

    /**
     * Update payout transaction with webhook data
     */
    private void updatePayoutTransaction(PayoutTransaction transaction,
                                         Map<String, Object> webhookData) {

        String eventType = (String) webhookData.get("eventType");
        String status = (String) webhookData.get("status");

        // Update basic fields
        transaction.setUpdatedAt(LocalDateTime.now());

        // Map Cashfree status to your entity status
        String entityStatus = mapToEntityStatus(eventType, status);
        transaction.setStatus(entityStatus);

        // Update specific fields based on event type
        switch (eventType) {
            case "TRANSFER_SUCCESS":
                transaction.setStatusCode("SUCCESS");
                transaction.setStatusDescription("Transfer completed successfully");

                if (webhookData.containsKey("utr")) {
                    // Assuming you have a utr field in your entity
                    // If not, you can store it in metadata
                    String utr = (String) webhookData.get("utr");
                    // transaction.setUtr(utr); // If you add this field
                }
                break;

            case "TRANSFER_FAILED":
                transaction.setStatusCode("FAILED");
                transaction.setStatusDescription("Transfer failed");

                if (webhookData.containsKey("failureReason")) {
                    transaction.setStatusDescription(
                            "Transfer failed: " + webhookData.get("failureReason")
                    );
                }
                break;

            case "TRANSFER_REVERSED":
                transaction.setStatusCode("REVERSED");
                transaction.setStatusDescription("Transfer reversed");
                break;

            case "TRANSFER_PROCESSING":
                transaction.setStatusCode("PROCESSING");
                transaction.setStatusDescription("Transfer is being processed");
                break;

            default:
                log.warn("Unknown event type: {}", eventType);
        }

        // Update fees and tax if available
        if (webhookData.containsKey("fees")) {
            transaction.setFees((BigDecimal) webhookData.get("fees"));
        }

        if (webhookData.containsKey("tax")) {
            transaction.setTax((BigDecimal) webhookData.get("tax"));
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