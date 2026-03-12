package com.example.payment_services.util;

import com.example.payment_services.entity.PaymentTransaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

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
            System.out.println("Calling RESL API " + type + " - URL: " + url);
            System.out.println("Request body: " + requestBody);

            // Make POST call
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            // Log result
            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("RESL API " + type + " success for ID: " + id);
                System.out.println("Response: " + response.getBody());
            } else {
                System.err.println("RESL API " + type + " failed for ID: " + id +
                        " - Status: " + response.getStatusCode());
                System.err.println("Response: " + response.getBody());
            }

        } catch (Exception e) {
            System.err.println("RESL API " + type + " error for ID: " + id);
            System.err.println("Error message: " + e.getMessage());
            e.printStackTrace();
        }
    }
}