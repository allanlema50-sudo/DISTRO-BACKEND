package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.RiderProfile;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.UserStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DriverResponse(
        UUID id,
        String fullName,
        String email,
        String phoneNumber,
        UserStatus status,
        boolean emailVerified,
        boolean phoneVerified,
        String licenseNumber,
        boolean available,
        Double currentLat,
        Double currentLng,
        OffsetDateTime lastLocationAt
) {
    public static DriverResponse from(User user, RiderProfile profile) {
        return new DriverResponse(
                user.getId(), user.getFullName(), user.getEmail(), user.getPhoneNumber(),
                user.getStatus(), user.isEmailVerified(), user.isPhoneVerified(),
                profile == null ? null : profile.getLicenseNumber(),
                profile != null && profile.isAvailable(),
                profile == null ? null : profile.getCurrentLat(),
                profile == null ? null : profile.getCurrentLng(),
                profile == null ? null : profile.getLastLocationAt());
    }
}
