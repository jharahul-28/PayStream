package com.paystream.auth.api.dto.response;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,       // seconds until access token expiry
        String tokenType,     // always "Bearer"
        String userId,
        String role
) {}
