package com.example.payment_services.controller;

import com.example.payment_services.service.RefundWebhookService;
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
@Tag(name = "Refund Webhooks", description = "APIs for receiving refund status updates")
public class RefundWebhookController {

    private final RefundWebhookService refundWebhookService;

    @Operation(summary = "Cashfree Refund Webhook")
    @PostMapping("/cashfree/refund")
    public ResponseEntity<?> handleCashfreeRefundWebhook(HttpServletRequest request) {

        String rawBody = null;
        Map<String, Object> response = new HashMap<>();

        try {
            // 1. Read request body
            rawBody = readRequestBody(request);
            log.debug("Cashfree refund webhook received, length: {} chars", rawBody.length());

            // 2. Get signature headers (Cashfree Refund uses same headers as Payin)
            String signature = request.getHeader("x-webhook-signature");
            String timestamp = request.getHeader("x-webhook-timestamp");

            // 3. Validate headers
            if (signature == null || signature.isEmpty()) {
                response.put("status", "error");
                response.put("message", "Missing x-webhook-signature header");
                response.put("timestamp", LocalDateTime.now().toString());
                log.error("Missing signature header for refund webhook");
                return ResponseEntity.badRequest().body(response);
            }

            if (timestamp == null || timestamp.isEmpty()) {
                response.put("status", "error");
                response.put("message", "Missing x-webhook-timestamp header");
                response.put("timestamp", LocalDateTime.now().toString());
                log.error("Missing timestamp header for refund webhook");
                return ResponseEntity.badRequest().body(response);
            }

            // 4. Verify signature (same as payin)
            if (!refundWebhookService.verifyRefundSignature(rawBody, signature, timestamp)) {
                response.put("status", "error");
                response.put("message", "Invalid refund webhook signature");
                response.put("timestamp", LocalDateTime.now().toString());
                log.error("Invalid refund webhook signature");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            // 5. Parse and process refund webhook
            Map<String, Object> webhookData = refundWebhookService.parseRefundWebhookPayload(rawBody);
            refundWebhookService.processRefundWebhook(webhookData);

            String refundId = (String) webhookData.get("refundId");
            String orderId = (String) webhookData.get("orderId");
            String eventType = (String) webhookData.get("eventType");

            // 6. Return success response
            response.put("status", "success");
            response.put("message", "Refund webhook processed successfully");
            response.put("refund_id", refundId);
            response.put("order_id", orderId);
            response.put("event_type", eventType);
            response.put("timestamp", LocalDateTime.now().toString());

            log.info("Refund webhook processed successfully: {} - {} - {}", orderId, refundId, eventType);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error processing refund webhook");
            response.put("error", e.getMessage());
            response.put("timestamp", LocalDateTime.now().toString());
            log.error("Error processing refund webhook", e);
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