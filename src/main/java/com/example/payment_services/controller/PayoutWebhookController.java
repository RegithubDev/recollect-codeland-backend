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
            log.info("Payout webhook received - Method: {}, Content-Type: {}",
                    request.getMethod(), request.getContentType());

            log.info("Webhook rawbody {}", rawBody);
            // 2. Handle test requests - Return simple OK without processing
            if (isTestRequest(request, rawBody)) {
                log.info("Test payout webhook detected - returning OK");
                return ResponseEntity.ok("OK");
            }

            log.debug("Cashfree payout webhook received, length: {} chars", rawBody.length());

            log.info("Headers start");
            java.util.Enumeration<String> headerNames = request.getHeaderNames();

            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                String headerValue = request.getHeader(headerName);
                log.info("Header: {} = {}", headerName, headerValue);
            }

            log.info("Headers close");
            // 3. Get signature headers (Cashfree Payout uses different headers)
            String signature = request.getHeader("x-cf-signature");
            String timestamp = request.getHeader("x-cf-timestamp");

            // 4. Verify signature for real requests
            if (signature != null && !signature.isEmpty()) {
                if (!payoutWebhookService.verifyPayoutSignature(rawBody, signature)) {
                    log.error("Invalid payout webhook signature");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
                }
            } else {
                log.warn("Missing signature header - treating as test");
                return ResponseEntity.ok("OK");
            }

            // 5. Parse and process payout webhook
            Map<String, Object> webhookData = payoutWebhookService.parsePayoutWebhookPayload(rawBody);

            // Check if it's a test webhook from the service
            if (webhookData.containsKey("isTest") && (Boolean) webhookData.get("isTest")) {
                log.info("Test webhook detected by service");
                return ResponseEntity.ok("OK");
            }

            // Process real webhook
            payoutWebhookService.processPayoutWebhook(webhookData);

            log.info("Payout webhook processed successfully: {}", webhookData.get("transferId"));
            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            log.error("Error processing payout webhook: {}", e.getMessage());
            // Always return OK to prevent retries
            return ResponseEntity.ok("OK");
        }
    }

    /**
     * Check if this is a test request
     */
    private boolean isTestRequest(HttpServletRequest request, String rawBody) {
        // Empty body test
        if (rawBody == null || rawBody.trim().isEmpty()) {
            return true;
        }

        // Check User-Agent header
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && (userAgent.contains("test") || userAgent.contains("Test"))) {
            return true;
        }

        // Check if body is simple test string
        String trimmedBody = rawBody.trim();
        if (trimmedBody.equals("test") || trimmedBody.equals("ping") || trimmedBody.equals("{}")) {
            return true;
        }

        // Check if body contains test indicators
        if (rawBody.contains("\"test\"") || rawBody.contains("\"isTest\"")) {
            return true;
        }

        return false;
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