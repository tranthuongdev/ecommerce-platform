package com.athanas.ecommerce.auth.security;

import com.athanas.ecommerce.common.security.PrincipalView;

import java.util.Set;
import java.util.UUID;

public record AuthenticatedUser(UUID userId, String email, Set<String> roleNames)
        implements PrincipalView {}
