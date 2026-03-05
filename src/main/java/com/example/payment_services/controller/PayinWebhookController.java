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
import java.time.format.DateTimeFormatter;
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

        try {
            // Read request body
            rawBody = readRequestBody(request);

            // Check if this is a test request (empty body or test headers)
            if (isTestRequest(request, rawBody)) {
                log.info("Test webhook detected - returning OK");
                return ResponseEntity.ok("OK");
            }

            log.info("Processing actual payment webhook");

            // Get signature headers
            String signature = request.getHeader("x-webhook-signature");
            String timestamp = request.getHeader("x-webhook-timestamp");

            // Validate headers
            if (signature == null || signature.isEmpty() || timestamp == null || timestamp.isEmpty()) {
                log.error("Missing signature headers");
                return ResponseEntity.badRequest().body("Missing signature headers");
            }

            // Verify signature
            if (!payinWebhookService.verifyWebhookSignature(rawBody, signature, timestamp)) {
                log.error("Invalid webhook signature");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
            }

            // Parse and process webhook (this will update the database)
            Map<String, Object> webhookData = payinWebhookService.parseWebhookPayload(rawBody);
            payinWebhookService.processPaymentWebhook(webhookData);

            log.info("Webhook processed successfully for order: {}", webhookData.get("orderId"));

            // Return success response
            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            log.error("Error processing webhook", e);

            // Always return 200 OK to prevent Cashfree from retrying
            // But log the error for investigation
            return ResponseEntity.ok("OK");
        }
    }

    /**
     * Check if this is a test request
     */
    private boolean isTestRequest(HttpServletRequest request, String rawBody) {
        // Check for empty body
        if (rawBody == null || rawBody.trim().isEmpty()) {
            return true;
        }

        // Check for test headers (Cashfree might send test indicators)
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && userAgent.contains("test")) {
            return true;
        }

        // Check if body contains test indicators
        if (rawBody.contains("test") || rawBody.contains("ping")) {
            return true;
        }

        return false;
    }

    @GetMapping("/cashfree/payment")
    @Operation(summary = "Handle GET requests for webhook testing")
    public ResponseEntity<String> handleCashfreePaymentWebhookGet() {
        log.info("GET request received on webhook endpoint");
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/cashfree/payment/health")
    @Operation(summary = "Webhook Health Check")
    public ResponseEntity<Map<String, Object>> webhookHealthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "healthy");
        response.put("service", "PayinWebhookService");
        response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        response.put("config", payinWebhookService.getWebhookConfigStatus());
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