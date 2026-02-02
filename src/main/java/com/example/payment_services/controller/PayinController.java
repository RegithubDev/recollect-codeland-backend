package com.example.payment_services.controller;

import com.example.payment_services.dto.payin.*;
import com.example.payment_services.service.PayinService;
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
public class PayinController {

    private final PayinService payinService;

    // 1. Create Order
    @PostMapping("/create/orders")
    public ResponseEntity<PayinOrderResponseDTO> createOrder(
            @RequestBody PayinOrderRequestDTO request) {

        log.info("Creating payment order");
        PayinOrderResponseDTO response = payinService.createPaymentOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 2. Get Order
    @GetMapping("/get/order/{orderId}/{cfPaymentId}/{amount}/{paymentMethod}")
    public ResponseEntity<PayinOrderResponseDTO> getOrder(
            @PathVariable String orderId,
            @PathVariable String cfPaymentId,
            @PathVariable BigDecimal amount,
            @PathVariable String paymentMethod){

        log.info("Getting order: {}", orderId);
        PayinOrderResponseDTO order = payinService.getOrderDetails(orderId,cfPaymentId,amount, paymentMethod);
        return ResponseEntity.ok(order);
    }

//    // 3. Cancel/Terminate Order
//    @PostMapping("/orders/{orderId}/cancel")
//    public ResponseEntity<PayinOrderResponseDTO> cancelOrder(
//            @PathVariable String orderId) {
//
//        log.info("Cancelling order: {}", orderId);
//        PayinOrderResponseDTO response = payinService.cancelOrder(orderId);
//        return ResponseEntity.ok(response);
//    }

//    // 4. Get Extended Order
//    @GetMapping("/update/orders/{orderId}")
//    public ResponseEntity<PayinExtendedOrderResponseDTO> getExtendedOrder(
//            @PathVariable String orderId) {
//
//        log.info("Getting extended order: {}", orderId);
//        PayinExtendedOrderResponseDTO order = payinService.getExtendedOrderDetails(orderId);
//        return ResponseEntity.ok(order);
//    }

    // 5. Health Check
    @GetMapping("/health/check")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Cashfree Payin API");
        response.put("apis", "Create Order, Get Order, Cancel Order, Get Extended Order");
        return ResponseEntity.ok(response);
    }
}