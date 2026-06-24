package com.athanas.ecommerce.auth.login;

import com.athanas.ecommerce.auth.token.JwtGenerator;
import com.athanas.ecommerce.auth.token.JwtProperties;
import com.athanas.ecommerce.auth.user.Role;
import com.athanas.ecommerce.auth.user.User;
import com.athanas.ecommerce.auth.user.UserRepository;
import com.athanas.ecommerce.common.error.InvalidCredentialsException;
import com.athanas.ecommerce.common.error.TooManyAttemptsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtGenerator jwtGenerator;
    @Mock JwtProperties jwtProperties;
    @Mock LoginAttemptTracker loginAttemptTracker;
    @InjectMocks LoginService service;

    private static final LoginRequest REQ = new LoginRequest("user@example.com", "secret123");

    private User buildUser(boolean enabled, Instant deletedAt) {
        Role role = new Role();
        role.setName("USER");
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setPasswordHash("encoded");
        user.setEnabled(enabled);
        user.setDeletedAt(deletedAt);
        user.getRoles().add(role);
        return user;
    }

    @Test
    void shouldReturnTokenOnValidCredentials() {
        User user = buildUser(true, null);
        when(jwtProperties.getAccessTtl()).thenReturn(Duration.ofMinutes(15));
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "encoded")).thenReturn(true);
        when(jwtGenerator.generateAccessToken(any(), any())).thenReturn("jwt.token.here");

        LoginResponse resp = service.login(REQ);

        assertThat(resp.accessToken()).isEqualTo("jwt.token.here");
        assertThat(resp.refreshToken()).isEqualTo("TBD_US005");
        assertThat(resp.expiresIn()).isEqualTo(900L);
        verify(loginAttemptTracker).clearOnSuccess(anyString());
    }

    @Test
    void shouldThrowInvalidCredentialsWhenEmailNotFound() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(REQ))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(loginAttemptTracker).recordFailure(anyString());
    }

    @Test
    void shouldThrowInvalidCredentialsWhenPasswordWrong() {
        User user = buildUser(true, null);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.login(REQ))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(loginAttemptTracker).recordFailure(anyString());
    }

    @Test
    void shouldThrowInvalidCredentialsWhenUserDisabled() {
        User user = buildUser(false, null);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(REQ))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(loginAttemptTracker).recordFailure(anyString());
    }

    @Test
    void shouldThrowTooManyAttemptsWhenLocked() {
        doThrow(new TooManyAttemptsException(Duration.ofMinutes(15)))
                .when(loginAttemptTracker).checkBlocked(anyString());

        assertThatThrownBy(() -> service.login(REQ))
                .isInstanceOf(TooManyAttemptsException.class);
    }
}
