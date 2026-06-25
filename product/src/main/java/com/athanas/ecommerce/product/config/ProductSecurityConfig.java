package com.athanas.ecommerce.product.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Enables method-level security (@PreAuthorize) for product module.
 * URL-level security and JWT filter chain are managed by auth module's SecurityConfig.
 */
@Configuration
@EnableMethodSecurity
public class ProductSecurityConfig {
}
