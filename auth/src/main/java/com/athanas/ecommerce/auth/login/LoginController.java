package com.athanas.ecommerce.auth.login;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class LoginController {

    private final LoginService loginService;
    private final ClientIpResolver clientIpResolver;
    private final IpRateLimiter ipRateLimiter;

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public LoginResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        ipRateLimiter.checkAndIncrement(clientIpResolver.resolveClientIp(request));
        return loginService.login(req);
    }
}
