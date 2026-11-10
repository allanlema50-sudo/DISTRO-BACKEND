package com.example.distrobackend.mapper;

import com.example.distrobackend.Domain.entity.Trip;
import com.example.distrobackend.Domain.entity.TripStop;
import com.example.distrobackend.dto.TripItemResponse;
import com.example.distrobackend.dto.TripLocation;
import com.example.distrobackend.dto.TripResponse;
import com.example.distrobackend.dto.TripStopResponse;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

public class TripMapper {

    private TripMapper() { }

    public static TripResponse toResponse(Trip trip, List<TripItemResponse> items) {
        return toResponse(trip, items, stop -> true);
    }

    public static TripResponse toCustomerResponse(Trip trip, List<TripItemResponse> items, UUID customerId) {
        return toResponse(trip, items, stop -> stop.getOrder() != null && stop.getOrder().getCustomer() != null
                && customerId.equals(stop.getOrder().getCustomer().getId()));
    }

    private static TripResponse toResponse(Trip trip, List<TripItemResponse> items,
                                           Predicate<TripStop> stopVisibility) {
        TripLocation origin = new TripLocation(trip.getOriginName(), trip.getOriginAddress(),
                trip.getOriginLat(), trip.getOriginLng());
        List<TripStopResponse> stops = trip.getStops().stream().filter(stopVisibility)
                .map(stop -> new TripStopResponse(stop.getId().toString(), stop.getSequence(),
                        new TripLocation(stop.getLocationName(), stop.getLocationAddress(),
                                stop.getLocationLat(), stop.getLocationLng()), stop.getStatus(), stop.getEtaLabel(),
                        stop.getDistanceFromPreviousKm(), stop.getOrder() == null ? null : stop.getOrder().getOrderNumber(),
                        stop.getContactName(), stop.getContactPhone())).toList();
        return new TripResponse(trip.getId().toString(), trip.getTripNumber(), trip.getTripType(), trip.getStatus(),
                trip.getRider() == null ? null : trip.getRider().getId(),
                trip.getVehicle() == null ? null : trip.getVehicle().getId(), origin, stops, items,
                trip.getScheduledStartLabel(), trip.getTotalDistanceKm() == null ? 0 : trip.getTotalDistanceKm());
    }
}
