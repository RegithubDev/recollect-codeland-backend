package com.example.payment_services.controller;

import com.example.payment_services.dto.refund.*;
import com.example.payment_services.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payins/orders/{orderId}/refunds")
@RequiredArgsConstructor
@Slf4j
public class RefundController {

    private final RefundService refundService;

    // 1. Create Refund
    @PostMapping
    public ResponseEntity<RefundResponseDTO> createRefund(
            @PathVariable String orderId,
            @RequestBody RefundRequestDTO request) {

        log.info("Creating refund for order: {}", orderId);

        RefundResponseDTO response = refundService.initiateRefund(orderId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 2. Get Refund by ID
    @GetMapping("/{refundId}")
    public ResponseEntity<RefundResponseDTO> getRefund(
            @PathVariable String orderId,
            @PathVariable String refundId) {

        log.info("Getting refund: orderId={}, refundId={}", orderId, refundId);

        RefundResponseDTO refund = refundService.getRefundDetails(orderId, refundId);
        return ResponseEntity.ok(refund);
    }

    // 3. Get All Refunds for Order
    @GetMapping
    public ResponseEntity<RefundResponseDTO[]> getAllRefunds(
            @PathVariable String orderId) {

        log.info("Getting all refunds for order: {}", orderId);

        RefundResponseDTO[] refunds = refundService.getAllOrderRefunds(orderId);
        return ResponseEntity.ok(refunds);
    }

    // 4. Check Refund Eligibility
    @GetMapping("/eligibility")
    public ResponseEntity<?> checkRefundEligibility(
            @PathVariable String orderId) {

        log.info("Checking refund eligibility for order: {}", orderId);

        boolean eligible = refundService.isRefundEligible(orderId);

        return ResponseEntity.ok().body(
                new EligibilityResponse(eligible,
                        eligible ? "Order is eligible for refund" : "Order is not eligible for refund")
        );
    }

    // Helper class for eligibility response
    private record EligibilityResponse(boolean eligible, String message) {}
}