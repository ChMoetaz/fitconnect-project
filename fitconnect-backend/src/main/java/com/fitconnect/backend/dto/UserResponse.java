package com.fitconnect.backend.dto;

import com.fitconnect.backend.domain.User;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserResponse {
    private Long userId;
    private String email;
    private String role;

    public static UserResponse from(User user) {
        return new UserResponse(user.getUserId(), user.getEmail(), user.getRole());
    }
}
