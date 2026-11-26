package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.RiderProfile;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.UserStatus;
import com.example.distrobackend.Domain.enums.DriverApprovalStatus;
import com.example.distrobackend.Domain.enums.Organizationtype;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.UUID;

public record DriverResponse(
        UUID id,
        String fullName,
        String email,
        String phoneNumber,
        UserStatus status,
        DriverApprovalStatus approvalStatus,
        String nationalId,
        LocalDate licenseExpiryDate,
        String internalNotes,
        boolean emailVerified,
        boolean phoneVerified,
        String licenseNumber,
        boolean available,
        Double currentLat,
        Double currentLng,
        OffsetDateTime lastLocationAt,
        String pendingFullName,
        String pendingPhoneNumber,
        String pendingLicenseNumber,
        OffsetDateTime createdAt,
        UUID organizationId,
        String organizationName,
        Organizationtype organizationType
) {
    public static DriverResponse from(User user, RiderProfile profile) {
        return from(user, profile, true);
    }

    public static DriverResponse from(User user, RiderProfile profile, boolean includeInternalNotes) {
        return new DriverResponse(
                user.getId(), user.getFullName(), user.getEmail(), user.getPhoneNumber(),
                user.getStatus(), user.getDriverApprovalStatus(),
                profile == null ? null : profile.getNationalId(),
                profile == null ? null : profile.getLicenseExpiryDate(),
                profile == null || !includeInternalNotes ? null : profile.getInternalNotes(),
                user.isEmailVerified(), user.isPhoneVerified(),
                profile == null ? null : profile.getLicenseNumber(),
                profile != null && profile.isAvailable(),
                profile == null ? null : profile.getCurrentLat(),
                profile == null ? null : profile.getCurrentLng(),
                profile == null ? null : profile.getLastLocationAt(),
                profile == null ? null : profile.getPendingFullName(),
                profile == null ? null : profile.getPendingPhoneNumber(),
                profile == null ? null : profile.getPendingLicenseNumber(),
                user.getCreatedAt(),
                user.getOrganization() == null ? null : user.getOrganization().getId(),
                user.getOrganization() == null ? null : user.getOrganization().getName(),
                user.getOrganization() == null ? null : user.getOrganization().getType());
    }
}
