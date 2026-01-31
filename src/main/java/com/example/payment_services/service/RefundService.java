package com.example.payment_services.service;

import com.example.payment_services.dto.refund.*;
import com.example.payment_services.service.http.CashfreeRefundHttpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundService {

    private final CashfreeRefundHttpService cashfreeRefundHttpService;

    public RefundResponseDTO initiateRefund(String orderId, RefundRequestDTO request) {
        try {
            // Validate request
            validateRefundRequest(request);

            // Call Cashfree API
            RefundResponseDTO response = cashfreeRefundHttpService.createRefund(orderId, request);

            log.info("Refund initiated: orderId={}, refundId={}, cfRefundId={}",
                    orderId, request.getRefundId(), response.getCfRefundId());

            return response;

        } catch (Exception e) {
            log.error("Initiate refund error", e);
            throw new RuntimeException("Refund initiation failed: " + e.getMessage());
        }
    }

    public RefundResponseDTO getRefundDetails(String orderId, String refundId) {
        try {
            RefundResponseDTO refund = cashfreeRefundHttpService.getRefund(orderId, refundId);

            // Add business logic if needed
            if ("SUCCESS".equalsIgnoreCase(refund.getRefundStatus())) {
                log.info("Refund {} completed successfully", refundId);
            } else if ("PENDING".equalsIgnoreCase(refund.getRefundStatus())) {
                log.info("Refund {} is pending", refundId);
            }

            return refund;

        } catch (Exception e) {
            log.error("Get refund details error", e);
            throw new RuntimeException("Get refund details failed: " + e.getMessage());
        }
    }

    public RefundResponseDTO[] getAllOrderRefunds(String orderId) {
        try {
            RefundResponseDTO[] refunds = cashfreeRefundHttpService.getAllRefunds(orderId);

            log.info("Found {} refunds for order {}", refunds.length, orderId);

            return refunds;

        } catch (Exception e) {
            log.error("Get all refunds error", e);
            throw new RuntimeException("Get all refunds failed: " + e.getMessage());
        }
    }

    // Check if refund is eligible
    public boolean isRefundEligible(String orderId) {
        try {
            // You might want to check order status first
            // For example, only completed orders can be refunded
            return true;
        } catch (Exception e) {
            log.error("Check refund eligibility error", e);
            return false;
        }
    }

    private void validateRefundRequest(RefundRequestDTO request) {
        if (request.getRefundAmount() == null || request.getRefundAmount() <= 0) {
            throw new IllegalArgumentException("Refund amount must be greater than 0");
        }

        if (request.getRefundId() == null || request.getRefundId().trim().isEmpty()) {
            throw new IllegalArgumentException("Refund ID is required");
        }

        if (!"STANDARD".equals(request.getRefundSpeed()) &&
                !"INSTANT".equals(request.getRefundSpeed())) {
            throw new IllegalArgumentException("Refund speed must be STANDARD or INSTANT");
        }
    }
}