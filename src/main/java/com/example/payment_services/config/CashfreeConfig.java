package com.example.payment_services.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "cashfree")
public class CashfreeConfig {
    private String clientId;
    private String clientSecret;
    private String payoutBaseUrl;

    public String getPgBaseUrl() {
        return "https://sandbox.cashfree.com/pg";
    }
}