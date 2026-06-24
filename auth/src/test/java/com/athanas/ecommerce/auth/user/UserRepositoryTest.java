package com.athanas.ecommerce.auth.user;

import com.athanas.ecommerce.common.config.JpaAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class UserRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    TestEntityManager em;

    @Test
    void shouldSaveUserWithDefaultRole() {
        Role userRole = roleRepository.findByName("USER").orElseThrow();

        User user = new User();
        user.setEmail("test@example.com");
        user.setPasswordHash("hash1234");
        user.setFullName("Test User");
        user.getRoles().add(userRole);

        User saved = userRepository.saveAndFlush(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getVersion()).isEqualTo(0L);
        assertThat(saved.getRoles()).hasSize(1);
    }

    @Test
    void shouldRejectDuplicateEmailCaseInsensitive() {
        Role userRole = roleRepository.findByName("USER").orElseThrow();

        User user1 = new User();
        user1.setEmail("test@example.com");
        user1.setPasswordHash("hash1234");
        user1.setFullName("First User");
        user1.getRoles().add(userRole);
        userRepository.saveAndFlush(user1);

        assertThatThrownBy(() -> {
            User user2 = new User();
            user2.setEmail("TEST@EXAMPLE.COM");
            user2.setPasswordHash("hash5678");
            user2.setFullName("Second User");
            user2.getRoles().add(userRole);
            userRepository.saveAndFlush(user2);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectInvalidEmailFormat() {
        Role userRole = roleRepository.findByName("USER").orElseThrow();

        assertThatThrownBy(() -> {
            User user = new User();
            user.setEmail("not-an-email");
            user.setPasswordHash("hash1234");
            user.setFullName("Test User");
            user.getRoles().add(userRole);
            userRepository.saveAndFlush(user);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void softDeletedUserShouldNotBeFound() {
        Role userRole = roleRepository.findByName("USER").orElseThrow();

        User user = new User();
        user.setEmail("active@example.com");
        user.setPasswordHash("hash1234");
        user.setFullName("Active User");
        user.getRoles().add(userRole);
        User saved = userRepository.saveAndFlush(user);

        UUID savedId = saved.getId();

        saved.setDeletedAt(Instant.now());
        userRepository.saveAndFlush(saved);
        em.clear();

        Optional<User> result = userRepository.findById(savedId);
        assertThat(result).isEmpty();
    }
}
