package com.athanas.ecommerce.auth.token;

import com.athanas.ecommerce.common.util.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class LogoutService {

    private final AccessTokenBlacklist accessTokenBlacklist;
    private final RefreshTokenRepository refreshTokenRepository;

    public void logout(UUID userId, UUID accessJti, Instant accessExp, String refreshTokenPlain) {
        accessTokenBlacklist.blacklist(accessJti, accessExp);

        if (refreshTokenPlain != null) {
            String tokenHash = HashUtil.sha256Hex(refreshTokenPlain);
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresentOrElse(
                    token -> {
                        if (token.getUserId().equals(userId) && token.getRevokedAt() == null) {
                            refreshTokenRepository.delete(token);
                        } else {
                            log.warn("Logout: refresh token mismatch or already revoked for userId={}", userId);
                        }
                    },
                    () -> log.warn("Logout: refresh token not found for userId={}", userId)
            );
        }

        log.info("User {} logged out, jti={}", userId, accessJti);
    }
}
