package com.example.distrobackend.dto;

import java.time.OffsetDateTime;

public record LocationPingResponse(
        Long id,
        double lat,
        double lng,
        OffsetDateTime recordedAt
) {
    public static LocationPingResponse from(com.example.distrobackend.Domain.entity.TripLocationPing ping) {
        return new LocationPingResponse(
                ping.getId(),
                ping.getLat(),
                ping.getLng(),
                ping.getRecordedAt()
        );
    }
}
