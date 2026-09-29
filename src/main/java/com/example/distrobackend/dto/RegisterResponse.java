package com.example.distrobackend.dto;

import java.util.UUID;

public record RegisterResponse(UUID userId, String message) {
}
