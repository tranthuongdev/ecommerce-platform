package com.athanas.ecommerce.auth.registration;

import com.athanas.ecommerce.auth.user.Role;
import com.athanas.ecommerce.auth.user.RoleRepository;
import com.athanas.ecommerce.auth.user.User;
import com.athanas.ecommerce.auth.user.UserRepository;
import com.athanas.ecommerce.common.error.EmailAlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks RegistrationService service;

    private static final RegistrationRequest VALID_REQ =
            new RegistrationRequest("user@example.com", "secret123", "Test User");

    @Test
    void shouldRegisterNewUser() {
        Role userRole = new Role();
        userRole.setId(UUID.randomUUID());
        userRole.setName("USER");

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(userRole));
        when(passwordEncoder.encode("secret123")).thenReturn("encoded_hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            u.setCreatedAt(Instant.now());
            return u;
        });

        RegistrationResponse resp = service.register(VALID_REQ);

        assertThat(resp.id()).isNotNull();
        assertThat(resp.email()).isEqualTo("user@example.com");
        assertThat(resp.fullName()).isEqualTo("Test User");
        assertThat(resp.createdAt()).isNotNull();
    }

    @Test
    void shouldThrowWhenEmailExists() {
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.register(VALID_REQ))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("user@example.com");
    }

    @Test
    void shouldThrowWhenUserRoleMissing() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(roleRepository.findByName("USER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(VALID_REQ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("seed data missing");
    }
}
