package com.athanas.ecommerce.product.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Enables @PreAuthorize / @PostAuthorize for the product module.
 * No SecurityFilterChain here — the full app uses auth module's chain which
 * includes JwtAuthenticationFilter. Product module tests supply their own
 * test-scoped filter chain via ProductTestSecurityConfig.
 */
@Configuration
@EnableMethodSecurity
public class ProductSecurityConfig {
}
