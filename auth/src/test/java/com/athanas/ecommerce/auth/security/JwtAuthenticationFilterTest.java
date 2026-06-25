package com.athanas.ecommerce.auth.security;

import com.athanas.ecommerce.auth._testapi.RoleTestController;
import com.athanas.ecommerce.auth.config.SecurityConfig;
import com.athanas.ecommerce.auth.token.AccessTokenBlacklist;
import com.athanas.ecommerce.auth.token.JwtGenerator;
import com.athanas.ecommerce.auth.token.JwtProperties;
import com.athanas.ecommerce.auth.user.User;
import com.athanas.ecommerce.auth.user.UserRepository;
import com.athanas.ecommerce.common.error.ProblemDetailFactory;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RoleTestController.class)
@Import({
    SecurityConfig.class,
    JwtAuthenticationFilter.class,
    JsonAuthenticationEntryPoint.class,
    JsonAccessDeniedHandler.class,
    CurrentUserResolver.class,
    ProblemDetailFactory.class,
    SecurityExceptionHandler.class
})
class JwtAuthenticationFilterTest {

    @Autowired MockMvc mockMvc;

    @MockBean JwtGenerator jwtGenerator;
    @MockBean UserRepository userRepository;
    @MockBean AccessTokenBlacklist accessTokenBlacklist;

    private static final JwtGenerator REAL_GENERATOR;

    static {
        JwtProperties props = new JwtProperties();
        props.setSecret("test_jwt_secret_for_unit_tests_needs_64_plus_bytes_long_for_hs512_algo_ok!");
        props.setAccessTtl(Duration.ofMinutes(15));
        props.setRefreshTtl(Duration.ofDays(7));
        REAL_GENERATOR = new JwtGenerator(props);
    }

    private UUID userId;
    private User activeUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        activeUser = new User();
        activeUser.setId(userId);
        activeUser.setEmail("user@example.com");
        activeUser.setPasswordHash("$2a$12$ignored");
        activeUser.setFullName("Test User");
        activeUser.setEnabled(true);
    }

    @Test
    void shouldRejectMissingAuthHeader() throws Exception {
        mockMvc.perform(get("/v1/_test/user-only"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectMalformedToken() throws Exception {
        when(jwtGenerator.parseAndValidate("not.a.token"))
                .thenThrow(new MalformedJwtException("malformed"));

        mockMvc.perform(get("/v1/_test/user-only")
                        .header("Authorization", "Bearer not.a.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://athanas.dev/errors/invalid-access-token"));
    }

    @Test
    void shouldRejectExpiredToken() throws Exception {
        when(jwtGenerator.parseAndValidate("expired.token.here"))
                .thenThrow(new ExpiredJwtException(null, null, "expired"));

        mockMvc.perform(get("/v1/_test/user-only")
                        .header("Authorization", "Bearer expired.token.here"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://athanas.dev/errors/invalid-access-token"));
    }

    @Test
    void shouldAcceptValidUserToken_accessUserEndpoint() throws Exception {
        String token = REAL_GENERATOR.generateAccessToken(userId, Set.of("USER")).token();
        when(jwtGenerator.parseAndValidate(token)).thenAnswer(inv -> REAL_GENERATOR.parseAndValidate(token));
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));

        mockMvc.perform(get("/v1/_test/user-only")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void shouldRejectUserToken_accessSellerEndpoint() throws Exception {
        String token = REAL_GENERATOR.generateAccessToken(userId, Set.of("USER")).token();
        when(jwtGenerator.parseAndValidate(token)).thenAnswer(inv -> REAL_GENERATOR.parseAndValidate(token));
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));

        mockMvc.perform(get("/v1/_test/seller-only")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectUserToken_accessAdminEndpoint() throws Exception {
        String token = REAL_GENERATOR.generateAccessToken(userId, Set.of("USER")).token();
        when(jwtGenerator.parseAndValidate(token)).thenAnswer(inv -> REAL_GENERATOR.parseAndValidate(token));
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));

        mockMvc.perform(get("/v1/_test/admin-only")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAcceptValidAdminToken_accessAllEndpoints() throws Exception {
        String token = REAL_GENERATOR.generateAccessToken(userId, Set.of("ADMIN")).token();
        when(jwtGenerator.parseAndValidate(token)).thenAnswer(inv -> REAL_GENERATOR.parseAndValidate(token));

        User adminUser = new User();
        adminUser.setId(userId);
        adminUser.setEmail("admin@example.com");
        adminUser.setPasswordHash("$2a$12$ignored");
        adminUser.setFullName("Admin User");
        adminUser.setEnabled(true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(adminUser));

        mockMvc.perform(get("/v1/_test/admin-only")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mockMvc.perform(get("/v1/_test/user-only")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/v1/_test/seller-only")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectRevokedUser() throws Exception {
        String token = REAL_GENERATOR.generateAccessToken(userId, Set.of("USER")).token();
        when(jwtGenerator.parseAndValidate(token)).thenAnswer(inv -> REAL_GENERATOR.parseAndValidate(token));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/_test/user-only")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://athanas.dev/errors/invalid-access-token"));
    }

    @Test
    void shouldRejectDisabledUser() throws Exception {
        String token = REAL_GENERATOR.generateAccessToken(userId, Set.of("USER")).token();
        when(jwtGenerator.parseAndValidate(token)).thenAnswer(inv -> REAL_GENERATOR.parseAndValidate(token));

        User disabledUser = new User();
        disabledUser.setId(userId);
        disabledUser.setEmail("user@example.com");
        disabledUser.setPasswordHash("$2a$12$ignored");
        disabledUser.setFullName("Disabled User");
        disabledUser.setEnabled(false);

        when(userRepository.findById(userId)).thenReturn(Optional.of(disabledUser));

        mockMvc.perform(get("/v1/_test/user-only")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://athanas.dev/errors/invalid-access-token"));
    }
}
