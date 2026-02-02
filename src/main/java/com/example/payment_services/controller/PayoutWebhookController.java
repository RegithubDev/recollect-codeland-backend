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
            log.debug("Cashfree payout webhook received, length: {} chars", rawBody.length());

            // 2. Get signature headers (Cashfree Payout uses different headers)
            String signature = request.getHeader("x-cf-signature");
            String timestamp = request.getHeader("x-cf-timestamp");

            // 3. Validate headers
            if (signature == null || signature.isEmpty()) {
                response.put("status", "error");
                response.put("message", "Missing x-cf-signature header");
                response.put("timestamp", LocalDateTime.now().toString());
                log.error("Missing signature header for payout webhook");
                return ResponseEntity.badRequest().body(response);
            }

            // 4. Verify signature (Cashfree Payout has different signature format)
            if (!payoutWebhookService.verifyPayoutSignature(rawBody, signature)) {
                response.put("status", "error");
                response.put("message", "Invalid payout webhook signature");
                response.put("timestamp", LocalDateTime.now().toString());
                log.error("Invalid payout webhook signature");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            // 5. Parse and process payout webhook
            Map<String, Object> webhookData = payoutWebhookService.parsePayoutWebhookPayload(rawBody);
            payoutWebhookService.processPayoutWebhook(webhookData);

            String transferId = (String) webhookData.get("transferId");
            String eventType = (String) webhookData.get("eventType");

            // 6. Return success response
            response.put("status", "success");
            response.put("message", "Payout webhook processed successfully");
            response.put("transfer_id", transferId);
            response.put("event_type", eventType);
            response.put("timestamp", LocalDateTime.now().toString());

            log.info("Payout webhook processed successfully: {} - {}", transferId, eventType);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error processing payout webhook");
            response.put("error", e.getMessage());
            response.put("timestamp", LocalDateTime.now().toString());
            log.error("Error processing payout webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
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