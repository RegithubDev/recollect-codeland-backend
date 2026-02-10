package com.example.payment_services.service;

import com.example.payment_services.dto.payin.*;
import com.example.payment_services.entity.PaymentTransaction;
import com.example.payment_services.repository.PaymentTransactionRepository;
import com.example.payment_services.service.http.CashfreePayinHttpService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static com.example.payment_services.util.SecurityUtil.getCurrentUserId;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayinService {

    private final CashfreePayinHttpService cashfreePayinHttpService;
    private final PaymentDataService paymentDataService;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final LedgerService ledgerService;

    @Transactional
    public PayinOrderResponseDTO createPaymentOrder(PayinOrderRequestDTO request) {
        try {
            // Validate basic requirements
            if (request.getOrderAmount() == null || request.getOrderAmount() <= 0) {
                throw new IllegalArgumentException("Order amount must be greater than 0");
            }

            if (request.getCustomerDetails() == null || request.getCustomerDetails().getCustomerId() == null) {
                throw new IllegalArgumentException("Customer details are required");
            }

            // Initialize amounts if null
            double realAmount = request.getRealAmount() != null ? request.getRealAmount() : 0.0;
            double walletAmount = request.getWalletAmount() != null ? request.getWalletAmount() : 0.0;

            // Validate total matches order amount
            if (Math.abs((realAmount + walletAmount) - request.getOrderAmount()) > 0.01) {
                throw new IllegalArgumentException("realAmount + walletAmount must equal orderAmount");
            }

            PayinOrderResponseDTO response;

            // Case 1: Real money involved (with or without wallet)
            if (realAmount > 0) {
                response = cashfreePayinHttpService.createOrder(request);
                log.info("Cashfree order created: orderId={}", response.getOrderId());

                // Deduct wallet if applicable
                if (walletAmount > 0) {
                    handleWalletDeduction(request, response, walletAmount);
                }
            }
            // Case 2: Wallet only payment
            else if (walletAmount > 0) {
                response = createWalletOnlyOrderResponse(request);
                handleWalletDeduction(request, response, walletAmount);
                response.setOrderStatus("PAID");
            } else {
                throw new IllegalArgumentException("Either realAmount or walletAmount must be greater than 0");
            }

            // Save to database
            PaymentTransaction paymentTransaction = paymentDataService.saveOrder(response);
            log.info("Order saved: id={}", paymentTransaction.getId());

            return response;

        } catch (Exception e) {
            log.error("Create payment order error", e);
            throw new RuntimeException("Payment order creation failed: " + e.getMessage());
        }
    }

    /**
     * Helper method to handle wallet-only order creation and wallet deduction
     */
    private PayinOrderResponseDTO createWalletOnlyOrderResponse(PayinOrderRequestDTO request) {
        PayinOrderResponseDTO response = new PayinOrderResponseDTO();

        String timestamp = String.valueOf(System.currentTimeMillis());
        String orderId = "WALLET_" + timestamp;

        // Set order details
        response.setOrderId(orderId);
        response.setCfOrderId("CF_" + orderId);
        response.setOrderAmount(request.getOrderAmount());
        response.setWalletAmount(request.getWalletAmount());
        response.setRealAmount(0.0);
        response.setOrderCurrency(request.getOrderCurrency() != null ? request.getOrderCurrency() : "INR");
        response.setOrderStatus("ACTIVE");
        response.setEntity("order");

        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        response.setCreatedAt(now);
        response.setOrderExpiryTime(LocalDateTime.now().plusHours(24).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        response.setPaymentSessionId("SESS_" + timestamp);

        if (request.getCustomerDetails() != null) {
            PayinOrderResponseDTO.CustomerDetails customer = new PayinOrderResponseDTO.CustomerDetails();
            customer.setCustomerId(request.getCustomerDetails().getCustomerId());
            customer.setCustomerName(request.getCustomerDetails().getCustomerName());
            customer.setCustomerEmail(request.getCustomerDetails().getCustomerEmail());
            customer.setCustomerPhone(request.getCustomerDetails().getCustomerPhone());
            customer.setCustomerUid(request.getCustomerDetails().getCustomerId());
            response.setCustomerDetails(customer);
        }
        response.setOrderNote(request.getOrderNote());

        return response;
    }

    /**
     * Handle wallet deduction for both cases
     */
    private void handleWalletDeduction(PayinOrderRequestDTO request, PayinOrderResponseDTO response, Double walletAmount) {
        try {
            String transactionId = "TX_" + System.currentTimeMillis();

            ledgerService.deductionFromWallet(
                    response.getCfOrderId(),
                    transactionId,
                    request.getCustomerDetails().getCustomerId(),
                    response.getOrderId(),
                    BigDecimal.valueOf(walletAmount),
                    getCurrentUserId()
            );

            log.info("Wallet deduction successful: Order={}, Amount={}",
                    response.getOrderId(), walletAmount);

        } catch (Exception e) {
            log.error("Wallet deduction failed: {}", e.getMessage());
            throw new RuntimeException("Wallet deduction failed: " + e.getMessage());
        }
    }

    public PaymentTransaction getOrderDetails(String orderId, String cfPaymentId, BigDecimal amount, String paymentMethod) {
        try {
            PayinOrderResponseDTO order = cashfreePayinHttpService.getOrder(orderId);

            if ("TERMINATED".equalsIgnoreCase(order.getOrderStatus())) {
                log.warn("Order {} is terminated", orderId);
            }
            // save to database
            PaymentTransaction paymentTransaction = paymentDataService.updatePayment(orderId, cfPaymentId, amount, paymentMethod);
            log.info("Payment order updated in database:{}", paymentTransaction);
            return paymentTransaction;

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

    @Transactional
    public PaymentTransaction updatePaymentStatus(PaymentStatusUpdateDTO request) {
        try {
            log.info("Updating payment status: Order={}, Status={}, Amount={}",
                    request.getOrderId(), request.getPaymentStatus(), request.getAmount());

            // 1. Get order from database
            PaymentTransaction transaction = paymentTransactionRepository.findByOrderId(request.getOrderId())
                    .orElseThrow(() -> new RuntimeException("Order not found: " + request.getOrderId()));

            // 2. Check if payment is already processed
            if (transaction.getPaymentStatus() == PaymentTransaction.PaymentStatus.SUCCESS) {
                throw new RuntimeException("Payment already processed for order: " + request.getOrderId());
            }

            // 3. Validate with Cashfree API
            PayinOrderResponseDTO cashfreeOrder = cashfreePayinHttpService.getOrder(request.getOrderId());

            // 4. Verify order details match
            validatePaymentDetails(transaction, cashfreeOrder, request);

            // 5. Update payment status in database
            updateTransactionWithPayment(transaction, request);

            // 6. Save updated transaction
            PaymentTransaction savedTransaction = paymentTransactionRepository.save(transaction);

            log.info("Payment status updated successfully: Order={}, NewStatus={}, CfPaymentId={}",
                    request.getOrderId(), request.getPaymentStatus(), request.getCfPaymentId());

            return savedTransaction;

        } catch (Exception e) {
            log.error("Update payment status error", e);
            throw new RuntimeException("Payment status update failed: " + e.getMessage());
        }
    }

    private void validatePaymentDetails(PaymentTransaction transaction,
                                        PayinOrderResponseDTO cashfreeOrder,
                                        PaymentStatusUpdateDTO request) {
        // Validate order exists in Cashfree
        if (cashfreeOrder == null) {
            throw new RuntimeException("Order not found in Cashfree: " + request.getOrderId());
        }

        // Validate order IDs match
        if (!request.getOrderId().equals(cashfreeOrder.getOrderId())) {
            throw new RuntimeException("Order ID mismatch between request and Cashfree");
        }

        // Validate order IDs match our database
        if (!request.getOrderId().equals(transaction.getOrderId())) {
            throw new RuntimeException("Order ID mismatch between request and database");
        }

        // Validate amount matches (with tolerance for minor differences)
        BigDecimal requestedAmount = request.getAmount();
        BigDecimal cashfreeAmount = BigDecimal.valueOf(cashfreeOrder.getOrderAmount());
        BigDecimal databaseAmount = transaction.getOrderAmount();

        // Check if amounts match within reasonable tolerance (e.g., 1 paisa)
        BigDecimal tolerance = new BigDecimal("0.01");

        if (requestedAmount != null) {
            BigDecimal diffWithCashfree = requestedAmount.subtract(cashfreeAmount).abs();
            BigDecimal diffWithDatabase = requestedAmount.subtract(databaseAmount).abs();

            if (diffWithCashfree.compareTo(tolerance) > 0) {
                throw new RuntimeException(String.format(
                        "Amount mismatch with Cashfree. Requested: %s, Cashfree: %s",
                        requestedAmount, cashfreeAmount));
            }

            if (diffWithDatabase.compareTo(tolerance) > 0) {
                throw new RuntimeException(String.format(
                        "Amount mismatch with database. Requested: %s, Database: %s",
                        requestedAmount, databaseAmount));
            }
        }

        // Validate order status from Cashfree
        String cashfreeStatus = cashfreeOrder.getOrderStatus();
        if (!"PAID".equalsIgnoreCase(cashfreeStatus) && !"ACTIVE".equalsIgnoreCase(cashfreeStatus)) {
            log.warn("Cashfree order status is not PAID or ACTIVE: {}", cashfreeStatus);
        }
    }

    private void updateTransactionWithPayment(PaymentTransaction transaction, PaymentStatusUpdateDTO request) {
        // Update payment status
        PaymentTransaction.PaymentStatus paymentStatus =
                PaymentTransaction.PaymentStatus.valueOf(request.getPaymentStatus().toUpperCase());
        transaction.setPaymentStatus(paymentStatus);

        // Update order status based on payment status
        if (paymentStatus == PaymentTransaction.PaymentStatus.SUCCESS) {
            transaction.setOrderStatus(PaymentTransaction.OrderStatus.PAID);
        } else if (paymentStatus == PaymentTransaction.PaymentStatus.FAILED) {
            transaction.setOrderStatus(PaymentTransaction.OrderStatus.FAILED);
        }

        // Update payment details
        transaction.setCfPaymentId(request.getCfPaymentId());
        transaction.setPaymentAmount(request.getAmount());
        transaction.setPaymentMethod(request.getPaymentMethod());
        transaction.setPaymentGateway(request.getPaymentGateway());
        transaction.setBankReference(request.getBankReference());
        transaction.setUtr(request.getUtr());
        transaction.setAuthCode(request.getAuthCode());
        transaction.setPaymentProcessedAt(request.getPaymentTime() != null ?
                request.getPaymentTime() : LocalDateTime.now());

        // Update attempt count
        transaction.setAttemptCount(transaction.getAttemptCount() != null ?
                transaction.getAttemptCount() + 1 : 1);

        // Mark callback received
        transaction.setCallbackReceived(true);
    }
}