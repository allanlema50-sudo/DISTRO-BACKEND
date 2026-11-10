package com.example.distrobackend.dto;



import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Domain.enums.UserStatus;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String fullName,
        String email,
        String phoneNumber,
        UserRole role,
        UUID organizationId,
        String organizationName,
        Organizationtype organizationtype,
        boolean phoneVerified,
        boolean emailVerified,
        UserStatus status,
        String jobTitle,
        UUID warehouseId
) {
    /** Must be called inside a transaction (organization is lazy). */
    public static UserResponse from(User user) {
        return com.example.distrobackend.mapper.UserMapper.toResponse(user);
    }
}
