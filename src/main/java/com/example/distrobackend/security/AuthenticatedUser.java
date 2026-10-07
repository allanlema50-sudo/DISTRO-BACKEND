package com.example.distrobackend.security;

import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;

import java.util.UUID;
import java.time.Instant;

public record AuthenticatedUser( UUID userId,
                                 UserRole role,
                                 UUID organizationId,
                                 Organizationtype organizationType,
                                 Instant expiresAt) {

    public boolean isExpired() {
        return expiresAt == null || !expiresAt.isAfter(Instant.now());
    }

}
