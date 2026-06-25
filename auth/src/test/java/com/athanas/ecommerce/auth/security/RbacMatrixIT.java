package com.athanas.ecommerce.auth.security;

import com.athanas.ecommerce.auth.testsupport.TestUserFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RbacMatrixIT {

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
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired StringRedisTemplate redisTemplate;

    @BeforeEach
    void clearIpRateLimit() {
        redisTemplate.delete("login:rate:ip:127.0.0.1");
    }

    static Stream<Arguments> rbacMatrix() {
        return Stream.of(
                arguments("USER",   "user-only",   200),
                arguments("USER",   "seller-only", 403),
                arguments("USER",   "admin-only",  403),
                arguments("SELLER", "user-only",   403),
                arguments("SELLER", "seller-only", 200),
                arguments("SELLER", "admin-only",  403),
                arguments("ADMIN",  "user-only",   403),
                arguments("ADMIN",  "seller-only", 403),
                arguments("ADMIN",  "admin-only",  200)
        );
    }

    @ParameterizedTest(name = "{0} → /{1} = {2}")
    @MethodSource("rbacMatrix")
    void testRbacMatrix(String role, String endpoint, int expectedStatus) throws Exception {
        String email = "rbac-" + role.toLowerCase() + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8)
                + "@example.com";

        String token = TestUserFactory.createUserWithRole(email, role, mockMvc, jdbcTemplate);

        mockMvc.perform(get("/v1/_test/" + endpoint)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is(expectedStatus));
    }

    @ParameterizedTest(name = "noToken → /{0} = 401")
    @MethodSource("endpointProvider")
    void shouldReturn401ForMissingToken(String endpoint) throws Exception {
        mockMvc.perform(get("/v1/_test/" + endpoint))
                .andExpect(status().isUnauthorized());
    }

    static Stream<Arguments> endpointProvider() {
        return Stream.of(
                arguments("user-only"),
                arguments("seller-only"),
                arguments("admin-only"),
                arguments("any-authenticated")
        );
    }
}
