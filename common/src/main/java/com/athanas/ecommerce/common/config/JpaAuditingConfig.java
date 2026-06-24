package com.athanas.ecommerce.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;
import java.util.UUID;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<UUID> auditorAware() {
        // TODO Sprint 2: replace with SecurityContextHolder.getContext().getAuthentication()
        //  to populate created_by / updated_by from the authenticated user's UUID.
        return Optional::empty;
    }
}
