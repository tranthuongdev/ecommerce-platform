package com.athanas.ecommerce.auth.token;

import com.athanas.ecommerce.auth.user.Role;
import com.athanas.ecommerce.auth.user.RoleRepository;
import com.athanas.ecommerce.auth.user.User;
import com.athanas.ecommerce.auth.user.UserRepository;
import com.athanas.ecommerce.common.error.InvalidRefreshTokenException;
import com.athanas.ecommerce.common.error.RefreshTokenReuseException;
import com.athanas.ecommerce.common.util.HashUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("test")
class RefreshTokenServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired RefreshTokenService refreshTokenService;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private UUID createUser() {
        Role userRole = roleRepository.findByName("USER").orElseThrow();
        User user = new User();
        user.setEmail("rttest_" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("password"));
        user.setFullName("RT Test User");
        user.setEnabled(true);
        user.getRoles().add(userRole);
        return userRepository.saveAndFlush(user).getId();
    }

    @Test
    void shouldIssueTokenWithHashedStorage() {
        UUID userId = createUser();
        String tokenPlain = refreshTokenService.issue(userId);

        assertThat(tokenPlain).isNotBlank().hasSize(43);

        String expectedHash = HashUtil.sha256Hex(tokenPlain);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(expectedHash).orElseThrow();

        assertThat(stored.getTokenHash()).isEqualTo(expectedHash);
        assertThat(stored.getTokenHash()).doesNotContain(tokenPlain);
        assertThat(stored.getUserId()).isEqualTo(userId);
        assertThat(stored.getExpiresAt()).isAfter(Instant.now().plusSeconds(3600));
        assertThat(stored.getRevokedAt()).isNull();
    }

    @Test
    void shouldRotateValidToken() {
        UUID userId = createUser();
        String oldPlain = refreshTokenService.issue(userId);

        RefreshTokenRotationResult result = refreshTokenService.rotate(oldPlain);

        assertThat(result.newRefreshTokenPlain()).isNotBlank().isNotEqualTo(oldPlain);
        assertThat(result.userId()).isEqualTo(userId);

        RefreshToken oldStored = refreshTokenRepository
                .findByTokenHash(HashUtil.sha256Hex(oldPlain)).orElseThrow();
        assertThat(oldStored.getRevokedAt()).isNotNull();

        RefreshToken newStored = refreshTokenRepository
                .findByTokenHash(HashUtil.sha256Hex(result.newRefreshTokenPlain())).orElseThrow();
        assertThat(oldStored.getReplacedBy()).isEqualTo(newStored.getId());
    }

    @Test
    void shouldDetectReuseAndRevokeAll() {
        UUID userId = createUser();
        String t1Plain = refreshTokenService.issue(userId);
        String t2Plain = refreshTokenService.issue(userId);

        refreshTokenService.rotate(t1Plain);

        assertThatThrownBy(() -> refreshTokenService.rotate(t1Plain))
                .isInstanceOf(RefreshTokenReuseException.class);

        RefreshToken t2Stored = refreshTokenRepository
                .findByTokenHash(HashUtil.sha256Hex(t2Plain)).orElseThrow();
        assertThat(t2Stored.getRevokedAt()).isNotNull();
    }

    @Test
    void shouldRejectExpiredToken() {
        UUID userId = createUser();
        String tokenPlain = "test-expired-token-" + UUID.randomUUID();
        String tokenHash = HashUtil.sha256Hex(tokenPlain);

        RefreshToken expired = new RefreshToken();
        expired.setUserId(userId);
        expired.setTokenHash(tokenHash);
        expired.setExpiresAt(Instant.now().minusSeconds(60));
        refreshTokenRepository.saveAndFlush(expired);

        assertThatThrownBy(() -> refreshTokenService.rotate(tokenPlain))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void shouldRejectUnknownToken() {
        assertThatThrownBy(() -> refreshTokenService.rotate("random-garbage-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }
}
