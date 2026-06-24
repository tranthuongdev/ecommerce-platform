package com.athanas.ecommerce.auth.login;

import com.athanas.ecommerce.auth.token.JwtGenerator;
import com.athanas.ecommerce.auth.token.JwtProperties;
import com.athanas.ecommerce.auth.user.Role;
import com.athanas.ecommerce.auth.user.User;
import com.athanas.ecommerce.auth.user.UserRepository;
import com.athanas.ecommerce.common.error.InvalidCredentialsException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtGenerator jwtGenerator;
    private final JwtProperties jwtProperties;
    private final LoginAttemptTracker loginAttemptTracker;

    public LoginResponse login(LoginRequest req) {
        String key = "login:fail:" + req.email().toLowerCase();

        loginAttemptTracker.checkBlocked(key);

        User user = userRepository.findByEmail(req.email())
                .orElseGet(() -> {
                    loginAttemptTracker.recordFailure(key);
                    throw new InvalidCredentialsException();
                });

        if (user.getDeletedAt() != null || !user.isEnabled()) {
            loginAttemptTracker.recordFailure(key);
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            loginAttemptTracker.recordFailure(key);
            throw new InvalidCredentialsException();
        }

        loginAttemptTracker.clearOnSuccess(key);

        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        String accessToken = jwtGenerator.generateAccessToken(user.getId(), roleNames);

        return new LoginResponse(accessToken, "TBD_US005", jwtProperties.getAccessTtl().toSeconds());
    }
}
