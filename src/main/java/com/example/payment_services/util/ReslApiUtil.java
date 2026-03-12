package com.example.payment_services.util;

import com.example.payment_services.entity.PaymentTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class ReslApiUtil {

    @Value("${resl.api.base-url}")
    private String baseUrl;

    @Value("${resl.api.key}")
    private String apiKey;

    @Value("${resl.api.payin.endpoint:/api/v1/payment/payin/status/update}")
    private String payinEndpoint;

    @Value("${resl.api.payout.endpoint:/api/v1/payment/payout/status/update}")
    private String payoutEndpoint;

    private final RestTemplate restTemplate;

    public ReslApiUtil() {
        this.restTemplate = new RestTemplate();
    }

    @PostConstruct
    public void init() {
        System.out.println("ReslApiUtil initialized with baseUrl: " + baseUrl);
    }

    /**
     * Call RESL API to update payin status
     */
    public void updatePayinStatus(String orderId,
                                  PaymentTransaction.PaymentStatus status,
                                  String cfPaymentId,
                                  BigDecimal paymentAmount,
                                  String paymentMethod) {

        String url = baseUrl + payinEndpoint;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("orderId", orderId);
        requestBody.put("status", status != null ? status.name() : null);
        requestBody.put("cfPaymentId", cfPaymentId);
        requestBody.put("paymentAmount", paymentAmount);
        requestBody.put("paymentMethod", paymentMethod);
        requestBody.put("transactionType", "PAYIN");
        requestBody.put("timestamp", java.time.LocalDateTime.now().toString());
        log.info("preparing payload Payin for URL: {}, requestBody: {}", url, requestBody);
        callReslApi(url, requestBody, "PAYIN", orderId);
    }

    /**
     * Call RESL API to update payout status
     */
    public void updatePayoutStatus(String transferId,
                                   String status,
                                   String cfTransferId,
                                   BigDecimal transferAmount,
                                   String statusDescription) {

        String url = baseUrl + payoutEndpoint;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("transferId", transferId);
        requestBody.put("status", status);
        requestBody.put("cfTransferId", cfTransferId);
        requestBody.put("transferAmount", transferAmount);
        requestBody.put("statusDescription", statusDescription);
        requestBody.put("transactionType", "PAYOUT");
        requestBody.put("timestamp", java.time.LocalDateTime.now().toString());
        log.info("preparing payload Payout for URL: {}, requestBody: {}", url, requestBody);
        callReslApi(url, requestBody, "PAYOUT", transferId);
    }

    /**
     * Generic method to call RESL API
     */
    private void callReslApi(String url, Map<String, Object> requestBody, String type, String id) {
        try {
            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apiKey);
            headers.set("X-Transaction-Type", type);

            // Create request entity
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // Log request (for debugging)
            log.info("Calling RESL API {} - URL: {}", type, url);
            log.info("Request body: {}", requestBody);

            // Make POST call
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            // Log result
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("RESL API {} success for ID: {}", type, id);
                log.info("Response success: {}" , response.getBody());
            } else {
                log.info("RESL API {} failed for ID: {} - Status: {}", type, id, response.getStatusCode());
                log.info("Response: {}" , response.getBody());
            }

        } catch (Exception e) {
            log.info("RESL API " + type + " error for ID: " + id);
            log.info("Error message: " + e.getMessage());
            e.printStackTrace();
        }
    }
}