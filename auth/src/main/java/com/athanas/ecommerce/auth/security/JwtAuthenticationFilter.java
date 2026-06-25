package com.athanas.ecommerce.auth.security;

import com.athanas.ecommerce.auth.token.JwtGenerator;
import com.athanas.ecommerce.auth.user.User;
import com.athanas.ecommerce.auth.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtGenerator jwtGenerator;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        Jws<Claims> jws;
        try {
            jws = jwtGenerator.parseAndValidate(token);
        } catch (JwtException e) {
            writeUnauthorized(response, "Invalid or expired access token");
            return;
        }

        Claims claims = jws.getPayload();
        UUID userId = UUID.fromString(claims.getSubject());
        List<String> roles = claims.get("roles", List.class);

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty() || !userOpt.get().isEnabled()) {
            writeUnauthorized(response, "User account is not active");
            return;
        }

        User user = userOpt.get();
        Set<String> roleNames = (roles != null) ? Set.copyOf(roles) : Set.of();

        var authorities = roleNames.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();

        AuthenticatedUser principal = new AuthenticatedUser(userId, user.getEmail(), roleNames);
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        chain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, detail);
        problem.setType(URI.create("https://athanas.dev/errors/invalid-access-token"));

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
