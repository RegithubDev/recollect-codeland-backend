package com.example.payment_services.controller;

import com.example.payment_services.dto.refund.RefundApprovalDTO;
import com.example.payment_services.dto.refund.*;
import com.example.payment_services.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/refund/")
@RequiredArgsConstructor
@Slf4j
public class RefundController {

    private final RefundService refundService;

    // 1. Initiate Refund
    @PostMapping("/initiate/{orderId}")
    public ResponseEntity<RefundResponseDTO> createRefund(
            @PathVariable String orderId,
            @RequestBody RefundRequestDTO request) {

        log.info("Creating refund for order: {}", orderId);

        RefundResponseDTO response = refundService.initiateRefund(orderId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 2. Get Refund status by ID
    @GetMapping("/status/{orderId}/{cfRefundId}")
    public ResponseEntity<RefundResponseDTO> getRefund(
            @PathVariable String orderId,
            @PathVariable String cfRefundId) {

        log.info("Getting refund: orderId={}, refundId={}", orderId, cfRefundId);

        RefundResponseDTO refund = refundService.getRefundDetails(orderId, cfRefundId);
        return ResponseEntity.ok(refund);
    }

//    // 3. Get All Refunds for Order (since partial payment not allowed one order only have one refund this feature only placeholder
//    @GetMapping
//    public ResponseEntity<RefundResponseDTO[]> getAllRefunds(
//            @PathVariable String orderId) {
//
//        log.info("Getting all refunds for order: {}", orderId);
//
//        RefundResponseDTO[] refunds = refundService.getAllOrderRefunds(orderId);
//        return ResponseEntity.ok(refunds);
//    }

    // 4. Check Refund Eligibility since partial payment not allowed one order only have one refund this feature only placeholder
    @GetMapping("/eligibility/{orderId}")
    public ResponseEntity<?> checkRefundEligibility(
            @PathVariable String orderId) {

        log.info("Checking refund eligibility for order: {}", orderId);

        boolean eligible = refundService.isRefundEligible(orderId);

        return ResponseEntity.ok().body(
                new EligibilityResponse(eligible,
                        eligible ? "Order is eligible for refund" : "Order is not eligible for refund")
        );
    }

    @PostMapping("/approve")
    public ResponseEntity<?> approveRefund(@RequestBody RefundApprovalDTO approvalRequest) {
        try {
            RefundApprovalDTO refund = refundService.handleRefundApproved(approvalRequest);
            return ResponseEntity.ok(refund);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
        }
    }

    // Helper class for eligibility response
    private record EligibilityResponse(boolean eligible, String message) {}
}