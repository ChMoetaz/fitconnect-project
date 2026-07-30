package com.fitconnect.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * WebSocket-handshake counterpart of {@code JwtAuthenticationFilter}: the WebSocket handshake is a
 * plain HTTP GET that does NOT go through the JWT servlet filter (and browsers can't set an
 * {@code Authorization} header on a WebSocket), so the token is instead passed as a query parameter
 * on the connection URL — {@code /ws?token=<jwt>} — and validated here, once, at the handshake.
 *
 * <p>The SAME {@link JwtService} as the REST side validates it (no duplicated JWT logic). On success
 * the userId is stashed in the handshake attributes for {@link JwtHandshakeHandler} to turn into the
 * session {@link WebSocketPrincipal}. If the token is missing or invalid, {@code beforeHandshake}
 * returns {@code false}, which makes Spring reject the upgrade with a 403 — the WebSocket is never
 * established, so an unauthenticated client can neither send nor subscribe.
 */
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    /** Key under which the validated userId is handed to {@link JwtHandshakeHandler}. */
    static final String USER_ID_ATTRIBUTE = "userId";

    private final JwtService jwtService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = extractToken(request);
        if (token == null || token.isBlank()) {
            return false; // no token → reject the handshake
        }
        try {
            Claims claims = jwtService.parseClaims(token);
            attributes.put(USER_ID_ATTRIBUTE, jwtService.extractUserId(claims));
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false; // malformed / expired / wrong-key token → reject
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // no-op
    }

    /** Reads the {@code token} query parameter from the (possibly SockJS-wrapped) handshake URL. */
    private String extractToken(ServerHttpRequest request) {
        List<String> tokens = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .get("token");
        return (tokens == null || tokens.isEmpty()) ? null : tokens.get(0);
    }
}
