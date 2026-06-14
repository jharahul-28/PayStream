package com.paystream.auth.api.dto.request;

import com.paystream.auth.domain.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Registration request DTO.
 * Password policy enforced via @Pattern — minimum 8 chars, 1 uppercase, 1 digit, 1 special char.
 */
public record RegisterRequest(

        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email must be a valid email address")
        String email,

        @NotBlank(message = "Password must not be blank")
        @Pattern(
                regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
                message = "Password must be at least 8 characters with 1 uppercase, 1 digit, and 1 special character (@$!%*?&)"
        )
        String password,

        @NotBlank(message = "Full name must not be blank")
        String fullName,

        @NotNull(message = "Role must not be null")
        Role role
) {}
