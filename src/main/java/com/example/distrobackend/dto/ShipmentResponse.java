package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.Shipment;
import com.example.distrobackend.Domain.enums.ShipmentStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ShipmentResponse(UUID id, String shipmentNumber, UUID orderId, String orderNumber,
        UUID organizationId, UUID tripId, ShipmentStatus status, String trackingReference,
        String notes, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    public static ShipmentResponse from(Shipment s) {
        return new ShipmentResponse(s.getId(), s.getShipmentNumber(), s.getOrder().getId(),
                s.getOrder().getOrderNumber(), s.getOrganization().getId(),
                s.getTrip() == null ? null : s.getTrip().getId(), s.getStatus(),
                s.getTrackingReference(), s.getNotes(), s.getCreatedAt(), s.getUpdatedAt());
    }
}
