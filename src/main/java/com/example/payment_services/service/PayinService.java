package com.example.payment_services.service;

import com.example.payment_services.dto.payin.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayinService {

    private final CashfreePayinHttpService cashfreePayinHttpService;

    public PayinOrderResponseDTO createPaymentOrder(PayinOrderRequestDTO request) {
        try {
            // Add any business logic here
            if (request.getOrderAmount() == null || request.getOrderAmount() <= 0) {
                throw new IllegalArgumentException("Order amount must be greater than 0");
            }

            if (request.getCustomerDetails() == null ||
                    request.getCustomerDetails().getCustomerId() == null) {
                throw new IllegalArgumentException("Customer details are required");
            }

            // Call Cashfree PG API
            PayinOrderResponseDTO response = cashfreePayinHttpService.createOrder(request);

            log.info("Payment order created: orderId={}, cfOrderId={}",
                    response.getOrderId(), response.getCfOrderId());

            return response;

        } catch (Exception e) {
            log.error("Create payment order error", e);
            throw new RuntimeException("Payment order creation failed: " + e.getMessage());
        }
    }

    public PayinOrderResponseDTO getOrderDetails(String orderId) {
        try {
            PayinOrderResponseDTO order = cashfreePayinHttpService.getOrder(orderId);

            // Add any business logic
            if ("TERMINATED".equalsIgnoreCase(order.getOrderStatus())) {
                log.warn("Order {} is terminated", orderId);
            }

            return order;

        } catch (Exception e) {
            log.error("Get order details error", e);
            throw new RuntimeException("Get order details failed: " + e.getMessage());
        }
    }

    public PayinOrderResponseDTO cancelOrder(String orderId) {
        try {
            log.info("Cancelling order: {}", orderId);

            // First check if order exists
            PayinOrderResponseDTO currentOrder = cashfreePayinHttpService.getOrder(orderId);

            if ("TERMINATED".equalsIgnoreCase(currentOrder.getOrderStatus())) {
                log.warn("Order {} is already terminated", orderId);
                return currentOrder;
            }

            // Terminate the order
            return cashfreePayinHttpService.terminateOrder(orderId);

        } catch (Exception e) {
            log.error("Cancel order error", e);
            throw new RuntimeException("Order cancellation failed: " + e.getMessage());
        }
    }

    public PayinExtendedOrderResponseDTO getExtendedOrderDetails(String orderId) {
        try {
            return cashfreePayinHttpService.getExtendedOrder(orderId);
        } catch (Exception e) {
            log.error("Get extended order error", e);
            throw new RuntimeException("Get extended order failed: " + e.getMessage());
        }
    }
}