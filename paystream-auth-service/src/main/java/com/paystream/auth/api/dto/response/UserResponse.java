package com.paystream.auth.api.dto.response;

import com.paystream.auth.domain.model.Role;
import com.paystream.auth.domain.model.UserStatus;

public record UserResponse(
        String id,
        String email,
        String fullName,
        Role role,
        UserStatus status
) {}
