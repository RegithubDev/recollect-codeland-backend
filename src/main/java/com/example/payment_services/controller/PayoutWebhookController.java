package com.example.payment_services.controller;

import com.example.payment_services.service.PayoutWebhookService;
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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payout Webhooks", description = "APIs for receiving payout status updates")
public class PayoutWebhookController {

    private final PayoutWebhookService payoutWebhookService;

    @Operation(summary = "Cashfree Payout Webhook")
    @PostMapping("/cashfree/payout")
    public ResponseEntity<?> handleCashfreePayoutWebhook(HttpServletRequest request) {
        String rawBody = null;

        try {
            // 1. Read request body
            rawBody = readRequestBody(request);

            // Log request details for debugging
            log.info("========== PAYOUT WEBHOOK RECEIVED ==========");
            log.info("Method: {}, Content-Type: {}", request.getMethod(), request.getContentType());

            // 2. Get signature headers (Cashfree sends x-webhook-signature, not x-cf-signature)
            String signature = request.getHeader("x-webhook-signature");
            String timestamp = request.getHeader("x-webhook-timestamp");

            // 3. If signature present, process as real webhook
            if (signature != null && !signature.isEmpty()) {
                log.info("Signature header found, processing real webhook");

                // Verify signature
                if (!payoutWebhookService.verifyPayoutSignature(rawBody, signature)) {
                    log.error("Invalid payout webhook signature");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
                }
                log.info("Signature verified successfully");

                // Parse and process payout webhook
                Map<String, Object> webhookData = payoutWebhookService.parsePayoutWebhookPayload(rawBody);

                // Check if it's a test webhook from the service
                if (webhookData.containsKey("isTest") && (Boolean) webhookData.get("isTest")) {
                    log.info("Test webhook detected by service - returning OK");
                    return ResponseEntity.ok("OK");
                }

                // Process real webhook
                payoutWebhookService.processPayoutWebhook(webhookData);

                log.info("Payout webhook processed successfully for transfer: {}",
                        webhookData.get("transferId"));
                return ResponseEntity.ok("OK");
            }

            // 4. No signature - treat as test
            log.warn("Missing signature header - treating as test request");
            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            log.error("Error processing payout webhook: {}", e.getMessage(), e);
            // Always return OK to prevent retries
            return ResponseEntity.ok("OK");
        }
    }

    @Operation(summary = "Handle GET requests for payout webhook testing")
    @GetMapping("/cashfree/payout")
    public ResponseEntity<String> handleCashfreePayoutWebhookGet() {
        log.info("GET request received on payout webhook endpoint");
        return ResponseEntity.ok("OK");
    }

    @Operation(summary = "Payout Webhook Health Check")
    @GetMapping("/cashfree/payout/health")
    public ResponseEntity<Map<String, Object>> payoutWebhookHealthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "healthy");
        response.put("service", "PayoutWebhookService");
        response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        response.put("config", payoutWebhookService.getWebhookConfigStatus());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Test endpoint for payout webhook")
    @PostMapping("/cashfree/payout/test")
    public ResponseEntity<String> testPayoutWebhook() {
        log.info("Test endpoint called");
        return ResponseEntity.ok("OK");
    }

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