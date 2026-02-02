package com.example.payment_services.service;

import com.example.payment_services.dto.refund.RefundApprovalDTO;
import com.example.payment_services.dto.refund.*;
import com.example.payment_services.entity.PaymentTransaction;
import com.example.payment_services.service.http.CashfreeRefundHttpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static com.example.payment_services.util.SecurityUtil.getCurrentUserId;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundService {

    private final CashfreeRefundHttpService cashfreeRefundHttpService;
    private final PaymentDataService paymentDataService;
    private final LedgerService ledgerService;

    public RefundResponseDTO initiateRefund(String orderId, RefundRequestDTO request) {
        try {
            // Validate request
            validateRefundRequest(request);

            // Call Cashfree API
            RefundResponseDTO response = cashfreeRefundHttpService.createRefund(orderId, request);

            log.info("Refund initiated: orderId={}, refundId={}, cfRefundId={}",
                    orderId, request.getRefundId(), response.getCfRefundId());
            // save to database
            PaymentTransaction paymentTransaction = paymentDataService.addRefund(orderId, response);
            log.info("Payment refund updated in database:{}", paymentTransaction);
            return response;

        } catch (Exception e) {
            log.error("Initiate refund error", e);
            throw new RuntimeException("Refund initiation failed: " + e.getMessage());
        }
    }

    public RefundResponseDTO getRefundDetails(String orderId, String cfRefundId) {
        try {
            RefundResponseDTO refund = cashfreeRefundHttpService.getRefund(orderId, cfRefundId);

            // Add business logic if needed
            if ("SUCCESS".equalsIgnoreCase(refund.getRefundStatus())) {
                log.info("Refund {} completed successfully", cfRefundId);
            } else if ("PENDING".equalsIgnoreCase(refund.getRefundStatus())) {
                log.info("Refund {} is pending", cfRefundId);
            }
            PaymentTransaction paymentTransaction = paymentDataService.updateRefundStatus(orderId, cfRefundId);
            log.info("Payment refund status updated in database:{}", paymentTransaction);
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

    public RefundApprovalDTO handleRefundApproved(RefundApprovalDTO approvalRequest){
        ledgerService.recordRefundApproved(approvalRequest.getPaymentTransactionId(), approvalRequest.getTransactionId(),
                approvalRequest.getCustomerId(), approvalRequest.getOrderId(), approvalRequest.getAmount(), getCurrentUserId());
        log.info("Refund processed RefundApproved received: Cashfreepay RefundId={}", approvalRequest);
        return approvalRequest;
    };

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