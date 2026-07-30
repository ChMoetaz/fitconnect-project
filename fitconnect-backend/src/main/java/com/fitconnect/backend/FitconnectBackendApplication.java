package com.fitconnect.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * UserDetailsServiceAutoConfiguration is excluded because authentication is fully handled
 * by JWT (see security/): without this exclusion, Spring Boot generates a default in-memory
 * user at startup (with its password logged in clear text) that is never used and has no
 * reason to exist here.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class FitconnectBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(FitconnectBackendApplication.class, args);
    }
}
