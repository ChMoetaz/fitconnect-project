package com.fitconnect.backend.dto;

import com.fitconnect.backend.domain.User;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private Long userId;
    private String email;
    private String role;
    private String accessToken;

    public static AuthResponse of(User user, String accessToken) {
        return new AuthResponse(user.getUserId(), user.getEmail(), user.getRole(), accessToken);
    }
}
