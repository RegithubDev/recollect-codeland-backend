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

            // Cashfree Payout signature: HMAC_SHA256(rawBody)
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
     * Parse Cashfree Payout webhook payload
     * Sample payload structure:
     * {
     *   "type": "TRANSFER_SUCCESS",
     *   "data": {
     *     "transferId": "transfer_123456",
     *     "referenceId": "ref_789012",
     *     "utr": "UTRN123456789012",
     *     "amount": "1000.00",
     *     "status": "SUCCESS",
     *     "processedOn": "2024-01-15T10:30:00+05:30",
     *     "fees": "5.00",
     *     "tax": "0.90",
     *     "failureReason": null,
     *     "beneficiaryDetails": {...}
     *   }
     * }
     */
    public Map<String, Object> parsePayoutWebhookPayload(String rawBody) throws Exception {
        JsonNode rootNode = objectMapper.readTree(rawBody);

        Map<String, Object> parsedData = new HashMap<>();

        // Extract event type
        String eventType = rootNode.get("type").asText();
        parsedData.put("eventType", eventType);

        // Extract event time if available
        if (rootNode.has("eventTime")) {
            parsedData.put("eventTime", rootNode.get("eventTime").asText());
        }

        // Extract data node
        JsonNode dataNode = rootNode.get("data");
        if (dataNode != null) {
            // Transfer details
            if (dataNode.has("transferId")) {
                parsedData.put("transferId", dataNode.get("transferId").asText());
            }

            if (dataNode.has("referenceId")) {
                parsedData.put("referenceId", dataNode.get("referenceId").asText());
            }

            if (dataNode.has("utr")) {
                parsedData.put("utr", dataNode.get("utr").asText());
            }

            if (dataNode.has("amount")) {
                parsedData.put("amount", new BigDecimal(dataNode.get("amount").asText()));
            }

            if (dataNode.has("status")) {
                parsedData.put("status", dataNode.get("status").asText());
            }

            if (dataNode.has("processedOn")) {
                parsedData.put("processedOn", dataNode.get("processedOn").asText());
            }

            if (dataNode.has("fees")) {
                parsedData.put("fees", new BigDecimal(dataNode.get("fees").asText()));
            }

            if (dataNode.has("tax")) {
                parsedData.put("tax", new BigDecimal(dataNode.get("tax").asText()));
            }

            if (dataNode.has("failureReason")) {
                parsedData.put("failureReason", dataNode.get("failureReason").asText());
            }

            if (dataNode.has("beneficiaryDetails")) {
                parsedData.put("beneficiaryDetails", dataNode.get("beneficiaryDetails").toString());
            }

            if (dataNode.has("remarks")) {
                parsedData.put("remarks", dataNode.get("remarks").asText());
            }
        }

        // Store raw payload for debugging
        parsedData.put("rawPayload", rawBody);

        log.debug("Parsed payout webhook data: {}", parsedData);
        return parsedData;
    }

    /**
     * Process payout webhook and update transaction
     */
    @Transactional
    public void processPayoutWebhook(Map<String, Object> webhookData) {
        String eventType = (String) webhookData.get("eventType");
        String transferId = (String) webhookData.get("transferId");
        String referenceId = (String) webhookData.get("referenceId");
        String status = (String) webhookData.get("status");

        log.info("Processing payout webhook - Event: {}, Transfer: {}, Status: {}",
                eventType, transferId, status);

        // Find payout transaction by transferId or referenceId
        PayoutTransaction transaction = null;

        if (transferId != null) {
            Optional<PayoutTransaction> byTransferId = payoutTransactionRepository
                    .findByTransferId(transferId);
            if (byTransferId.isPresent()) {
                transaction = byTransferId.get();
            }
        }

        if (transaction == null && referenceId != null) {
            Optional<PayoutTransaction> byReferenceId = payoutTransactionRepository
                    .findByReferenceId(referenceId);
            if (byReferenceId.isPresent()) {
                transaction = byReferenceId.get();
            }
        }

        if (transaction == null) {
            String errorMsg = String.format("Payout transaction not found. TransferId: %s, ReferenceId: %s",
                    transferId, referenceId);
            log.error(errorMsg);
            throw new IllegalArgumentException(errorMsg);
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

                if (webhookData.containsKey("processedOn")) {
                    // You might want to store processed time
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

            case "BENEFICIARY_ADDED":
                // Handle beneficiary addition
                log.info("Beneficiary added event received");
                if (webhookData.containsKey("beneficiaryDetails")) {
                    transaction.setBeneficiaryDetails(
                            (String) webhookData.get("beneficiaryDetails")
                    );
                }
                break;

            case "BENEFICIARY_VERIFICATION_FAILED":
                transaction.setStatusCode("BENEFICIARY_FAILED");
                transaction.setStatusDescription("Beneficiary verification failed");
                break;
        }

        // Update fees and tax if available
        if (webhookData.containsKey("fees")) {
            transaction.setFees((BigDecimal) webhookData.get("fees"));
        }

        if (webhookData.containsKey("tax")) {
            transaction.setTax((BigDecimal) webhookData.get("tax"));
        }

        // Store webhook metadata if needed
        if (webhookData.containsKey("rawPayload")) {
            // You might want to store webhook payload for debugging
            String existingMetadata = transaction.getMetadata();
            Map<String, Object> metadata = new HashMap<>();

            if (existingMetadata != null && !existingMetadata.isEmpty()) {
                try {
                    metadata = objectMapper.readValue(existingMetadata, Map.class);
                } catch (Exception e) {
                    log.warn("Could not parse existing metadata");
                }
            }

            metadata.put("lastWebhook", webhookData.get("eventType"));
            metadata.put("lastWebhookTime", LocalDateTime.now().toString());

            try {
                transaction.setMetadata(objectMapper.writeValueAsString(metadata));
            } catch (Exception e) {
                log.error("Could not serialize metadata", e);
            }
        }
    }

    /**
     * Map Cashfree status to your entity status
     */
    private String mapToEntityStatus(String eventType, String cashfreeStatus) {
        switch (eventType) {
            case "TRANSFER_SUCCESS":
                return "success";
            case "TRANSFER_FAILED":
                return "failed";
            case "TRANSFER_REVERSED":
                return "reversed";
            case "TRANSFER_PROCESSING":
                return "processing";
            case "BENEFICIARY_VERIFICATION_FAILED":
                return "rejected";
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
                    // Send success notification to user
                    sendPayoutSuccessNotification(transaction);
                    // Update accounting
                    updateAccountingForSuccessfulPayout(transaction);
                    // Update user wallet if needed
                    updateUserWallet(transaction, "SUCCESS");
                    break;

                case "TRANSFER_FAILED":
                    // Send failure notification
                    sendPayoutFailureNotification(transaction);
                    // Reverse wallet deduction
                    updateUserWallet(transaction, "FAILED");
                    // Log failure for analytics
                    logPayoutFailure(transaction);
                    break;

                case "TRANSFER_REVERSED":
                    // Handle reversal
                    handlePayoutReversal(transaction);
                    // Update user wallet
                    updateUserWallet(transaction, "REVERSED");
                    break;

                case "BENEFICIARY_ADDED":
                    // Update beneficiary status in your system
                    updateBeneficiaryStatus(transaction);
                    break;

                case "BENEFICIARY_VERIFICATION_FAILED":
                    // Notify admin about verification failure
                    notifyBeneficiaryVerificationFailure(transaction);
                    break;
            }
        } catch (Exception e) {
            log.error("Error in post-payout actions for transaction ID: {}",
                    transaction.getId(), e);
        }
    }

    // ========== HELPER METHODS (Implement based on your business logic) ==========

    private void sendPayoutSuccessNotification(PayoutTransaction transaction) {
        try {
            log.info("Sending payout success notification for transfer: {}",
                    transaction.getTransferId());

            // Implement notification logic:
            // 1. Send email to customer
            // 2. Send SMS
            // 3. Send in-app notification
            // 4. Update notification table

            // Example:
            // notificationService.sendEmail(
            //     transaction.getCustomerId(),
            //     "Payout Successful",
            //     String.format("Your payout of ₹%s has been processed successfully. UTR: %s",
            //         transaction.getTransferAmount(),
            //         transaction.getUtr())
            // );

        } catch (Exception e) {
            log.error("Failed to send payout success notification", e);
        }
    }

    private void updateAccountingForSuccessfulPayout(PayoutTransaction transaction) {
        try {
            log.info("Updating accounting for successful payout: {}",
                    transaction.getTransferId());

            // Implement accounting logic:
            // 1. Update ledger entries
            // 2. Update balance sheets
            // 3. Record transaction in accounting system

            // Example:
            // accountingService.recordPayout(
            //     transaction.getTransferAmount(),
            //     transaction.getFees(),
            //     transaction.getTax(),
            //     transaction.getTransferId()
            // );

        } catch (Exception e) {
            log.error("Failed to update accounting for payout", e);
        }
    }

    private void updateUserWallet(PayoutTransaction transaction, String status) {
        try {
            log.info("Updating user wallet for payout: {}, Status: {}",
                    transaction.getTransferId(), status);

            // If payout failed or reversed, add back amount to user's wallet
            if ("FAILED".equals(status) || "REVERSED".equals(status)) {
                // walletService.creditBalance(
                //     transaction.getCustomerId(),
                //     transaction.getTransferAmount(),
                //     "Payout reversal - " + transaction.getTransferId()
                // );
            }

        } catch (Exception e) {
            log.error("Failed to update user wallet for payout", e);
        }
    }

    private void sendPayoutFailureNotification(PayoutTransaction transaction) {
        try {
            log.info("Sending payout failure notification for transfer: {}",
                    transaction.getTransferId());

            // notificationService.sendEmail(
            //     transaction.getCustomerId(),
            //     "Payout Failed",
            //     String.format("Your payout of ₹%s has failed. Reason: %s",
            //         transaction.getTransferAmount(),
            //         transaction.getStatusDescription())
            // );

        } catch (Exception e) {
            log.error("Failed to send payout failure notification", e);
        }
    }

    private void logPayoutFailure(PayoutTransaction transaction) {
        // Log to analytics database
        log.warn("Payout failed - ID: {}, Customer: {}, Amount: {}, Reason: {}",
                transaction.getTransferId(),
                transaction.getCustomerId(),
                transaction.getTransferAmount(),
                transaction.getStatusDescription());
    }

    private void handlePayoutReversal(PayoutTransaction transaction) {
        log.info("Handling payout reversal for: {}", transaction.getTransferId());
        // Additional reversal logic if needed
    }

    private void updateBeneficiaryStatus(PayoutTransaction transaction) {
        log.info("Updating beneficiary status for payout: {}", transaction.getTransferId());
        // Update beneficiary verification status in your system
    }

    private void notifyBeneficiaryVerificationFailure(PayoutTransaction transaction) {
        log.warn("Beneficiary verification failed for payout: {}", transaction.getTransferId());
        // Notify admin team
        // adminNotificationService.notify(
        //     "Beneficiary Verification Failed",
        //     String.format("Beneficiary verification failed for transfer: %s",
        //         transaction.getTransferId())
        // );
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
                "TRANSFER_PROCESSING",
                "BENEFICIARY_ADDED",
                "BENEFICIARY_VERIFICATION_FAILED"
        });
        return status;
    }
}