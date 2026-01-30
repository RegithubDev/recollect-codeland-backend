package com.example.payment_services.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import java.io.IOException;

@Slf4j
class LoggingInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {

        log.debug("Request: {} {}", request.getMethod(), request.getURI());
        log.debug("Headers: {}", request.getHeaders());

        if (body.length > 0) {
            log.debug("Body: {}", new String(body));
        }

        ClientHttpResponse response = execution.execute(request, body);

        log.debug("Response: {} - {}", response.getStatusCode(), response.getStatusText());

        return response;
    }
}