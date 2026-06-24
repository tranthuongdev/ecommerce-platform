package com.athanas.ecommerce.auth.token;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtGeneratorTest {

    private static final String TEST_SECRET =
            "test_secret_at_least_256_bits_long_for_hs256_padding_xxxx";

    private JwtGenerator generator;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(TEST_SECRET);
        props.setAccessTtl(Duration.ofMinutes(15));
        props.setRefreshTtl(Duration.ofDays(7));
        generator = new JwtGenerator(props);
    }

    @Test
    void shouldGenerateValidAccessToken() {
        UUID userId = UUID.randomUUID();
        Set<String> roles = Set.of("USER", "ADMIN");

        String token = generator.generateAccessToken(userId, roles);
        Jws<Claims> jws = generator.parseAndValidate(token);

        assertThat(jws.getPayload().getSubject()).isEqualTo(userId.toString());
        assertThat(jws.getPayload().get("roles", List.class))
                .containsExactlyInAnyOrder("USER", "ADMIN");
    }

    @Test
    void shouldRejectTamperedToken() {
        String token = generator.generateAccessToken(UUID.randomUUID(), Set.of("USER"));
        String tampered = token.substring(0, token.length() - 1) + "X";

        assertThatThrownBy(() -> generator.parseAndValidate(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void shouldRejectExpiredToken() throws InterruptedException {
        JwtProperties expiredProps = new JwtProperties();
        expiredProps.setSecret(TEST_SECRET);
        expiredProps.setAccessTtl(Duration.ZERO);
        expiredProps.setRefreshTtl(Duration.ofDays(7));
        JwtGenerator expiredGenerator = new JwtGenerator(expiredProps);

        String token = expiredGenerator.generateAccessToken(UUID.randomUUID(), Set.of("USER"));
        Thread.sleep(100);

        assertThatThrownBy(() -> expiredGenerator.parseAndValidate(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void shouldRejectWrongSignature() {
        String token = generator.generateAccessToken(UUID.randomUUID(), Set.of("USER"));

        JwtProperties otherProps = new JwtProperties();
        otherProps.setSecret("other_secret_at_least_256_bits_long_for_hs256_xxxx_different_key");
        otherProps.setAccessTtl(Duration.ofMinutes(15));
        otherProps.setRefreshTtl(Duration.ofDays(7));
        JwtGenerator otherGenerator = new JwtGenerator(otherProps);

        assertThatThrownBy(() -> otherGenerator.parseAndValidate(token))
                .isInstanceOf(SignatureException.class);
    }
}
