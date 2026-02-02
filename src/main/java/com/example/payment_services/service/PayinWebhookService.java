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
public class PayinWebhookService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ObjectMapper objectMapper;

    @Value("${cashfree.webhook.secret:}")
    private String webhookSecret;

    /**
     * Verify webhook signature
     */
    public boolean verifyWebhookSignature(String rawBody, String signature, String timestamp) {
        try {
            if (webhookSecret == null || webhookSecret.isEmpty()) {
                log.error("Webhook secret not configured");
                return false;
            }

            String data = timestamp + rawBody;
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            sha256_HMAC.init(secretKeySpec);

            String computedSignature = Base64.getEncoder().encodeToString(
                    sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8))
            );

            return constantTimeEquals(computedSignature, signature);

        } catch (Exception e) {
            log.error("Error verifying webhook signature", e);
            return false;
        }
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
     * Parse webhook payload
     */
    public Map<String, Object> parseWebhookPayload(String rawBody) throws Exception {
        JsonNode rootNode = objectMapper.readTree(rawBody);
        JsonNode dataNode = rootNode.get("data");
        JsonNode orderNode = dataNode.get("order");
        JsonNode paymentNode = dataNode.get("payment");

        Map<String, Object> parsedData = new HashMap<>();

        // Basic information
        parsedData.put("eventType", rootNode.get("type").asText());
        parsedData.put("eventTime", rootNode.get("event_time").asText());

        // Order details
        parsedData.put("orderId", orderNode.get("order_id").asText());
        parsedData.put("orderAmount", new BigDecimal(orderNode.get("order_amount").asText()));
        parsedData.put("orderCurrency", orderNode.get("order_currency").asText());

        // Payment details
        parsedData.put("cfPaymentId", paymentNode.get("cf_payment_id").asText());
        parsedData.put("paymentStatus", paymentNode.get("payment_status").asText());

        BigDecimal paymentAmount = paymentNode.has("payment_amount") ?
                new BigDecimal(paymentNode.get("payment_amount").asText()) :
                new BigDecimal(orderNode.get("order_amount").asText());
        parsedData.put("paymentAmount", paymentAmount);

        parsedData.put("paymentMessage", paymentNode.has("payment_message") ?
                paymentNode.get("payment_message").asText() : "");

        // Parse payment time
        if (paymentNode.has("payment_time")) {
            try {
                LocalDateTime paymentTime = OffsetDateTime
                        .parse(paymentNode.get("payment_time").asText())
                        .toLocalDateTime();
                parsedData.put("paymentTime", paymentTime);
            } catch (Exception e) {
                log.warn("Could not parse payment time");
                parsedData.put("paymentTime", LocalDateTime.now());
            }
        } else {
            parsedData.put("paymentTime", LocalDateTime.now());
        }

        // Additional payment details
        if (paymentNode.has("bank_reference")) {
            parsedData.put("bankReference", paymentNode.get("bank_reference").asText());
        }
        if (paymentNode.has("payment_group")) {
            parsedData.put("paymentGroup", paymentNode.get("payment_group").asText());
        }
        if (paymentNode.has("payment_method")) {
            parsedData.put("paymentMethod", paymentNode.get("payment_method").asText());
        }

        // Raw payload for storage
        parsedData.put("rawPayload", rawBody);

        return parsedData;
    }

    /**
     * Process webhook and update payment status
     */
    @Transactional
    public void processPaymentWebhook(Map<String, Object> webhookData) {
        String orderId = (String) webhookData.get("orderId");
        String paymentStatus = (String) webhookData.get("paymentStatus");
        String cfPaymentId = (String) webhookData.get("cfPaymentId");
        BigDecimal paymentAmount = (BigDecimal) webhookData.get("paymentAmount");
        LocalDateTime paymentTime = (LocalDateTime) webhookData.get("paymentTime");
        String paymentMessage = (String) webhookData.get("paymentMessage");
        String rawPayload = (String) webhookData.get("rawPayload");

        log.info("Processing webhook for order: {}, status: {}", orderId, paymentStatus);

        // Find the payment transaction
        Optional<PaymentTransaction> transactionOpt =
                paymentTransactionRepository.findByOrderId(orderId);

        if (transactionOpt.isEmpty()) {
            log.error("Payment transaction not found for order: {}", orderId);
            throw new IllegalArgumentException("Order not found: " + orderId);
        }

        PaymentTransaction transaction = transactionOpt.get();

        // Update transaction with webhook data
        updateTransactionFromWebhook(transaction, webhookData);

        // Save the updated transaction
        paymentTransactionRepository.save(transaction);

        log.info("Successfully updated payment status for order: {} to {}",
                orderId, paymentStatus);

        // Trigger post-processing actions
        triggerPostPaymentActions(orderId, paymentStatus, paymentMessage);
    }

    /**
     * Update transaction entity with webhook data
     */
    private void updateTransactionFromWebhook(PaymentTransaction transaction,
                                              Map<String, Object> webhookData) {

        String paymentStatus = (String) webhookData.get("paymentStatus");
        String cfPaymentId = (String) webhookData.get("cfPaymentId");
        BigDecimal paymentAmount = (BigDecimal) webhookData.get("paymentAmount");
        LocalDateTime paymentTime = (LocalDateTime) webhookData.get("paymentTime");
        String rawPayload = (String) webhookData.get("rawPayload");

        // Set basic payment details
        transaction.setCfPaymentId(cfPaymentId);
        transaction.setPaymentAmount(paymentAmount);
        transaction.setPaymentProcessedAt(paymentTime);
        transaction.setWebhookPayload(rawPayload);
        transaction.setCallbackReceived(true);
        transaction.setUpdatedAt(LocalDateTime.now());

        // Set payment method if available
        if (webhookData.containsKey("paymentMethod")) {
            transaction.setPaymentMethod((String) webhookData.get("paymentMethod"));
        }

        // Set bank reference if available
        if (webhookData.containsKey("bankReference")) {
            transaction.setBankReference((String) webhookData.get("bankReference"));
        }

        // Update status based on payment status
        switch (paymentStatus) {
            case "SUCCESS":
                transaction.setPaymentStatus(PaymentTransaction.PaymentStatus.SUCCESS);
                transaction.setOrderStatus(PaymentTransaction.OrderStatus.PAID);
                transaction.setSettlementAmount(paymentAmount);
                transaction.setSettlementDate(LocalDateTime.now().plusDays(2)); // T+2 settlement
                break;

            case "FAILED":
                transaction.setPaymentStatus(PaymentTransaction.PaymentStatus.FAILED);
                transaction.setOrderStatus(PaymentTransaction.OrderStatus.FAILED);
                break;

            case "USER_DROPPED":
                transaction.setPaymentStatus(PaymentTransaction.PaymentStatus.CANCELLED);
                transaction.setOrderStatus(PaymentTransaction.OrderStatus.TERMINATED);
                break;

            default:
                log.warn("Unknown payment status: {}", paymentStatus);
                transaction.setPaymentStatus(PaymentTransaction.PaymentStatus.PENDING);
        }
    }

    /**
     * Trigger post-payment actions
     */
    private void triggerPostPaymentActions(String orderId, String paymentStatus,
                                           String paymentMessage) {
        try {
            switch (paymentStatus) {
                case "SUCCESS":
                    // These would be implemented as separate services
                    // sendConfirmationEmail(orderId);
                    // updateInventory(orderId);
                    // generateInvoice(orderId);
                    log.info("Post-payment actions triggered for successful order: {}", orderId);
                    break;

                case "FAILED":
                    // sendFailureNotification(orderId, paymentMessage);
                    log.warn("Payment failed for order: {} - {}", orderId, paymentMessage);
                    break;

                case "USER_DROPPED":
                    // sendAbandonedCartEmail(orderId);
                    log.info("User dropped payment for order: {}", orderId);
                    break;
            }
        } catch (Exception e) {
            log.error("Error in post-payment actions for order: {}", orderId, e);
        }
    }

    /**
     * Get webhook configuration status
     */
    public Map<String, Object> getWebhookConfigStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("webhookSecretConfigured", webhookSecret != null && !webhookSecret.isEmpty());
        status.put("timestamp", LocalDateTime.now().toString());
        status.put("service", "PayinWebhookService");
        return status;
    }
}