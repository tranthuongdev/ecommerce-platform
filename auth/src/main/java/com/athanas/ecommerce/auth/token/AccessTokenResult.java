package com.athanas.ecommerce.auth.token;

import java.time.Instant;
import java.util.UUID;

public record AccessTokenResult(String token, UUID jti, Instant expiresAt) {}
