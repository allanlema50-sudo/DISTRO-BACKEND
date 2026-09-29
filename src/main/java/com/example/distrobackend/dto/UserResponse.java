package com.example.distrobackend.dto;



import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;

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
        boolean phoneVerified
) {
    /** Must be called inside a transaction (organization is lazy). */
    public static UserResponse from(User user) {
        Organization org = user.getOrganization();
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getRole(),
                org == null ? null : org.getId(),
                org == null ? null : org.getName(),
                org == null ? null : org.getType(),
                user.isPhoneVerified()
        );
    }
}