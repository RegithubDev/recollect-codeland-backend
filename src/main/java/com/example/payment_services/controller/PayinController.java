package com.example.payment_services.controller;

import com.example.payment_services.dto.payin.*;
import com.example.payment_services.entity.PaymentTransaction;
import com.example.payment_services.service.PayinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "PayIn Operations", description = "APIs for handling payment inflows and order management")
public class PayinController {

    private final PayinService payinService;

    @Operation(
            summary = "Create Payment Order",
            description = "Creates a new payment order for processing incoming payments"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Order created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/create/orders")
    public ResponseEntity<PayinOrderResponseDTO> createOrder(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Payment order request details",
                    required = true
            )
            @RequestBody PayinOrderRequestDTO request) {

        log.info("Creating payment order");
        PayinOrderResponseDTO response = payinService.createPaymentOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Get Order Details",
            description = "Retrieves payment order details using multiple identifiers for verification"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Order details retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters or mismatched data")
    })
    @GetMapping("/get/order/{orderId}/{cfPaymentId}/{amount}/{paymentMethod}")
    public ResponseEntity<PaymentTransaction> getOrder(
            @Parameter(description = "Unique order identifier", example = "ORD_12345", required = true)
            @PathVariable String orderId,

            @Parameter(description = "Cashfree payment ID", example = "CF123456789", required = true)
            @PathVariable String cfPaymentId,

            @Parameter(description = "Transaction amount", example = "1000.00", required = true)
            @PathVariable BigDecimal amount,

            @Parameter(description = "Payment method used", example = "UPI", required = true)
            @PathVariable String paymentMethod) {

        log.info("Getting order: {}", orderId);
        PaymentTransaction order = payinService.getOrderDetails(orderId, cfPaymentId, amount, paymentMethod);
        return ResponseEntity.ok(order);
    }

//    @Operation(
//        summary = "Cancel Payment Order",
//        description = "Cancels an existing payment order"
//    )
//    @ApiResponses(value = {
//        @ApiResponse(responseCode = "200", description = "Order cancelled successfully"),
//        @ApiResponse(responseCode = "404", description = "Order not found"),
//        @ApiResponse(responseCode = "400", description = "Order cannot be cancelled (already processed)")
//    })
//    @PostMapping("/orders/{orderId}/cancel")
//    public ResponseEntity<PayinOrderResponseDTO> cancelOrder(
//            @Parameter(description = "Order ID to cancel", example = "ORD_12345", required = true)
//            @PathVariable String orderId) {
//
//        log.info("Cancelling order: {}", orderId);
//        PayinOrderResponseDTO response = payinService.cancelOrder(orderId);
//        return ResponseEntity.ok(response);
//    }

//    @Operation(
//        summary = "Get Extended Order Details",
//        description = "Retrieves comprehensive order details including payment history and status timeline"
//    )
//    @ApiResponses(value = {
//        @ApiResponse(responseCode = "200", description = "Extended order details retrieved"),
//        @ApiResponse(responseCode = "404", description = "Order not found")
//    })
//    @GetMapping("/update/orders/{orderId}")
//    public ResponseEntity<PayinExtendedOrderResponseDTO> getExtendedOrder(
//            @Parameter(description = "Order ID", example = "ORD_12345", required = true)
//            @PathVariable String orderId) {
//
//        log.info("Getting extended order: {}", orderId);
//        PayinExtendedOrderResponseDTO order = payinService.getExtendedOrderDetails(orderId);
//        return ResponseEntity.ok(order);
//    }

    @Operation(
            summary = "Service Health Check",
            description = "Checks the health and availability of the PayIn service"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Service is healthy and running"),
            @ApiResponse(responseCode = "503", description = "Service is unavailable")
    })
    @GetMapping("/health/check")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Cashfree Payin API");
        response.put("apis", "Create Order, Get Order, Cancel Order, Get Extended Order");
        return ResponseEntity.ok(response);
    }
}