package com.fitconnect.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: boots the full Spring application context.
 *
 * <p>With {@code src/test/resources/application.yml} it now boots against in-memory H2 with a
 * throwaway JWT secret and a dummy Gemini key, so it genuinely exercises the whole wiring —
 * security filter chain, JPA layer, {@code JwtProperties.validate()} — without Docker/Postgres
 * or the real environment variables. The behavioural coverage lives in the service unit tests
 * and the {@code integration} package; this one just guarantees the context is wireable.
 */
@SpringBootTest
class FitconnectBackendApplicationTests {

    @Test
    void contextLoads() {
    }
}
