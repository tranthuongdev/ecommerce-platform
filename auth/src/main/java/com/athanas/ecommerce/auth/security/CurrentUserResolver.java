package com.athanas.ecommerce.auth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CurrentUserResolver {

    public Optional<AuthenticatedUser> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof AuthenticatedUser)) {
            return Optional.empty();
        }
        return Optional.of((AuthenticatedUser) auth.getPrincipal());
    }

    public AuthenticatedUser requireCurrentUser() {
        return getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("No authenticated user in SecurityContext"));
    }
}
