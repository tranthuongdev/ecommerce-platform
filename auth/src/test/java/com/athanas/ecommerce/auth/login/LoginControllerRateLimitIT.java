package com.athanas.ecommerce.auth.login;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginControllerRateLimitIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired MockMvc mockMvc;
    @Autowired StringRedisTemplate redisTemplate;

    private static final String REGISTER_URL = "/v1/auth/register";
    private static final String LOGIN_URL    = "/v1/auth/login";
    private static final String PASSWORD     = "Test1234!";

    @BeforeEach
    void flushRateLimitKeys() {
        var keys = redisTemplate.keys("login:rate:ip:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"email\":\"%s\",\"password\":\"%s\",\"fullName\":\"Rate Test\"}", email, PASSWORD)))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldReturn429AfterFiveSuccessfulAttempts() throws Exception {
        register("rate-success@example.com");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post(LOGIN_URL)
                            .header("X-Forwarded-For", "10.0.1.1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format("{\"email\":\"rate-success@example.com\",\"password\":\"%s\"}", PASSWORD)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post(LOGIN_URL)
                        .header("X-Forwarded-For", "10.0.1.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"email\":\"rate-success@example.com\",\"password\":\"%s\"}", PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("https://athanas.dev/errors/too-many-attempts"))
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void shouldReturn429EvenForFailedLogins() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post(LOGIN_URL)
                            .header("X-Forwarded-For", "10.0.1.2")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"nobody-" + i + "@example.com\",\"password\":\"wrong\"}"))
                    .andReturn();
        }

        mockMvc.perform(post(LOGIN_URL)
                        .header("X-Forwarded-For", "10.0.1.2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("https://athanas.dev/errors/too-many-attempts"));
    }

    @Test
    void shouldReturn429BeforeBcryptVerify() throws Exception {
        register("rate-bcrypt@example.com");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post(LOGIN_URL)
                            .header("X-Forwarded-For", "10.0.1.3")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format("{\"email\":\"rate-bcrypt@example.com\",\"password\":\"%s\"}", PASSWORD)))
                    .andReturn();
        }

        long start = System.nanoTime();
        mockMvc.perform(post(LOGIN_URL)
                        .header("X-Forwarded-For", "10.0.1.3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"email\":\"rate-bcrypt@example.com\",\"password\":\"%s\"}", PASSWORD)))
                .andExpect(status().isTooManyRequests());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).as("Rate limit should fire before bcrypt (< 200ms)").isLessThan(200);
    }

    @Test
    void shouldIsolateCountersByIp() throws Exception {
        register("rate-iso@example.com");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post(LOGIN_URL)
                            .header("X-Forwarded-For", "10.0.2.1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format("{\"email\":\"rate-iso@example.com\",\"password\":\"%s\"}", PASSWORD)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post(LOGIN_URL)
                        .header("X-Forwarded-For", "10.0.2.2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"email\":\"rate-iso@example.com\",\"password\":\"%s\"}", PASSWORD)))
                .andExpect(status().isOk());
    }
}
