package com.example.distrobackend.dto;

public record TripLocation(
        String name,
        String address,
        Double latitude,
        Double longitude
) {
}
