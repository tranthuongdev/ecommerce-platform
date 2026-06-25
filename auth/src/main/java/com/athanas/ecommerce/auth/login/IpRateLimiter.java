package com.athanas.ecommerce.auth.login;

import com.athanas.ecommerce.common.error.TooManyAttemptsException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class IpRateLimiter {

    // Default values kept as constants so existing tests compile without changes.
    static final int MAX_REQUESTS = 5;
    static final int WINDOW_SECONDS = 60;
    private static final String KEY_PREFIX = "login:rate:ip:";

    @Value("${app.login.rate-limit.max-requests:5}")
    private int maxRequests;

    @Value("${app.login.rate-limit.window-seconds:60}")
    private int windowSeconds;

    private static final DefaultRedisScript<Long> INCR_SCRIPT;

    static {
        INCR_SCRIPT = new DefaultRedisScript<>();
        INCR_SCRIPT.setScriptText(
                "local current = redis.call('INCR', KEYS[1])\n" +
                "if current == 1 then\n" +
                "  redis.call('EXPIRE', KEYS[1], ARGV[1])\n" +
                "end\n" +
                "return current"
        );
        INCR_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;

    public void checkAndIncrement(String ip) {
        String key = KEY_PREFIX + ip;
        Long count = redisTemplate.execute(INCR_SCRIPT, List.of(key), String.valueOf(windowSeconds));
        if (count != null && count > maxRequests) {
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            long retryAfter = (ttl != null && ttl > 0) ? ttl : windowSeconds;
            throw new TooManyAttemptsException(Duration.ofSeconds(retryAfter));
        }
    }
}
