package com.athanas.ecommerce.auth.testsupport;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public final class TestUserFactory {

    private static final String REGISTER_URL = "/v1/auth/register";
    private static final String LOGIN_URL = "/v1/auth/login";
    private static final String DEFAULT_PASSWORD = "Test1234!";

    private TestUserFactory() {}

    public static String createUserWithRole(String email, String roleName,
                                             MockMvc mockMvc, JdbcTemplate jdbcTemplate) throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"email\":\"%s\",\"password\":\"%s\",\"fullName\":\"Test %s\"}",
                                email, DEFAULT_PASSWORD, roleName)))
                .andExpect(status().isCreated());

        jdbcTemplate.update(
                "DELETE FROM user_roles WHERE user_id = (SELECT id FROM users WHERE email = LOWER(?))",
                email);
        jdbcTemplate.update("""
                INSERT INTO user_roles (user_id, role_id)
                SELECT u.id, r.id FROM users u, roles r
                WHERE LOWER(u.email) = LOWER(?) AND r.name = ?
                """, email, roleName);

        MvcResult loginResult = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, DEFAULT_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");
    }
}
