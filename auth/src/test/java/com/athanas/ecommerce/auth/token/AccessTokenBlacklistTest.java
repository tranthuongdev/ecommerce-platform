package com.athanas.ecommerce.auth.token;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessTokenBlacklistTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    private AccessTokenBlacklist blacklist;

    @BeforeEach
    void setUp() {
        blacklist = new AccessTokenBlacklist(redisTemplate);
    }

    @Test
    void shouldBlacklistAndDetect() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        UUID jti = UUID.randomUUID();
        String expectedKey = "jwt:blacklist:" + jti;
        Instant future = Instant.now().plusSeconds(300);

        blacklist.blacklist(jti, future);

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOps).set(eq(expectedKey), eq("1"), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue().toSeconds()).isGreaterThan(0).isLessThanOrEqualTo(300);

        when(redisTemplate.hasKey(expectedKey)).thenReturn(true);
        assertThat(blacklist.isBlacklisted(jti)).isTrue();
    }

    @Test
    void shouldNotBlacklistExpiredToken() {
        UUID jti = UUID.randomUUID();
        Instant past = Instant.now().minusSeconds(1);

        blacklist.blacklist(jti, past);

        verify(valueOps, never()).set(eq("jwt:blacklist:" + jti), eq("1"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldNotBlacklistTokenExpiringNow() {
        UUID jti = UUID.randomUUID();
        Instant now = Instant.now();

        blacklist.blacklist(jti, now);

        verify(valueOps, never()).set(eq("jwt:blacklist:" + jti), eq("1"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldReturnFalseWhenNotBlacklisted() {
        UUID jti = UUID.randomUUID();
        when(redisTemplate.hasKey("jwt:blacklist:" + jti)).thenReturn(false);

        assertThat(blacklist.isBlacklisted(jti)).isFalse();
    }

    @Test
    void shouldReturnFalseWhenKeyDoesNotExist() {
        UUID jti = UUID.randomUUID();
        when(redisTemplate.hasKey("jwt:blacklist:" + jti)).thenReturn(null);

        assertThat(blacklist.isBlacklisted(jti)).isFalse();
    }
}
