package com.fitconnect.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Body of {@code PATCH /api/admin/users/{userId}/role}. The value is validated against the allowed
 * roles in {@code AdminUserService} (USER / ADMIN / COACH); an unknown value yields a clean 400.
 */
@Data
public class UpdateUserRoleRequest {
    @NotBlank
    private String role;
}
