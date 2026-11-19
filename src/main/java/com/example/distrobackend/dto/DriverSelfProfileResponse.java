package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.RiderProfile;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.DriverApprovalStatus;
import com.example.distrobackend.Domain.enums.UserStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DriverSelfProfileResponse(UUID id, String fullName, String phoneNumber, String email,
                                        UserStatus status, DriverApprovalStatus approvalStatus,
                                        boolean available, Double currentLat, Double currentLng,
                                        OffsetDateTime lastLocationAt) {
    public static DriverSelfProfileResponse from(User user, RiderProfile profile) {
        return new DriverSelfProfileResponse(user.getId(), user.getFullName(), user.getPhoneNumber(),
                user.getEmail(), user.getStatus(), user.getDriverApprovalStatus(),
                profile != null && profile.isAvailable(), profile == null ? null : profile.getCurrentLat(),
                profile == null ? null : profile.getCurrentLng(), profile == null ? null : profile.getLastLocationAt());
    }
}
