package com.fitconnect.backend.security;

import com.fitconnect.backend.config.JwtProperties;
import com.fitconnect.backend.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Issuance and validation of JWT access tokens. The token encodes the userId (subject)
 * and the role, and is signed with HMAC-SHA256 using the secret configured via JWT_SECRET.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String ROLE_CLAIM = "role";

    private final JwtProperties jwtProperties;

    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(jwtProperties.getExpirationMs());

        return Jwts.builder()
                .subject(String.valueOf(user.getUserId()))
                .claim(ROLE_CLAIM, user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key())
                .compact();
    }

    /**
     * @throws io.jsonwebtoken.JwtException if the token is absent, malformed, expired or signed
     *         with a different key — left to propagate as-is, leaving it to the caller
     *         (JwtAuthenticationFilter) to decide how to react.
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long extractUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public String extractRole(Claims claims) {
        return claims.get(ROLE_CLAIM, String.class);
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
