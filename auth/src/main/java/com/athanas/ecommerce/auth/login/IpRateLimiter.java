package com.athanas.ecommerce.auth.login;

import com.athanas.ecommerce.common.error.TooManyAttemptsException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class IpRateLimiter {

    static final int MAX_REQUESTS = 5;
    static final int WINDOW_SECONDS = 60;
    private static final String KEY_PREFIX = "login:rate:ip:";

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
        Long count = redisTemplate.execute(INCR_SCRIPT, List.of(key), String.valueOf(WINDOW_SECONDS));
        if (count != null && count > MAX_REQUESTS) {
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            long retryAfter = (ttl != null && ttl > 0) ? ttl : WINDOW_SECONDS;
            throw new TooManyAttemptsException(Duration.ofSeconds(retryAfter));
        }
    }
}
