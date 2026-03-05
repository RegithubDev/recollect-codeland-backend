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
        Map<String, Object> response = new HashMap<>();

        try {
            // 1. Read request body
            rawBody = readRequestBody(request);

            // 2. Handle empty body (test requests)
            if (rawBody == null || rawBody.trim().isEmpty()) {
                log.info("Received empty payout webhook request - test ping");
                response.put("status", "success");
                response.put("message", "Payout webhook endpoint is ready");
                response.put("timestamp", LocalDateTime.now().toString());
                return ResponseEntity.ok(response);
            }

            log.debug("Cashfree payout webhook received, length: {} chars", rawBody.length());

            // 3. Get signature headers (Cashfree Payout uses different headers)
            String signature = request.getHeader("x-cf-signature");
            String timestamp = request.getHeader("x-cf-timestamp");

            // 4. For test requests without signatures, still accept
            if (signature == null || signature.isEmpty()) {
                log.info("Received payout webhook without signature headers - processing as test");

                // Try to parse and process anyway if possible
                try {
                    Map<String, Object> webhookData = payoutWebhookService.parsePayoutWebhookPayload(rawBody);
                    payoutWebhookService.processPayoutWebhook(webhookData);

                    response.put("status", "success");
                    response.put("message", "Payout webhook processed successfully (test mode)");
                    response.put("transfer_id", webhookData.get("transferId"));
                    response.put("event_type", webhookData.get("eventType"));
                } catch (Exception e) {
                    log.warn("Could not process test payout webhook data: {}", e.getMessage());
                    response.put("status", "success");
                    response.put("message", "Payout webhook received but processing skipped (test mode)");
                }

                response.put("timestamp", LocalDateTime.now().toString());
                return ResponseEntity.ok(response);
            }

            // 5. Verify signature (Cashfree Payout has different signature format)
            if (!payoutWebhookService.verifyPayoutSignature(rawBody, signature)) {
                response.put("status", "error");
                response.put("message", "Invalid payout webhook signature");
                response.put("timestamp", LocalDateTime.now().toString());
                log.error("Invalid payout webhook signature");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            // 6. Parse and process payout webhook
            Map<String, Object> webhookData = payoutWebhookService.parsePayoutWebhookPayload(rawBody);
            payoutWebhookService.processPayoutWebhook(webhookData);

            String transferId = (String) webhookData.get("transferId");
            String eventType = (String) webhookData.get("eventType");

            // 7. Return success response
            response.put("status", "success");
            response.put("message", "Payout webhook processed successfully");
            response.put("transfer_id", transferId);
            response.put("event_type", eventType);
            response.put("timestamp", LocalDateTime.now().toString());

            log.info("Payout webhook processed successfully: {} - {}", transferId, eventType);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing payout webhook", e);
            response.put("status", "error");
            response.put("message", "Error processing payout webhook");
            response.put("error", e.getMessage());
            response.put("timestamp", LocalDateTime.now().toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @Operation(summary = "Handle GET requests for payout webhook testing")
    @GetMapping("/cashfree/payout")
    public ResponseEntity<?> handleCashfreePayoutWebhookGet() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Payout webhook endpoint is ready");
        response.put("method", "GET");
        response.put("service", "PayoutWebhookService");
        response.put("config", payoutWebhookService.getWebhookConfigStatus());
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Payout Webhook Health Check")
    @GetMapping("/cashfree/payout/health")
    public ResponseEntity<?> payoutWebhookHealthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "healthy");
        response.put("service", "PayoutWebhookService");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("config", payoutWebhookService.getWebhookConfigStatus());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Test endpoint for payout webhook")
    @PostMapping("/cashfree/payout/test")
    public ResponseEntity<?> testPayoutWebhook(@RequestBody(required = false) String testPayload) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Payout test endpoint working");
        response.put("payload_received", testPayload != null && !testPayload.isEmpty());
        response.put("config", payoutWebhookService.getWebhookConfigStatus());
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
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