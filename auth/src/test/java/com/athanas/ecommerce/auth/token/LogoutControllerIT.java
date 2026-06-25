package com.athanas.ecommerce.auth.token;

import com.jayway.jsonpath.JsonPath;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LogoutControllerIT {

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

    private static final String REGISTER_URL = "/v1/auth/register";
    private static final String LOGIN_URL    = "/v1/auth/login";
    private static final String LOGOUT_URL   = "/v1/auth/logout";
    private static final String REFRESH_URL  = "/v1/auth/refresh";

    record Tokens(String access, String refresh) {}

    private Tokens registerAndLogin(String email) throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"email\":\"%s\",\"password\":\"Test1234!\",\"fullName\":\"Logout Test\"}",
                                email)))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"email\":\"%s\",\"password\":\"Test1234!\"}", email)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        return new Tokens(JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"));
    }

    @Test
    void shouldLogoutSuccessfully() throws Exception {
        Tokens tokens = registerAndLogin("logout-ok@example.com");

        mockMvc.perform(post(LOGOUT_URL)
                        .header("Authorization", "Bearer " + tokens.access())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"refreshToken\":\"%s\"}", tokens.refresh())))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldRejectRevokedAccessTokenAfterLogout() throws Exception {
        Tokens tokens = registerAndLogin("logout-access@example.com");

        mockMvc.perform(post(LOGOUT_URL)
                        .header("Authorization", "Bearer " + tokens.access())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"refreshToken\":\"%s\"}", tokens.refresh())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/_test/user-only")
                        .header("Authorization", "Bearer " + tokens.access()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://athanas.dev/errors/token-revoked"));
    }

    @Test
    void shouldRejectRevokedRefreshAfterLogout() throws Exception {
        Tokens tokens = registerAndLogin("logout-refresh@example.com");

        mockMvc.perform(post(LOGOUT_URL)
                        .header("Authorization", "Bearer " + tokens.access())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"refreshToken\":\"%s\"}", tokens.refresh())))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(REFRESH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"refreshToken\":\"%s\"}", tokens.refresh())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://athanas.dev/errors/invalid-refresh-token"));
    }

    @Test
    void shouldLogoutWithoutRefreshTokenBody() throws Exception {
        Tokens tokens = registerAndLogin("logout-nobody@example.com");

        mockMvc.perform(post(LOGOUT_URL)
                        .header("Authorization", "Bearer " + tokens.access()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/_test/user-only")
                        .header("Authorization", "Bearer " + tokens.access()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://athanas.dev/errors/token-revoked"));
    }

    @Test
    void shouldRejectLogoutWithoutAuth() throws Exception {
        mockMvc.perform(post(LOGOUT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
