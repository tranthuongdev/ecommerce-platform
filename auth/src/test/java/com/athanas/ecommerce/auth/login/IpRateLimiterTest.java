package com.athanas.ecommerce.auth.login;

import com.athanas.ecommerce.common.error.TooManyAttemptsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataRedisTest
@Testcontainers
@Import(IpRateLimiter.class)
class IpRateLimiterTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired StringRedisTemplate redisTemplate;
    @Autowired IpRateLimiter rateLimiter;

    @BeforeEach
    void flushRedis() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void shouldAllowFirstFiveRequests() {
        for (int i = 0; i < IpRateLimiter.MAX_REQUESTS; i++) {
            assertThatCode(() -> rateLimiter.checkAndIncrement("1.1.1.1"))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void shouldBlockSixthRequest() {
        for (int i = 0; i < IpRateLimiter.MAX_REQUESTS; i++) {
            rateLimiter.checkAndIncrement("1.1.1.2");
        }

        assertThatThrownBy(() -> rateLimiter.checkAndIncrement("1.1.1.2"))
                .isInstanceOf(TooManyAttemptsException.class)
                .satisfies(ex -> {
                    TooManyAttemptsException tooMany = (TooManyAttemptsException) ex;
                    assertThat(tooMany.getRetryAfter().toSeconds())
                            .isGreaterThan(0)
                            .isLessThanOrEqualTo(IpRateLimiter.WINDOW_SECONDS);
                });
    }

    @Test
    void shouldIsolateByIp() {
        for (int i = 0; i < IpRateLimiter.MAX_REQUESTS; i++) {
            rateLimiter.checkAndIncrement("1.1.1.3");
        }

        for (int i = 0; i < IpRateLimiter.MAX_REQUESTS; i++) {
            assertThatCode(() -> rateLimiter.checkAndIncrement("2.2.2.2"))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void shouldResetAfterWindowExpiry() {
        for (int i = 0; i < IpRateLimiter.MAX_REQUESTS; i++) {
            rateLimiter.checkAndIncrement("1.1.1.4");
        }
        assertThatThrownBy(() -> rateLimiter.checkAndIncrement("1.1.1.4"))
                .isInstanceOf(TooManyAttemptsException.class);

        redisTemplate.delete("login:rate:ip:1.1.1.4");

        assertThatCode(() -> rateLimiter.checkAndIncrement("1.1.1.4"))
                .doesNotThrowAnyException();
    }
}
