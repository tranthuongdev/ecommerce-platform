package com.athanas.ecommerce.auth.login;

import com.athanas.ecommerce.common.error.TooManyAttemptsException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class LoginAttemptTracker {

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final StringRedisTemplate redis;

    public void checkBlocked(String key) {
        String value = redis.opsForValue().get(key);
        if (value != null && Integer.parseInt(value) >= MAX_ATTEMPTS) {
            throw new TooManyAttemptsException(WINDOW);
        }
    }

    public void recordFailure(String key) {
        Long count = redis.opsForValue().increment(key);
        if (Long.valueOf(1L).equals(count)) {
            redis.expire(key, WINDOW);
        }
    }

    public void clearOnSuccess(String key) {
        redis.delete(key);
    }
}
