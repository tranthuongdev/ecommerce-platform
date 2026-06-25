package com.athanas.ecommerce.product.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Test-only security filter chain for product module integration tests.
 * Only activated in servlet web contexts (i.e. @SpringBootTest with RANDOM_PORT).
 * webEnvironment=NONE tests (ProductServiceIT, N1QueryAuditIT) do not load this.
 * Production JAR: this class is in test scope and excluded from the packaged artifact.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ProductTestSecurityConfig {

    @Bean
    @Order(10)
    public SecurityFilterChain productTestSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/v1/products/**", "/v1/admin/products/**")
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.GET, "/v1/products", "/v1/products/**").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }
}
