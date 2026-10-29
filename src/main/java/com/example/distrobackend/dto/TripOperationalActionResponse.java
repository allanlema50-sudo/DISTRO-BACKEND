package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.TripOperationalAction;
import com.example.distrobackend.Domain.enums.TripOperationalActionType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TripOperationalActionResponse(
        UUID id,
        UUID tripId,
        UUID actorId,
        TripOperationalActionType action,
        String note,
        OffsetDateTime createdAt
) {
    public static TripOperationalActionResponse from(TripOperationalAction action) {
        return new TripOperationalActionResponse(
                action.getId(), action.getTrip().getId(), action.getActor().getId(),
                action.getActionType(), action.getNote(), action.getCreatedAt());
    }
}
