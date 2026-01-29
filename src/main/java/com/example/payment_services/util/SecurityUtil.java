package com.example.payment_services.util;

import com.example.payment_services.config.ExternalTokenValidationFilter.UserInfo;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public class SecurityUtil {

    public static Optional<UserInfo> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null &&
                authentication.isAuthenticated() &&
                !"anonymousUser".equals(authentication.getPrincipal()) &&
                authentication.getPrincipal() instanceof UserInfo) {
            return Optional.of((UserInfo) authentication.getPrincipal());
        }

        return Optional.empty();
    }

    public static String getCurrentUserId() {
        return getCurrentUser()
                .map(UserInfo::getUid)
                .orElse(null);
    }

    public static String getCurrentUserRole() {
        return getCurrentUser()
                .map(UserInfo::getRole)
                .orElse(null);
    }
}