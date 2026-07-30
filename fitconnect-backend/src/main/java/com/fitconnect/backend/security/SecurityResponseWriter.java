package com.fitconnect.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitconnect.backend.exception.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.time.Instant;

/**
 * Serializes an error in the ApiError format (the same as GlobalExceptionHandler) from a
 * point in the Spring Security filter chain, where no @RestControllerAdvice applies.
 */
final class SecurityResponseWriter {

    private SecurityResponseWriter() {
    }

    static void write(HttpServletResponse response, ObjectMapper objectMapper,
                       HttpStatus status, String message) throws IOException {
        ApiError error = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
