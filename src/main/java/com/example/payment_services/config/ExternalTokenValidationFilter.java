package com.example.payment_services.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;

@Component  // Add @Component annotation
public class ExternalTokenValidationFilter extends OncePerRequestFilter {

    // Values from application.properties
    @Value("${app.auth.validation-url}")
    private String validationUrl;

    @Value("${app.auth.timeout-ms}")
    private int timeoutMs;

    @Value("${app.auth.header-name}")
    private String authHeaderName;

    @Value("${app.auth.bearer-prefix}")
    private String bearerPrefix;

    @Value("${app.auth.enabled}")
    private boolean authEnabled;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // Constructor injection
    public ExternalTokenValidationFilter() {
        this.restTemplate = createRestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return new RestTemplate(factory);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String requestURI = request.getRequestURI();
        String method = request.getMethod();

        logger.debug("Processing request: " + method + " " + requestURI);

        // Skip validation for public endpoints
        if (isPublicEndpoint(requestURI, method)) {
            logger.debug("Skipping validation for public endpoint: " + requestURI);
            filterChain.doFilter(request, response);
            return;
        }

        // Check if authentication is enabled
        if (!authEnabled) {
            logger.warn("Authentication is disabled - allowing all requests");
            setAnonymousAuthentication();
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader(authHeaderName);

        if (authHeader == null || !authHeader.startsWith(bearerPrefix + " ")) {
            logger.warn("Missing or invalid Authorization header for: " + requestURI);
            sendUnauthorized(response, "Missing or invalid Authorization header. Format: " + bearerPrefix + " <token>");
            return;
        }

        String token = authHeader.substring((bearerPrefix + " ").length()).trim();

        if (token.isEmpty()) {
            logger.warn("Empty token provided for: " + requestURI);
            sendUnauthorized(response, "Empty token provided");
            return;
        }

        try {
            UserInfo userInfo = validateTokenWithExternalServer(token);

            if (userInfo != null && userInfo.isValid()) {
                logger.info("Token validated successfully for user: " + userInfo.getEmail());

                // Create authentication with user info
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                userInfo, // Principal contains user info
                                null,
                                getAuthorities(userInfo.getRole())
                        );

                // Store additional details
                auth.setDetails(userInfo);
                SecurityContextHolder.getContext().setAuthentication(auth);

                // Add user info to request for controller access
                request.setAttribute("userInfo", userInfo);
                request.setAttribute("userId", userInfo.getUid());
                request.setAttribute("userRole", userInfo.getRole());

                filterChain.doFilter(request, response); // Continue chain
            } else {
                logger.warn("Invalid token for request: " + requestURI);
                sendUnauthorized(response, "Invalid or expired token");
                return; // Return after sending error
            }

        } catch (ResourceAccessException e) {
            logger.error("Auth server timeout/unreachable for: " + requestURI, e);
            sendServiceUnavailable(response, "Authentication service unavailable");
            return; // Return after sending error
        } catch (Exception e) {
            logger.error("Token validation error for: " + requestURI, e);
            sendInternalError(response, "Token validation failed");
            return; // Return after sending error
        }
    }

    private void setAnonymousAuthentication() {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        UserInfo anonymousUser = new UserInfo(
                false,
                "anonymous",
                "USER",
                "anonymous@example.com",
                "",
                "Authentication disabled",
                System.currentTimeMillis()
        );

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        anonymousUser,
                        null,
                        authorities
                );

        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private boolean isPublicEndpoint(String requestURI, String method) {
        // Exact match endpoints (could also be moved to properties)
        if (requestURI.equals("/api/payments/health") && "GET".equals(method)) {
            return true;
        }

        if (requestURI.equals("/api/payments/webhook") && "POST".equals(method)) {
            return true;
        }

        // Swagger/OpenAPI endpoints
        String lowerUri = requestURI.toLowerCase();
        if (lowerUri.startsWith("/swagger-ui") ||
                lowerUri.startsWith("/v3/api-docs") ||
                lowerUri.equals("/swagger-ui.html") ||
                lowerUri.startsWith("/swagger-resources") ||
                lowerUri.startsWith("/configuration/ui") ||
                lowerUri.startsWith("/configuration/security") ||
                lowerUri.startsWith("/webjars/") ||
                lowerUri.equals("/favicon.ico") ||
                lowerUri.equals("/error")) {
            return true;
        }

        // Actuator endpoints
        if (lowerUri.startsWith("/actuator/health") ||
                lowerUri.startsWith("/actuator/info")) {
            return true;
        }

        return false;
    }

    private UserInfo validateTokenWithExternalServer(String token) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.set(authHeaderName, bearerPrefix + " " + token);
        headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>("", headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    validationUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());

                boolean isValid = root.path("valid").asBoolean();
                String message = root.path("message").asText();

                if (isValid && "Token is valid".equalsIgnoreCase(message)) {
                    JsonNode data = root.path("data");

                    return new UserInfo(
                            true,
                            data.path("uid").asText(),
                            data.path("role").asText(),
                            data.path("email").asText(),
                            data.path("mobile").asText(),
                            message,
                            System.currentTimeMillis()
                    );
                } else {
                    logger.debug("Token invalid. Response: " + response.getBody());
                }
            } else {
                logger.warn("Auth server returned status: " + response.getStatusCode());
            }

        } catch (Exception e) {
            if (e.getMessage().contains("401") || e.getMessage().contains("Unauthorized")) {
                logger.warn("Token rejected by auth server");
            } else {
                throw e;
            }
        }

        return null;
    }

    private List<SimpleGrantedAuthority> getAuthorities(String role) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();

        if (role != null && !role.trim().isEmpty()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.trim().toUpperCase()));
        }

        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        return authorities;
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(String.format(
                "{\"status\": \"error\", \"code\": 401, \"message\": \"%s\", \"timestamp\": \"%s\"}",
                message, new Date()
        ));
    }

    private void sendServiceUnavailable(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(String.format(
                "{\"status\": \"error\", \"code\": 503, \"message\": \"%s\", \"timestamp\": \"%s\"}",
                message, new Date()
        ));
    }

    private void sendInternalError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(String.format(
                "{\"status\": \"error\", \"code\": 500, \"message\": \"%s\", \"timestamp\": \"%s\"}",
                message, new Date()
        ));
    }

    // User Info class
    public static class UserInfo {
        private final boolean valid;
        private final String uid;
        private final String role;
        private final String email;
        private final String mobile;
        private final String message;
        private final long validationTime;

        public UserInfo(boolean valid, String uid, String role, String email,
                        String mobile, String message, long validationTime) {
            this.valid = valid;
            this.uid = uid;
            this.role = role;
            this.email = email;
            this.mobile = mobile;
            this.message = message;
            this.validationTime = validationTime;
        }

        public boolean isValid() { return valid; }
        public String getUid() { return uid; }
        public String getRole() { return role; }
        public String getEmail() { return email; }
        public String getMobile() { return mobile; }
        public String getMessage() { return message; }
        public long getValidationTime() { return validationTime; }

        @Override
        public String toString() {
            return String.format("UserInfo{uid='%s', role='%s', email='%s'}", uid, role, email);
        }
    }
}