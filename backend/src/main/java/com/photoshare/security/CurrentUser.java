package com.photoshare.security;

import com.photoshare.common.ApiException;
import com.photoshare.user.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    public Principal require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw ApiException.unauthorized("AUTHENTICATION_REQUIRED", "Authentication is required");
        }
        try {
            return new Principal(Long.parseLong(jwt.getSubject()), UserRole.valueOf(jwt.getClaimAsString("role")));
        } catch (RuntimeException ex) {
            throw ApiException.unauthorized("INVALID_TOKEN", "Authentication token is invalid");
        }
    }

    public record Principal(long id, UserRole role) {}
}

