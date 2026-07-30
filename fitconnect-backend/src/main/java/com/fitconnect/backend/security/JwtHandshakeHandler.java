package com.fitconnect.backend.security;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * Turns the userId that {@link JwtHandshakeInterceptor} validated and stashed in the handshake
 * attributes into the session {@link WebSocketPrincipal}. Because the interceptor already rejects
 * the handshake when the token is absent/invalid, by the time this runs the attribute is guaranteed
 * to be present.
 *
 * <p>The resulting {@code Principal} is bound to the WebSocket session and is what
 * {@code @MessageMapping} handlers receive and what {@code convertAndSendToUser} routes replies to.
 */
@Component
public class JwtHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request,
                                      WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        Long userId = (Long) attributes.get(JwtHandshakeInterceptor.USER_ID_ATTRIBUTE);
        return new WebSocketPrincipal(userId);
    }
}
