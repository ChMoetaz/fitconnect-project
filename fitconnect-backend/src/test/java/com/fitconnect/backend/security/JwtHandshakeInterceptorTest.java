package com.fitconnect.backend.security;

import com.fitconnect.backend.config.JwtProperties;
import com.fitconnect.backend.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for the WebSocket auth gate {@link JwtHandshakeInterceptor}. Uses a REAL
 * {@link JwtService} (same one the REST side uses — the whole point is not duplicating JWT logic)
 * with a throwaway secret. Asserts that a valid {@code ?token=} handshake is accepted and stashes
 * the userId, and that a missing or invalid token rejects the handshake (returns {@code false} →
 * Spring answers the upgrade with a 403).
 */
class JwtHandshakeInterceptorTest {

    private static final String SECRET = "test-only-jwt-secret-please-change-me-0123456789";

    private JwtService jwtService;
    private JwtHandshakeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setExpirationMs(86_400_000L);
        jwtService = new JwtService(properties);
        interceptor = new JwtHandshakeInterceptor(jwtService);
    }

    private boolean handshakeWithUri(String uri, Map<String, Object> attributes) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create(uri));
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        return interceptor.beforeHandshake(request, response, null, attributes);
    }

    @Test
    void acceptsValidTokenAndStashesUserId() {
        User user = User.builder().userId(42L).email("runner@fitconnect.test").role("USER").build();
        String token = jwtService.generateToken(user);
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = handshakeWithUri("http://localhost:8080/ws?token=" + token, attributes);

        assertThat(accepted).isTrue();
        assertThat(attributes.get(JwtHandshakeInterceptor.USER_ID_ATTRIBUTE)).isEqualTo(42L);
    }

    @Test
    void rejectsWhenTokenMissing() {
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = handshakeWithUri("http://localhost:8080/ws", attributes);

        assertThat(accepted).isFalse();
        assertThat(attributes).doesNotContainKey(JwtHandshakeInterceptor.USER_ID_ATTRIBUTE);
    }

    @Test
    void rejectsWhenTokenInvalid() {
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = handshakeWithUri(
                "http://localhost:8080/ws?token=not-a-real-jwt", attributes);

        assertThat(accepted).isFalse();
        assertThat(attributes).doesNotContainKey(JwtHandshakeInterceptor.USER_ID_ATTRIBUTE);
    }
}
