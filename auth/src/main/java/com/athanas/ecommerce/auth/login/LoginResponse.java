package com.athanas.ecommerce.auth.login;

public record LoginResponse(String accessToken, String refreshToken, long expiresIn) {}
