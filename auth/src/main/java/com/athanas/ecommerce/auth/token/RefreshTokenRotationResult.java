package com.athanas.ecommerce.auth.token;

import java.util.UUID;

public record RefreshTokenRotationResult(UUID userId, String newRefreshTokenPlain) {}
