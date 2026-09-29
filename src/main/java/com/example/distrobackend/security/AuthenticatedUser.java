package com.example.distrobackend.security;

import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;

import java.util.UUID;

public record AuthenticatedUser( UUID userId,
                                 UserRole role,
                                 UUID organizationId,
                                 Organizationtype organizationType) {

}
