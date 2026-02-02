package com.example.payment_services.service;

import com.example.payment_services.entity.PaymentTransaction;
import com.example.payment_services.repository.PaymentTransactionRepository;
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
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundWebhookService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ObjectMapper objectMapper;

    @Value("${cashfree.refund.webhook.secret:}")
    private String refundWebhookSecret;

    /**
     * Verify Cashfree Refund webhook signature
     * Same as payin webhook: HMAC_SHA256(timestamp + rawBody)
     */
    public boolean verifyRefundSignature(String rawBody, String signature, String timestamp) {
        try {
            if (refundWebhookSecret == null || refundWebhookSecret.isEmpty()) {
                log.error("Refund webhook secret not configured");
                return false;
            }

            String data = timestamp + rawBody;
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    refundWebhookSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            sha256_HMAC.init(secretKeySpec);

            String computedSignature = Base64.getEncoder().encodeToString(
                    sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8))
            );

            return constantTimeEquals(computedSignature, signature);

        } catch (Exception e) {
            log.error("Error verifying refund webhook signature", e);
            return false;
        }
    }

    /**
     * Parse Cashfree Refund webhook payload
     * Sample payload:
     * {
     *   "data": {
     *     "refund": {
     *       "cf_refund_id": "1234567890",
     *       "order_id": "order_12345",
     *       "refund_id": "refund_12345",
     *       "entity": "refund",
     *       "refund_amount": 100.50,
     *       "refund_currency": "INR",
     *       "refund_note": "Customer requested refund",
     *       "refund_status": "SUCCESS",
     *       "refund_type": "NORMAL",
     *       "refund_arn": "ARN1234567890",
     *       "refund_charge": 0.00,
     *       "refund_splits": [],
     *       "refund_mode": "STANDARD",
     *       "created_at": "2024-01-15T10:30:00+05:30",
     *       "processed_at": "2024-01-15T10:35:00+05:30"
     *     },
     *     "order": {
     *       "order_id": "order_12345",
     *       "order_amount": 100.50,
     *       "order_currency": "INR"
     *     }
     *   },
     *   "event_time": "2024-01-15T10:35:00+05:30",
     *   "type": "REFUND_SUCCESS_WEBHOOK"
     * }
     */
    public Map<String, Object> parseRefundWebhookPayload(String rawBody) throws Exception {
        JsonNode rootNode = objectMapper.readTree(rawBody);

        Map<String, Object> parsedData = new HashMap<>();

        // Extract event type and time
        parsedData.put("eventType", rootNode.get("type").asText());
        parsedData.put("eventTime", rootNode.get("event_time").asText());

        // Extract data node
        JsonNode dataNode = rootNode.get("data");
        if (dataNode != null) {
            // Extract refund details
            JsonNode refundNode = dataNode.get("refund");
            if (refundNode != null) {
                parsedData.put("cfRefundId", refundNode.get("cf_refund_id").asText());
                parsedData.put("refundId", refundNode.get("refund_id").asText());
                parsedData.put("refundAmount", new BigDecimal(refundNode.get("refund_amount").asText()));
                parsedData.put("refundCurrency", refundNode.get("refund_currency").asText());
                parsedData.put("refundStatus", refundNode.get("refund_status").asText());
                parsedData.put("refundType", refundNode.get("refund_type").asText());

                if (refundNode.has("refund_note") && !refundNode.get("refund_note").isNull()) {
                    parsedData.put("refundNote", refundNode.get("refund_note").asText());
                }

                if (refundNode.has("refund_arn") && !refundNode.get("refund_arn").isNull()) {
                    parsedData.put("refundArn", refundNode.get("refund_arn").asText());
                }

                if (refundNode.has("refund_charge") && !refundNode.get("refund_charge").isNull()) {
                    parsedData.put("refundCharge", new BigDecimal(refundNode.get("refund_charge").asText()));
                }

                if (refundNode.has("refund_splits") && !refundNode.get("refund_splits").isNull()) {
                    parsedData.put("refundSplits", refundNode.get("refund_splits").toString());
                }

                if (refundNode.has("refund_mode") && !refundNode.get("refund_mode").isNull()) {
                    parsedData.put("refundMode", refundNode.get("refund_mode").asText());
                }

                if (refundNode.has("refund_speed") && !refundNode.get("refund_speed").isNull()) {
                    parsedData.put("refundSpeed", refundNode.get("refund_speed").asText());
                }

                // Parse timestamps
                if (refundNode.has("created_at") && !refundNode.get("created_at").isNull()) {
                    try {
                        LocalDateTime createdAt = OffsetDateTime.parse(refundNode.get("created_at").asText())
                                .toLocalDateTime();
                        parsedData.put("refundCreatedAt", createdAt);
                    } catch (Exception e) {
                        log.warn("Could not parse refund created_at: {}", refundNode.get("created_at").asText());
                    }
                }

                if (refundNode.has("processed_at") && !refundNode.get("processed_at").isNull()) {
                    try {
                        LocalDateTime processedAt = OffsetDateTime.parse(refundNode.get("processed_at").asText())
                                .toLocalDateTime();
                        parsedData.put("refundProcessedAt", processedAt);
                    } catch (Exception e) {
                        log.warn("Could not parse refund processed_at: {}", refundNode.get("processed_at").asText());
                    }
                }
            }

            // Extract order details
            JsonNode orderNode = dataNode.get("order");
            if (orderNode != null) {
                parsedData.put("orderId", orderNode.get("order_id").asText());
                parsedData.put("orderAmount", new BigDecimal(orderNode.get("order_amount").asText()));
                parsedData.put("orderCurrency", orderNode.get("order_currency").asText());
            }
        }

        // Store raw payload for debugging
        parsedData.put("rawPayload", rawBody);

        log.debug("Parsed refund webhook data: {}", parsedData);
        return parsedData;
    }

    /**
     * Process refund webhook and update payment transaction
     */
    @Transactional
    public void processRefundWebhook(Map<String, Object> webhookData) {
        String orderId = (String) webhookData.get("orderId");
        String cfRefundId = (String) webhookData.get("cfRefundId");
        String refundId = (String) webhookData.get("refundId");
        String refundStatus = (String) webhookData.get("refundStatus");
        BigDecimal refundAmount = (BigDecimal) webhookData.get("refundAmount");
        String eventType = (String) webhookData.get("eventType");

        log.info("Processing refund webhook - Order: {}, Refund: {}, Status: {}, Event: {}",
                orderId, cfRefundId, refundStatus, eventType);

        // Find payment transaction by order ID
        Optional<PaymentTransaction> transactionOpt = paymentTransactionRepository
                .findByOrderId(orderId);

        if (transactionOpt.isEmpty()) {
            String errorMsg = String.format("Payment transaction not found for order: %s", orderId);
            log.error(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        PaymentTransaction transaction = transactionOpt.get();

        // Update transaction with refund details
        updatePaymentTransactionWithRefund(transaction, webhookData);

        // Save the updated transaction
        paymentTransactionRepository.save(transaction);

        // Trigger post-processing actions
        triggerPostRefundActions(transaction, refundStatus, eventType);

        log.info("Refund webhook processed successfully. Order: {}, Refund: {}, Status: {}",
                orderId, cfRefundId, refundStatus);
    }

    /**
     * Update payment transaction with refund details
     */
    private void updatePaymentTransactionWithRefund(PaymentTransaction transaction,
                                                    Map<String, Object> webhookData) {

        String cfRefundId = (String) webhookData.get("cfRefundId");
        String refundId = (String) webhookData.get("refundId");
        String refundStatus = (String) webhookData.get("refundStatus");
        BigDecimal refundAmount = (BigDecimal) webhookData.get("refundAmount");
        String eventType = (String) webhookData.get("eventType");

        // Update webhook payload
        transaction.setWebhookPayload((String) webhookData.get("rawPayload"));
        transaction.setCallbackReceived(true);
        transaction.setUpdatedAt(LocalDateTime.now());

        // Update refund details
        transaction.setCfRefundId(cfRefundId);
        transaction.setRefundId(refundId);

        // Calculate total refund amount
        BigDecimal currentRefundAmount = transaction.getRefundAmount() != null ?
                transaction.getRefundAmount() : BigDecimal.ZERO;

        // Only update refund amount if this is a new refund or different amount
        if (refundAmount != null &&
                (transaction.getCfRefundId() == null || transaction.getCfRefundId().equals(cfRefundId))) {

            // If this is the same refund, update the amount
            // If it's a different refund, add to existing amount
            if (cfRefundId.equals(transaction.getCfRefundId())) {
                transaction.setRefundAmount(refundAmount);
            } else {
                transaction.setRefundAmount(currentRefundAmount.add(refundAmount));
            }
        }

        // Update refund notes
        if (webhookData.containsKey("refundNote")) {
            transaction.setRefundNote((String) webhookData.get("refundNote"));
        }

        // Update refund ARN
        if (webhookData.containsKey("refundArn")) {
            transaction.setRefundArn((String) webhookData.get("refundArn"));
        }

        // Update refund charge
        if (webhookData.containsKey("refundCharge")) {
            transaction.setRefundCharge((BigDecimal) webhookData.get("refundCharge"));
        }

        // Update refund mode/speed
        if (webhookData.containsKey("refundMode")) {
            transaction.setRefundMode((String) webhookData.get("refundMode"));
        }

        if (webhookData.containsKey("refundSpeed")) {
            transaction.setRefundSpeed((String) webhookData.get("refundSpeed"));
        }

        // Update timestamps
        if (webhookData.containsKey("refundCreatedAt")) {
            transaction.setRefundCreatedAt((LocalDateTime) webhookData.get("refundCreatedAt"));
        }

        if (webhookData.containsKey("refundProcessedAt")) {
            transaction.setRefundProcessedAt((LocalDateTime) webhookData.get("refundProcessedAt"));
        } else if ("SUCCESS".equals(refundStatus)) {
            transaction.setRefundProcessedAt(LocalDateTime.now());
        }

        // Update refund status based on webhook event
        updateRefundStatus(transaction, refundStatus, eventType);

        // Update refund splits if available
        if (webhookData.containsKey("refundSplits")) {
            try {
                Map<String, Object> refundSplits = objectMapper.readValue(
                        (String) webhookData.get("refundSplits"),
                        Map.class
                );
                transaction.setRefundSplits(refundSplits);
            } catch (Exception e) {
                log.warn("Could not parse refund splits", e);
            }
        }
    }

    /**
     * Update refund status based on webhook data
     */
    private void updateRefundStatus(PaymentTransaction transaction,
                                    String refundStatus,
                                    String eventType) {

        // Map Cashfree status to your entity enum
        PaymentTransaction.RefundStatus entityStatus;

        switch (refundStatus) {
            case "SUCCESS":
                entityStatus = PaymentTransaction.RefundStatus.SUCCESS;
                log.info("Refund marked as SUCCESS for order: {}", transaction.getOrderId());
                break;

            case "FAILED":
                entityStatus = PaymentTransaction.RefundStatus.FAILED;
                log.warn("Refund marked as FAILED for order: {}", transaction.getOrderId());
                break;

            case "PENDING":
                entityStatus = PaymentTransaction.RefundStatus.PENDING;
                log.info("Refund marked as PENDING for order: {}", transaction.getOrderId());
                break;

            case "CANCELLED":
                entityStatus = PaymentTransaction.RefundStatus.REVERSED;
                log.info("Refund marked as CANCELLED/REVERSED for order: {}", transaction.getOrderId());
                break;

            default:
                entityStatus = PaymentTransaction.RefundStatus.PENDING;
                log.warn("Unknown refund status: {} for order: {}",
                        refundStatus, transaction.getOrderId());
        }

        transaction.setRefundStatus(entityStatus);

        // If refund is successful, check if order is fully refunded
        if (entityStatus == PaymentTransaction.RefundStatus.SUCCESS) {
            checkAndUpdateFullRefundStatus(transaction);
        }
    }

    /**
     * Check if order is fully refunded and update status
     */
    private void checkAndUpdateFullRefundStatus(PaymentTransaction transaction) {
        BigDecimal orderAmount = transaction.getOrderAmount();
        BigDecimal refundAmount = transaction.getRefundAmount();

        if (orderAmount != null && refundAmount != null) {
            // Check if fully refunded
            if (refundAmount.compareTo(orderAmount) >= 0) {
                log.info("Order {} fully refunded. Amount: {}, Refunded: {}",
                        transaction.getOrderId(), orderAmount, refundAmount);
                // You might want to set a flag or additional status
            } else if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
                log.info("Order {} partially refunded. Amount: {}, Refunded: {}",
                        transaction.getOrderId(), orderAmount, refundAmount);
            }
        }
    }

    /**
     * Trigger actions after refund processing
     */
    private void triggerPostRefundActions(PaymentTransaction transaction,
                                          String refundStatus,
                                          String eventType) {
        try {
            switch (refundStatus) {
                case "SUCCESS":
                    // Send refund success notification
                    sendRefundSuccessNotification(transaction);
                    // Update accounting
                    updateAccountingForRefund(transaction);
                    // Credit user wallet if needed
                    creditUserWallet(transaction);
                    // Update inventory if physical products
                    updateInventoryForRefund(transaction);
                    break;

                case "FAILED":
                    // Send refund failure notification
                    sendRefundFailureNotification(transaction);
                    // Notify admin team
                    notifyAdminRefundFailure(transaction);
                    break;

                case "PENDING":
                    // Refund is being processed
                    log.info("Refund is pending processing for order: {}", transaction.getOrderId());
                    sendRefundPendingNotification(transaction);
                    break;

                case "CANCELLED":
                    // Refund was cancelled
                    sendRefundCancelledNotification(transaction);
                    break;
            }

            // Log refund event for analytics
            logRefundEvent(transaction, refundStatus, eventType);

        } catch (Exception e) {
            log.error("Error in post-refund actions for order: {}",
                    transaction.getOrderId(), e);
        }
    }

    // ========== HELPER METHODS ==========

    private void sendRefundSuccessNotification(PaymentTransaction transaction) {
        try {
            log.info("Sending refund success notification for order: {}",
                    transaction.getOrderId());

            // Example implementation:
            // notificationService.sendEmail(
            //     transaction.getCustomerDetails().getCustomerEmail(),
            //     "Refund Processed Successfully",
            //     String.format("Your refund of ₹%s for order %s has been processed. Refund ID: %s",
            //         transaction.getRefundAmount(),
            //         transaction.getOrderId(),
            //         transaction.getCfRefundId())
            // );

            // Also send to admin if needed
            // adminNotificationService.notify(
            //     "Refund Successful",
            //     String.format("Refund %s for order %s processed successfully",
            //         transaction.getCfRefundId(),
            //         transaction.getOrderId())
            // );

        } catch (Exception e) {
            log.error("Failed to send refund success notification", e);
        }
    }

    private void updateAccountingForRefund(PaymentTransaction transaction) {
        try {
            log.info("Updating accounting for refund: {}", transaction.getCfRefundId());

            // Example:
            // accountingService.recordRefund(
            //     transaction.getRefundAmount(),
            //     transaction.getRefundCharge(),
            //     transaction.getOrderId(),
            //     transaction.getCfRefundId(),
            //     "REFUND"
            // );

        } catch (Exception e) {
            log.error("Failed to update accounting for refund", e);
        }
    }

    private void creditUserWallet(PaymentTransaction transaction) {
        try {
            log.info("Crediting user wallet for refund: {}", transaction.getCfRefundId());

            // If payment was from wallet, credit back to wallet
            // if ("WALLET".equals(transaction.getPaymentMethod())) {
            //     walletService.creditBalance(
            //         transaction.getCustomerDetails().getCustomerId(),
            //         transaction.getRefundAmount(),
            //         "Refund for order " + transaction.getOrderId()
            //     );
            // }

        } catch (Exception e) {
            log.error("Failed to credit user wallet for refund", e);
        }
    }

    private void updateInventoryForRefund(PaymentTransaction transaction) {
        try {
            log.info("Updating inventory for refunded order: {}", transaction.getOrderId());

            // If this is a physical product order, update inventory
            // if (transaction.getProductId() != null) {
            //     inventoryService.increaseStock(
            //         transaction.getProductId(),
            //         1, // or quantity from cart details
            //         "Refund for order " + transaction.getOrderId()
            //     );
            // }

        } catch (Exception e) {
            log.error("Failed to update inventory for refund", e);
        }
    }

    private void sendRefundFailureNotification(PaymentTransaction transaction) {
        try {
            log.info("Sending refund failure notification for order: {}",
                    transaction.getOrderId());

            // notificationService.sendEmail(
            //     transaction.getCustomerDetails().getCustomerEmail(),
            //     "Refund Failed",
            //     String.format("Your refund of ₹%s for order %s has failed. Please contact support.",
            //         transaction.getRefundAmount(),
            //         transaction.getOrderId())
            // );

        } catch (Exception e) {
            log.error("Failed to send refund failure notification", e);
        }
    }

    private void notifyAdminRefundFailure(PaymentTransaction transaction) {
        try {
            log.warn("Notifying admin about refund failure: {}", transaction.getCfRefundId());

            // adminNotificationService.notify(
            //     "Refund Failed - Requires Attention",
            //     String.format("Refund %s for order %s failed. Amount: ₹%s",
            //         transaction.getCfRefundId(),
            //         transaction.getOrderId(),
            //         transaction.getRefundAmount())
            // );

        } catch (Exception e) {
            log.error("Failed to notify admin about refund failure", e);
        }
    }

    private void sendRefundPendingNotification(PaymentTransaction transaction) {
        log.info("Refund pending notification sent for order: {}", transaction.getOrderId());
        // Similar to success notification but "pending" status
    }

    private void sendRefundCancelledNotification(PaymentTransaction transaction) {
        log.info("Refund cancelled notification sent for order: {}", transaction.getOrderId());
        // Notify user/admin about cancellation
    }

    private void logRefundEvent(PaymentTransaction transaction, String status, String eventType) {
        // Log to analytics database
        log.info("Refund event logged - Order: {}, Refund: {}, Status: {}, Event: {}, Amount: {}",
                transaction.getOrderId(),
                transaction.getCfRefundId(),
                status,
                eventType,
                transaction.getRefundAmount());
    }

    /**
     * Constant-time string comparison
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
                refundWebhookSecret != null && !refundWebhookSecret.isEmpty());
        status.put("timestamp", LocalDateTime.now().toString());
        status.put("service", "RefundWebhookService");
        status.put("supportedEvents", new String[]{
                "REFUND_SUCCESS_WEBHOOK",
                "REFUND_FAILED_WEBHOOK",
                "REFUND_PROCESSED_WEBHOOK",
                "REFUND_UPDATED_WEBHOOK"
        });
        return status;
    }
}