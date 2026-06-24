package com.athanas.ecommerce.auth.token;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

@Component
public class JwtGenerator {

    private final JwtProperties properties;

    public JwtGenerator(JwtProperties properties) {
        this.properties = properties;
    }

    public String generateAccessToken(UUID userId, Set<String> roleNames) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("roles", roleNames)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getAccessTtl())))
                .signWith(signingKey())
                .compact();
    }

    public Jws<Claims> parseAndValidate(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token);
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
