package com.example.payment_services.controller;

import com.example.payment_services.service.PayinWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment Webhooks", description = "APIs for receiving payment status updates")
public class PayinWebhookController {

    private final PayinWebhookService payinWebhookService;

    @PostMapping("/cashfree/payment")
    @Operation(summary = "Handle Cashfree payment webhook")
    public ResponseEntity<?> handleCashfreePaymentWebhook(HttpServletRequest request) {
        String rawBody = null;
        Map<String, Object> response = new HashMap<>();

        try {
            // 1. Read request body
            rawBody = readRequestBody(request);

            // 2. Handle empty body (test requests)
            if (rawBody == null || rawBody.trim().isEmpty()) {
                log.info("Received empty webhook request - test ping");
                response.put("status", "success");
                response.put("message", "Webhook endpoint is ready");
                response.put("timestamp", LocalDateTime.now().toString());
                return ResponseEntity.ok(response);
            }

            log.debug("Cashfree webhook received, body length: {} chars", rawBody.length());

            // 3. Get signature headers
            String signature = request.getHeader("x-webhook-signature");
            String timestamp = request.getHeader("x-webhook-timestamp");

            // 4. Validate headers - but allow test requests without signatures
            if (signature == null || signature.isEmpty() || timestamp == null || timestamp.isEmpty()) {
                // This might be a test request without headers
                log.info("Received webhook without signature headers - processing as test");

                // Try to parse and process anyway if possible
                try {
                    Map<String, Object> webhookData = payinWebhookService.parseWebhookPayload(rawBody);
                    payinWebhookService.processPaymentWebhook(webhookData);

                    response.put("status", "success");
                    response.put("message", "Webhook processed successfully (test mode)");
                    response.put("order_id", webhookData.get("orderId"));
                    response.put("payment_status", webhookData.get("paymentStatus"));
                } catch (Exception e) {
                    log.warn("Could not process test webhook data: {}", e.getMessage());
                    response.put("status", "success");
                    response.put("message", "Webhook received but processing skipped (test mode)");
                }

                response.put("timestamp", LocalDateTime.now().toString());
                return ResponseEntity.ok(response);
            }

            // 5. Verify signature using your service
            if (!payinWebhookService.verifyWebhookSignature(rawBody, signature, timestamp)) {
                response.put("status", "error");
                response.put("message", "Invalid webhook signature");
                response.put("timestamp", LocalDateTime.now().toString());
                log.error("Invalid webhook signature");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            // 6. Parse and process webhook
            Map<String, Object> webhookData = payinWebhookService.parseWebhookPayload(rawBody);
            payinWebhookService.processPaymentWebhook(webhookData);

            // 7. Return success response
            response.put("status", "success");
            response.put("message", "Webhook processed successfully");
            response.put("order_id", webhookData.get("orderId"));
            response.put("payment_status", webhookData.get("paymentStatus"));
            response.put("cf_payment_id", webhookData.get("cfPaymentId"));
            response.put("timestamp", LocalDateTime.now().toString());

            log.info("Webhook processed successfully for order: {}", webhookData.get("orderId"));
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing webhook", e);
            response.put("status", "error");
            response.put("message", "Error processing webhook");
            response.put("error", e.getMessage());
            response.put("timestamp", LocalDateTime.now().toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/cashfree/payment")
    @Operation(summary = "Handle GET requests for webhook testing")
    public ResponseEntity<?> handleCashfreePaymentWebhookGet() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Webhook endpoint is ready");
        response.put("method", "GET");
        response.put("service", "PayinWebhookService");
        response.put("timestamp", LocalDateTime.now().toString());

        // Add config status from service
        response.put("config", payinWebhookService.getWebhookConfigStatus());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/cashfree/payment/health")
    @Operation(summary = "Webhook Health Check")
    public ResponseEntity<?> webhookHealthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "healthy");
        response.put("service", "PayinWebhookService");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("config", payinWebhookService.getWebhookConfigStatus());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/cashfree/payment/test")
    @Operation(summary = "Test endpoint for webhook")
    public ResponseEntity<?> testWebhook(@RequestBody(required = false) String testPayload) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Test endpoint working");
        response.put("payload_received", testPayload != null && !testPayload.isEmpty());
        response.put("config", payinWebhookService.getWebhookConfigStatus());
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }

    /**
     * Helper method to read request body
     */
    private String readRequestBody(HttpServletRequest request) throws IOException {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }
        return body.toString();
    }
}