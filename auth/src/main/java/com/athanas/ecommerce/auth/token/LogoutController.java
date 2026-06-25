package com.athanas.ecommerce.auth.token;

import com.athanas.ecommerce.auth.security.AuthenticatedUser;
import com.athanas.ecommerce.auth.security.CurrentUserResolver;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class LogoutController {

    private final LogoutService logoutService;
    private final JwtGenerator jwtGenerator;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody(required = false) LogoutRequest body, HttpServletRequest req) {
        AuthenticatedUser principal = currentUserResolver.requireCurrentUser();

        String header = req.getHeader("Authorization");
        String token = header.substring(7);

        Claims claims = jwtGenerator.parseAndValidate(token).getPayload();
        UUID jti = UUID.fromString(claims.getId());
        Instant exp = claims.getExpiration().toInstant();

        logoutService.logout(principal.userId(), jti, exp,
                body != null ? body.refreshToken() : null);
    }
}
