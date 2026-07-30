package com.fitconnect.backend.security;

import java.security.Principal;

/**
 * The authenticated identity of a WebSocket session, derived from the JWT at the handshake (see
 * {@link JwtHandshakeInterceptor} / {@link JwtHandshakeHandler}). Mirrors what
 * {@code JwtAuthenticationFilter} does for HTTP requests (principal = userId), but for STOMP:
 * once set on the session it is injected into {@code @MessageMapping} handlers as {@code Principal}
 * and is what {@code SimpMessagingTemplate.convertAndSendToUser(name, ...)} targets.
 *
 * <p>{@link #getName()} returns the userId as a String because Spring's user-destination machinery
 * keys sessions by {@code Principal.getName()}; {@link #getUserId()} exposes the same value typed as
 * {@code Long} so handlers do not have to re-parse it.
 */
public final class WebSocketPrincipal implements Principal {

    private final Long userId;

    public WebSocketPrincipal(Long userId) {
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
