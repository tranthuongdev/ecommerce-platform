// TEMP — remove at end of Sprint 2
package com.athanas.ecommerce.auth._testapi;

import com.athanas.ecommerce.auth.security.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/v1/_test")
@RequiredArgsConstructor
public class RoleTestController {

    private final CurrentUserResolver currentUserResolver;

    @GetMapping("/user-only")
    @PreAuthorize("hasRole('USER')")
    public Map<String, String> userOnly() {
        return Map.of("role", "USER");
    }

    @GetMapping("/seller-only")
    @PreAuthorize("hasRole('SELLER')")
    public Map<String, String> sellerOnly() {
        return Map.of("role", "SELLER");
    }

    @GetMapping("/admin-only")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> adminOnly() {
        return Map.of("role", "ADMIN");
    }

    @GetMapping("/any-authenticated")
    public Map<String, Object> anyAuthenticated() {
        var user = currentUserResolver.requireCurrentUser();
        return Map.of(
                "userId", user.userId().toString(),
                "email", user.email(),
                "roles", user.roleNames()
        );
    }
}
