package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.TripStatus;
import com.example.distrobackend.Domain.enums.TripType;
import com.example.distrobackend.Domain.entity.Trip;

import java.util.List;

public record TripResponse(
        String id,
        String tripNumber,
        TripType type,
        TripStatus status,
        TripLocation origin,
        List<TripStopResponse> stops,
        List<TripItemResponse> itemsOnBoard,
        String scheduledStartLabel,
        Integer totalDistanceKm
) {
    public static TripResponse from(Trip trip, List<TripItemResponse> items) {
        TripLocation origin = new TripLocation(
                trip.getOriginName(),
                trip.getOriginAddress(),
                trip.getOriginLat(),
                trip.getOriginLng()
        );

        List<TripStopResponse> stops = trip.getStops().stream()
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
                origin,
                stops,
                items,
                trip.getScheduledStartLabel(),
                trip.getTotalDistanceKm() != null ? trip.getTotalDistanceKm() : 0
        );
    }
}
