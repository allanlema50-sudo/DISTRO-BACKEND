package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.TripStatus;
import com.example.distrobackend.Domain.enums.TripType;
import com.example.distrobackend.Domain.entity.Trip;
import com.example.distrobackend.Domain.entity.TripStop;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

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
        return from(trip, items, stop -> true);
    }

    public static TripResponse fromCustomer(Trip trip, List<TripItemResponse> items,
                                            UUID customerId) {
        return from(trip, items, stop -> stop.getOrder() != null
                && stop.getOrder().getCustomer() != null
                && customerId.equals(stop.getOrder().getCustomer().getId()));
    }

    private static TripResponse from(Trip trip, List<TripItemResponse> items,
                                     Predicate<TripStop> stopVisibility) {
        TripLocation origin = new TripLocation(
                trip.getOriginName(),
                trip.getOriginAddress(),
                trip.getOriginLat(),
                trip.getOriginLng()
        );

        List<TripStopResponse> stops = trip.getStops().stream()
                .filter(stopVisibility)
                .map(stop -> new TripStopResponse(
                        stop.getId().toString(),
                        stop.getSequence(),
                        new TripLocation(
                                stop.getLocationName(),
                                stop.getLocationAddress(),
                                stop.getLocationLat(),
                                stop.getLocationLng()
                        ),
                        stop.getStatus(),
                        stop.getEtaLabel(),
                        stop.getDistanceFromPreviousKm(),
                        stop.getOrder() != null ? stop.getOrder().getOrderNumber() : null,
                        stop.getContactName(),
                        stop.getContactPhone()
                ))
                .toList();

        return new TripResponse(
                trip.getId().toString(),
                trip.getTripNumber(),
                trip.getTripType(),
                trip.getStatus(),
                trip.getRider() == null ? null : trip.getRider().getId(),
                trip.getVehicle() == null ? null : trip.getVehicle().getId(),
                origin,
                stops,
                items,
                trip.getScheduledStartLabel(),
                trip.getTotalDistanceKm() != null ? trip.getTotalDistanceKm() : 0
        );
    }
}
