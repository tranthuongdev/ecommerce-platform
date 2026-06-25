package com.athanas.ecommerce.auth.token;

import com.athanas.ecommerce.auth.login.LoginResponse;
import com.athanas.ecommerce.auth.user.Role;
import com.athanas.ecommerce.auth.user.UserRepository;
import com.athanas.ecommerce.common.error.InvalidRefreshTokenException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class RefreshController {

    private final RefreshTokenService refreshTokenService;
    private final JwtGenerator jwtGenerator;
    private final UserRepository userRepository;
    private final JwtProperties jwtProperties;

    @PostMapping("/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest req) {
        RefreshTokenRotationResult rotationResult = refreshTokenService.rotate(req.refreshToken());

        Set<String> roleNames = userRepository.findById(rotationResult.userId())
                .orElseThrow(InvalidRefreshTokenException::new)
                .getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        AccessTokenResult accessResult = jwtGenerator.generateAccessToken(rotationResult.userId(), roleNames);
        return new LoginResponse(accessResult.token(), rotationResult.newRefreshTokenPlain(),
                jwtProperties.getAccessTtl().toSeconds());
    }
}
