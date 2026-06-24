package com.athanas.ecommerce.auth.login;

import com.athanas.ecommerce.auth.token.JwtGenerator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginControllerIT {

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
    @Autowired JwtGenerator jwtGenerator;

    private static final String REGISTER_URL = "/v1/auth/register";
    private static final String LOGIN_URL    = "/v1/auth/login";

    private void register(String email, String password, String name) throws Exception {
        String body = String.format(
                "{\"email\":\"%s\",\"password\":\"%s\",\"fullName\":\"%s\"}", email, password, name);
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private MvcResult login(String email, String password) throws Exception {
        String body = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
        return mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    @Test
    void shouldLoginAfterRegister() throws Exception {
        String email = "loginok@example.com";
        String password = "secret123";

        MvcResult regResult = mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"email\":\"%s\",\"password\":\"%s\",\"fullName\":\"Login Test\"}",
                                email, password)))
                .andExpect(status().isCreated())
                .andReturn();

        String userId = com.jayway.jsonpath.JsonPath.read(
                regResult.getResponse().getContentAsString(), "$.id");

        MvcResult loginResult = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").value("TBD_US005"))
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andReturn();

        String accessToken = com.jayway.jsonpath.JsonPath.read(
                loginResult.getResponse().getContentAsString(), "$.accessToken");

        Jws<Claims> jws = jwtGenerator.parseAndValidate(accessToken);
        assertThat(jws.getPayload().getSubject()).isEqualTo(userId);
        assertThat(jws.getPayload().get("roles", List.class)).contains("USER");
    }

    @Test
    void shouldReturn401OnWrongPassword() throws Exception {
        register("wrongpwd@example.com", "correct123", "Wrong Pwd User");

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"wrongpwd@example.com\",\"password\":\"wrong_password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://athanas.dev/errors/invalid-credentials"));
    }

    @Test
    void shouldReturn401OnUnknownEmail() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://athanas.dev/errors/invalid-credentials"));
    }

    @Test
    void shouldReturn429AfterThreeFailures() throws Exception {
        String email = "ratelimit@example.com";
        register(email, "correct123", "Rate Limit User");

        String wrongBody = String.format(
                "{\"email\":\"%s\",\"password\":\"wrong_password\"}", email);

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(wrongBody))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongBody))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("https://athanas.dev/errors/too-many-attempts"))
                .andExpect(header().exists("Retry-After"));
    }
}
