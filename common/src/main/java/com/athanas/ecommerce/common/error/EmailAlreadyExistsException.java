package com.athanas.ecommerce.common.error;

import lombok.Getter;

@Getter
public class EmailAlreadyExistsException extends RuntimeException {

    private final String email;

    public EmailAlreadyExistsException(String email) {
        super("Email already registered: " + email);
        this.email = email;
    }
}
