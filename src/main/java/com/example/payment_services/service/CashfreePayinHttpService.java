package com.example.payment_services.service;

import com.example.payment_services.config.CashfreeConfig;
import com.example.payment_services.dto.payin.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CashfreePayinHttpService {

    private final RestTemplate restTemplate;
    private final CashfreeConfig cashfreeConfig;

    // 1. Create Order
    public PayinOrderResponseDTO createOrder(PayinOrderRequestDTO request) {
        try {
            String url = cashfreeConfig.getPgBaseUrl() + "/orders";

            log.info("Creating payin order: {}", request);

            HttpHeaders headers = createPayinHeaders();
            HttpEntity<PayinOrderRequestDTO> entity = new HttpEntity<>(request, headers);

            ResponseEntity<PayinOrderResponseDTO> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, PayinOrderResponseDTO.class);

            log.info("Create order response status: {}", response.getStatusCode());

            if (response.getStatusCode() == HttpStatus.OK ||
                    response.getStatusCode() == HttpStatus.CREATED) {
                return response.getBody();
            }
            throw new RuntimeException("Failed to create order: " + response.getStatusCode());

        } catch (Exception e) {
            log.error("Create order error", e);
            throw new RuntimeException("Order creation failed: " + e.getMessage());
        }
    }

    // 2. Get Order Details
    public PayinOrderResponseDTO getOrder(String orderId) {
        try {
            String url = cashfreeConfig.getPgBaseUrl() + "/orders/" + orderId;

            log.info("Getting order details: {}", orderId);

            HttpHeaders headers = createPayinHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<PayinOrderResponseDTO> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, PayinOrderResponseDTO.class);

            log.info("Get order response status: {}", response.getStatusCode());
            return response.getBody();

        } catch (Exception e) {
            log.error("Get order error", e);
            throw new RuntimeException("Get order failed: " + e.getMessage());
        }
    }

    // 3. Update Order
    public PayinOrderResponseDTO updateOrder(String orderId, PayinOrderUpdateDTO updateRequest) {
        try {
            String url = cashfreeConfig.getPgBaseUrl() + "/orders/" + orderId;

            log.info("Updating order {}: {}", orderId, updateRequest);

            HttpHeaders headers = createPayinHeaders();
            HttpEntity<PayinOrderUpdateDTO> entity = new HttpEntity<>(updateRequest, headers);

            ResponseEntity<PayinOrderResponseDTO> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, PayinOrderResponseDTO.class);

            log.info("Update order response status: {}", response.getStatusCode());
            return response.getBody();

        } catch (Exception e) {
            log.error("Update order error", e);
            throw new RuntimeException("Update order failed: " + e.getMessage());
        }
    }

    // 4. Get Extended Order Details
    public PayinExtendedOrderResponseDTO getExtendedOrder(String orderId) {
        try {
            String url = cashfreeConfig.getPgBaseUrl() + "/orders" + orderId + "/extended";

            log.info("Getting extended order details: {}", orderId);

            HttpHeaders headers = createPayinHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<PayinExtendedOrderResponseDTO> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, PayinExtendedOrderResponseDTO.class);

            log.info("Get extended order response status: {}", response.getStatusCode());
            return response.getBody();

        } catch (Exception e) {
            log.error("Get extended order error", e);
            throw new RuntimeException("Get extended order failed: " + e.getMessage());
        }
    }

    // 5. Terminate Order
    public PayinOrderResponseDTO terminateOrder(String orderId) {
        PayinOrderUpdateDTO updateRequest = new PayinOrderUpdateDTO();
        updateRequest.setOrderStatus("TERMINATED");

        return updateOrder(orderId, updateRequest);
    }

    // Headers for PG API (different from Payout)
    private HttpHeaders createPayinHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-client-id", cashfreeConfig.getPgClientId());
        headers.set("x-client-secret", cashfreeConfig.getPgClientSecret());
        headers.set("x-api-version", "2025-01-01");
        headers.set("x-request-id", generateRequestId());

        return headers;
    }

    private String generateRequestId() {
        return "PG_REQ_" + System.currentTimeMillis() + "_" +
                UUID.randomUUID().toString().substring(0, 8);
    }
}
