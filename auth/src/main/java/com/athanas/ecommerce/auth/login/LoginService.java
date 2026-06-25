package com.athanas.ecommerce.auth.login;

import com.athanas.ecommerce.auth.token.AccessTokenResult;
import com.athanas.ecommerce.auth.token.JwtGenerator;
import com.athanas.ecommerce.auth.token.JwtProperties;
import com.athanas.ecommerce.auth.token.RefreshTokenService;
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
@Transactional
public class LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtGenerator jwtGenerator;
    private final JwtProperties jwtProperties;
    private final LoginAttemptTracker loginAttemptTracker;
    private final RefreshTokenService refreshTokenService;

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

        AccessTokenResult accessResult = jwtGenerator.generateAccessToken(user.getId(), roleNames);
        String refreshToken = refreshTokenService.issue(user.getId());

        return new LoginResponse(accessResult.token(), refreshToken, jwtProperties.getAccessTtl().toSeconds());
    }
}
