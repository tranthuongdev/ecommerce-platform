package com.athanas.ecommerce.auth.login;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver();

    @Mock HttpServletRequest request;

    @Test
    void shouldUseXForwardedForWhenPresent() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.1.1.1, 2.2.2.2");

        assertThat(resolver.resolveClientIp(request)).isEqualTo("1.1.1.1");
    }

    @Test
    void shouldTrimWhitespace() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(" 1.1.1.1 , 2.2.2.2");

        assertThat(resolver.resolveClientIp(request)).isEqualTo("1.1.1.1");
    }

    @Test
    void shouldFallbackToRemoteAddrWhenNoHeader() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("2.2.2.2");

        assertThat(resolver.resolveClientIp(request)).isEqualTo("2.2.2.2");
    }

    @Test
    void shouldReturnUnknownWhenAllFail() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn(null);

        assertThat(resolver.resolveClientIp(request)).isEqualTo("unknown");
    }
}
