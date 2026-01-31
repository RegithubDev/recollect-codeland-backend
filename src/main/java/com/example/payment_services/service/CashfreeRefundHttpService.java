package com.example.payment_services.service;

import com.example.payment_services.config.CashfreeConfig;
import com.example.payment_services.dto.refund.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class CashfreeRefundHttpService {

    private final RestTemplate restTemplate;
    private final CashfreeConfig cashfreeConfig;

    // ========== CREATE REFUND ==========
    public RefundResponseDTO createRefund(String orderId, RefundRequestDTO request) {
        try {
            String url = cashfreeConfig.getPgBaseUrl() + "/orders/" + orderId + "/refunds";

            log.info("Creating refund for order {}: {}", orderId, request);

            HttpHeaders headers = createPayinHeaders();
            HttpEntity<RefundRequestDTO> entity = new HttpEntity<>(request, headers);

            ResponseEntity<RefundResponseDTO> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, RefundResponseDTO.class);

            log.info("Create refund response status: {}", response.getStatusCode());

            if (response.getStatusCode() == HttpStatus.OK ||
                    response.getStatusCode() == HttpStatus.CREATED) {
                return response.getBody();
            }
            throw new RuntimeException("Failed to create refund: " + response.getStatusCode());

        } catch (Exception e) {
            log.error("Create refund error", e);
            throw new RuntimeException("Refund creation failed: " + e.getMessage());
        }
    }

    // ========== GET REFUND DETAILS ==========
    public RefundResponseDTO getRefund(String orderId, String refundId) {
        try {
            String url = cashfreeConfig.getPgBaseUrl() + "/orders/" + orderId + "/refunds/" + refundId;

            log.info("Getting refund details: orderId={}, refundId={}", orderId, refundId);

            HttpHeaders headers = createPayinHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<RefundResponseDTO> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, RefundResponseDTO.class);

            log.info("Get refund response status: {}", response.getStatusCode());
            return response.getBody();

        } catch (Exception e) {
            log.error("Get refund error", e);
            throw new RuntimeException("Get refund failed: " + e.getMessage());
        }
    }

    // ========== GET ALL REFUNDS FOR ORDER ==========
    public RefundResponseDTO[] getAllRefunds(String orderId) {
        try {
            String url = cashfreeConfig.getPgBaseUrl() + "/orders/" + orderId + "/refunds";

            log.info("Getting all refunds for order: {}", orderId);

            HttpHeaders headers = createPayinHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<RefundResponseDTO[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, RefundResponseDTO[].class);

            log.info("Get all refunds response status: {}", response.getStatusCode());
            return response.getBody();

        } catch (Exception e) {
            log.error("Get all refunds error", e);
            throw new RuntimeException("Get all refunds failed: " + e.getMessage());
        }
    }

    // ========== HEADERS FOR PG API ==========
    private HttpHeaders createPayinHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-client-id", cashfreeConfig.getPgClientId());
        headers.set("x-client-secret", cashfreeConfig.getPgClientSecret());
        headers.set("x-api-version", "2025-01-01");

        return headers;
    }
}