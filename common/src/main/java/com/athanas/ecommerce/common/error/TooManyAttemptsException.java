package com.athanas.ecommerce.common.error;

import lombok.Getter;

import java.time.Duration;

@Getter
public class TooManyAttemptsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyAttemptsException(Duration retryAfter) {
        super("Too many login attempts");
        this.retryAfter = retryAfter;
    }
}
