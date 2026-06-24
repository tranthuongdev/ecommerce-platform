package com.athanas.ecommerce.common.error;

import lombok.Getter;

import java.util.UUID;

@Getter
public class RefreshTokenReuseException extends RuntimeException {

    private final UUID userId;

    public RefreshTokenReuseException(UUID userId) {
        super("Refresh token reuse detected");
        this.userId = userId;
    }
}
