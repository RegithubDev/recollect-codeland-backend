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
    public ResponseEntity<?> handleCashfreePaymentWebhook(HttpServletRequest request) {

        String rawBody = null;
        Map<String, Object> response = new HashMap<>();

        try {
            // 1. Read request body
            rawBody = readRequestBody(request);
            log.debug("Cashfree webhook received, body length: {} chars", rawBody.length());

            // 2. Get signature headers
            String signature = request.getHeader("x-webhook-signature");
            String timestamp = request.getHeader("x-webhook-timestamp");

            // 3. Validate headers
            if (signature == null || signature.isEmpty()) {
                response.put("status", "error");
                response.put("message", "Missing x-webhook-signature header");
                response.put("timestamp", LocalDateTime.now().toString());
                log.error("Missing signature header");
                return ResponseEntity.badRequest().body(response);
            }

            if (timestamp == null || timestamp.isEmpty()) {
                response.put("status", "error");
                response.put("message", "Missing x-webhook-timestamp header");
                response.put("timestamp", LocalDateTime.now().toString());
                log.error("Missing timestamp header");
                return ResponseEntity.badRequest().body(response);
            }

            // 4. Verify signature
            if (!payinWebhookService.verifyWebhookSignature(rawBody, signature, timestamp)) {
                response.put("status", "error");
                response.put("message", "Invalid webhook signature");
                response.put("timestamp", LocalDateTime.now().toString());
                log.error("Invalid webhook signature");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            // 5. Parse and process webhook
            Map<String, Object> webhookData = payinWebhookService.parseWebhookPayload(rawBody);
            payinWebhookService.processPaymentWebhook(webhookData);

            String orderId = (String) webhookData.get("orderId");
            String paymentStatus = (String) webhookData.get("paymentStatus");

            // 6. Return success response
            response.put("status", "success");
            response.put("message", "Webhook processed successfully");
            response.put("order_id", orderId);
            response.put("payment_status", paymentStatus);
            response.put("timestamp", LocalDateTime.now().toString());

            log.info("Webhook processed successfully for order: {}", orderId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error processing webhook");
            response.put("error", e.getMessage());
            response.put("timestamp", LocalDateTime.now().toString());
            log.error("Error processing webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @Operation(summary = "Webhook Health Check")
    @GetMapping("/cashfree/payment/health")
    public ResponseEntity<?> webhookHealthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "healthy");
        response.put("service", "PayinWebhookService");
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