package com.fitconnect.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Triggered when a valid token is present but Spring Security's own authorization denies
 * access (in practice rarely used here, since most ownership checks happen via
 * CurrentUser.requireSelf in the controllers — see GlobalExceptionHandler.handleAccessDenied
 * for that case).
 */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                        AccessDeniedException accessDeniedException) throws IOException {
        SecurityResponseWriter.write(response, objectMapper, HttpStatus.FORBIDDEN,
                "Access to this resource is denied");
    }
}
