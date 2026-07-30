package com.fitconnect.backend.config;

import com.fitconnect.backend.security.JwtHandshakeHandler;
import com.fitconnect.backend.security.JwtHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP-over-WebSocket setup for the real-time group chat (see the "Chat temps réel (WebSocket)"
 * section of CLAUDE.md). This is the project's first non-REST surface; everything else stays
 * stateless HTTP + JWT.
 *
 * <ul>
 *   <li><b>Endpoint {@code /ws}</b> (SockJS fallback enabled) is where clients open the connection,
 *       passing their JWT as {@code /ws?token=<jwt>}. Authentication is done at the handshake by
 *       {@link JwtHandshakeInterceptor} (validates the token, same {@code JwtService} as REST) +
 *       {@link JwtHandshakeHandler} (binds the userId as the session {@code Principal}) — NOT by the
 *       servlet JWT filter, which the handshake bypasses. {@code /ws/**} is therefore whitelisted in
 *       {@code SecurityConfig}: the servlet chain lets the handshake through and the interceptor is
 *       the real gate.</li>
 *   <li><b>Simple broker on {@code /topic}</b> — server → clients broadcast (per group:
 *       {@code /topic/community-groups/{groupId}/messages}) — and on {@code /user}, the prefix Spring
 *       uses for per-session replies ({@code /user/queue/errors}).</li>
 *   <li><b>Application prefix {@code /app}</b> — client → server sends routed to {@code @MessageMapping}
 *       handlers (e.g. {@code /app/community-groups/{groupId}/messages} → {@code ChatController}).</li>
 * </ul>
 *
 * <p>Allowed origins mirror {@code WebConfig}'s CORS (the React dev servers). The SockJS handshake
 * does its own origin check here, independently of the servlet CORS bean.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final JwtHandshakeHandler jwtHandshakeHandler;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("http://localhost:5173", "http://localhost:3000")
                .setHandshakeHandler(jwtHandshakeHandler)
                .addInterceptors(jwtHandshakeInterceptor)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }
}
