package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.TripStatus;
import com.example.distrobackend.Domain.enums.TripType;
import com.example.distrobackend.Domain.entity.Trip;
import java.util.List;
import java.util.UUID;

public record TripResponse(
        String id,
        String tripNumber,
        TripType type,
        TripStatus status,
        UUID riderId,
        UUID vehicleId,
        TripLocation origin,
        List<TripStopResponse> stops,
        List<TripItemResponse> itemsOnBoard,
        String scheduledStartLabel,
        Integer totalDistanceKm
) {
    public static TripResponse from(Trip trip, List<TripItemResponse> items) {
        return com.example.distrobackend.mapper.TripMapper.toResponse(trip, items);
    }

    public static TripResponse fromCustomer(Trip trip, List<TripItemResponse> items,
                                            UUID customerId) {
        return com.example.distrobackend.mapper.TripMapper.toCustomerResponse(trip, items, customerId);
    }
}
