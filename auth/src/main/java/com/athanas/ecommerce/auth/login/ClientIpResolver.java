package com.athanas.ecommerce.auth.login;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {

    // TODO(SPRINT-10): When Nginx is in front, configure trusted proxy IPs
    // and validate X-Forwarded-For chain. Until then, this is spoofable
    // by malicious clients setting their own X-Forwarded-For header.

    public String resolveClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String remoteAddr = req.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr : "unknown";
    }
}
