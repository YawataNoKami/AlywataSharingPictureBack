package com.photoapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credentials submitted to {@code POST /api/auth/login}.
 */
public record LoginRequest(

        @NotBlank(message = "username is required")
        @Size(max = 100)
        String username,

        @NotBlank(message = "password is required")
        @Size(max = 200)
        String password
) {
}
