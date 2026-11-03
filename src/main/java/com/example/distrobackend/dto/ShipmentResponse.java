package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.Shipment;
import com.example.distrobackend.Domain.enums.ShipmentStatus;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ShipmentResponse(UUID id, String shipmentNumber, UUID orderId, String orderNumber,
        UUID organizationId, UUID tripId, ShipmentStatus status, String trackingReference,
        String notes, String cargoDescription, String cargoCategory, BigDecimal weightKg,
        BigDecimal volumeM3, List<String> handlingRequirements, String deliveryLocation,
        LocalDateTime deliveryWindowStart, LocalDateTime deliveryWindowEnd,
        boolean allowSharedLoads, String specialInstructions,
        OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    public static ShipmentResponse from(Shipment s) {
        return new ShipmentResponse(s.getId(), s.getShipmentNumber(),
                s.getOrder() == null ? null : s.getOrder().getId(),
                s.getOrder() == null ? null : s.getOrder().getOrderNumber(), s.getOrganization().getId(),
                s.getTrip() == null ? null : s.getTrip().getId(), s.getStatus(),
                s.getTrackingReference(), s.getNotes(), s.getCargoDescription(), s.getCargoCategory(),
                s.getWeightKg(), s.getVolumeM3(), s.getHandlingRequirements(), s.getDeliveryLocation(),
                s.getDeliveryWindowStart(), s.getDeliveryWindowEnd(), s.isAllowSharedLoads(),
                s.getSpecialInstructions(), s.getCreatedAt(), s.getUpdatedAt());
    }
}
