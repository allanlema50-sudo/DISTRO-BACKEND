package com.example.distrobackend.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        String redirectPath,
        UserResponse user
) {
}
