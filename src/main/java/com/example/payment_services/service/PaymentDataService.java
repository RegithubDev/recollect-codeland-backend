package com.example.payment_services.service;

import com.example.payment_services.dto.payin.*;
import com.example.payment_services.dto.refund.*;
import com.example.payment_services.entity.PaymentTransaction;
import com.example.payment_services.repository.PaymentTransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentDataService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public PaymentTransaction saveOrder(PayinOrderResponseDTO orderResponse) {
        PaymentTransaction transaction = new PaymentTransaction();

        // Set order details
        transaction.setOrderId(orderResponse.getOrderId());
        transaction.setCfOrderId(orderResponse.getCfOrderId());
        transaction.setPaymentSessionId(orderResponse.getPaymentSessionId());
        // Handle orderAmount - check for null and set to 0 if null
        Double orderAmountValue = orderResponse.getOrderAmount();
        transaction.setOrderAmount(orderAmountValue != null ? BigDecimal.valueOf(orderAmountValue) : BigDecimal.ZERO);

        // Handle realAmount - check for null and set to 0 if null
        Double realAmountValue = orderResponse.getRealAmount();
        transaction.setRealAmount(realAmountValue != null ? BigDecimal.valueOf(realAmountValue) : BigDecimal.ZERO);

        // Handle walletAmount - check for null and set to 0 if null
        Double walletAmountValue = orderResponse.getWalletAmount();
        transaction.setWalletAmount(walletAmountValue != null ? BigDecimal.valueOf(walletAmountValue) : BigDecimal.ZERO);
        transaction.setOrderCurrency(orderResponse.getOrderCurrency());
        transaction.setOrderStatus(PaymentTransaction.OrderStatus.valueOf(orderResponse.getOrderStatus()));

        // Parse dates
        DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        if (orderResponse.getCreatedAt() != null) {
            transaction.setCreatedAt(LocalDateTime.parse(orderResponse.getCreatedAt(), formatter));
        }
        if (orderResponse.getOrderExpiryTime() != null) {
            transaction.setOrderExpiryTime(LocalDateTime.parse(orderResponse.getOrderExpiryTime(), formatter));
        }

        // Set customer details
        if (orderResponse.getCustomerDetails() != null) {
            PaymentTransaction.CustomerDetails customer = new PaymentTransaction.CustomerDetails();
            customer.setCustomerId(orderResponse.getCustomerDetails().getCustomerId());
            customer.setCustomerName(orderResponse.getCustomerDetails().getCustomerName());
            customer.setCustomerEmail(orderResponse.getCustomerDetails().getCustomerEmail());
            customer.setCustomerPhone(orderResponse.getCustomerDetails().getCustomerPhone());
            customer.setCustomerUid(orderResponse.getCustomerDetails().getCustomerUid());
            transaction.setCustomerDetails(customer);
        }

        // Store JSON data
        transaction.setOrderNote(orderResponse.getOrderNote());
        transaction.setEntity(orderResponse.getEntity());

        // Convert objects to JSON maps
        if (orderResponse.getOrderMeta() != null) {
            transaction.setOrderMeta(objectMapper.convertValue(orderResponse.getOrderMeta(), Map.class));
        }
        if (orderResponse.getOrderTags() != null) {
            transaction.setOrderTags(objectMapper.convertValue(orderResponse.getOrderTags(), Map.class));
        }

        return paymentTransactionRepository.save(transaction);
    }

    @Transactional
    public PaymentTransaction updatePayment(String orderId, String cfPaymentId,
                                            BigDecimal amount, String paymentMethod) {
        PaymentTransaction transaction = paymentTransactionRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        transaction.markPaymentSuccess(cfPaymentId, amount, paymentMethod);
        return paymentTransactionRepository.save(transaction);
    }

    @Transactional
    public PaymentTransaction addRefund(String orderId, RefundResponseDTO refundResponse) {
        PaymentTransaction transaction = paymentTransactionRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        BigDecimal refundAmount = BigDecimal.valueOf(refundResponse.getRefundAmount());

        // Add refund
        transaction.addRefund(refundAmount, refundResponse.getCfRefundId(), refundResponse.getRefundId());

        // Set refund details
        transaction.setRefundNote(refundResponse.getRefundNote());
        transaction.setRefundSpeed(refundResponse.getRefundSpeed() != null ?
                refundResponse.getRefundSpeed().getRequested() : null);

        // Parse refund dates
        DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        if (refundResponse.getCreatedAt() != null) {
            transaction.setRefundCreatedAt(LocalDateTime.parse(refundResponse.getCreatedAt(), formatter));
        }
        if (refundResponse.getProcessedAt() != null) {
            transaction.setRefundProcessedAt(LocalDateTime.parse(refundResponse.getProcessedAt(), formatter));
        }

        // Set refund status
        if ("SUCCESS".equals(refundResponse.getRefundStatus())) {
            transaction.setRefundStatus(PaymentTransaction.RefundStatus.SUCCESS);
        } else if ("PENDING".equals(refundResponse.getRefundStatus())) {
            transaction.setRefundStatus(PaymentTransaction.RefundStatus.PENDING);
        } else if ("FAILED".equals(refundResponse.getRefundStatus())) {
            transaction.setRefundStatus(PaymentTransaction.RefundStatus.FAILED);
        }

        // Store JSON data
        if (refundResponse.getRefundSpeed() != null) {
            Map<String, Object> speedDetails = new HashMap<>();
            speedDetails.put("requested", refundResponse.getRefundSpeed().getRequested());
            speedDetails.put("accepted", refundResponse.getRefundSpeed().getAccepted());
            speedDetails.put("processed", refundResponse.getRefundSpeed().getProcessed());
            speedDetails.put("message", refundResponse.getRefundSpeed().getMessage());
            transaction.setRefundSpeedDetails(speedDetails);
        }

        return paymentTransactionRepository.save(transaction);
    }

    @Transactional
    public PaymentTransaction updateRefundStatus(String cfRefundId, String status) {
        PaymentTransaction transaction = paymentTransactionRepository.findByCfRefundId(cfRefundId)
                .orElseThrow(() -> new RuntimeException("Refund not found: " + cfRefundId));

        if ("SUCCESS".equals(status)) {
            transaction.setRefundStatus(PaymentTransaction.RefundStatus.SUCCESS);
            transaction.setRefundProcessedAt(LocalDateTime.now());
        } else if ("FAILED".equals(status)) {
            transaction.setRefundStatus(PaymentTransaction.RefundStatus.FAILED);
        }

        return paymentTransactionRepository.save(transaction);
    }
}