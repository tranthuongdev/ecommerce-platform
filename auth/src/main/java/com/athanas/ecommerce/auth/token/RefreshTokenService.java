package com.athanas.ecommerce.auth.token;

import com.athanas.ecommerce.common.error.InvalidRefreshTokenException;
import com.athanas.ecommerce.common.error.RefreshTokenReuseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public String issue(UUID userId) {
        String tokenPlain = generateTokenPlain();
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(sha256Hex(tokenPlain));
        token.setExpiresAt(Instant.now().plus(jwtProperties.getRefreshTtl()));
        refreshTokenRepository.save(token);
        return tokenPlain;
    }

    @Transactional(noRollbackFor = RefreshTokenReuseException.class)
    public RefreshTokenRotationResult rotate(String oldTokenPlain) {
        String oldHash = sha256Hex(oldTokenPlain);
        RefreshToken old = refreshTokenRepository.findByTokenHash(oldHash)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (old.getRevokedAt() != null) {
            refreshTokenRepository.revokeAllByUserId(old.getUserId(), Instant.now());
            throw new RefreshTokenReuseException(old.getUserId());
        }

        if (!old.isActive()) {
            throw new InvalidRefreshTokenException();
        }

        String newTokenPlain = generateTokenPlain();
        RefreshToken newToken = new RefreshToken();
        newToken.setUserId(old.getUserId());
        newToken.setTokenHash(sha256Hex(newTokenPlain));
        newToken.setExpiresAt(Instant.now().plus(jwtProperties.getRefreshTtl()));
        refreshTokenRepository.saveAndFlush(newToken);

        old.setRevokedAt(Instant.now());
        old.setReplacedBy(newToken.getId());
        refreshTokenRepository.save(old);

        return new RefreshTokenRotationResult(old.getUserId(), newTokenPlain);
    }

    private String generateTokenPlain() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
