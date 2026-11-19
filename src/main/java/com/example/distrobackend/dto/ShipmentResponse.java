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
        BigDecimal volumeM3, List<String> handlingRequirements, String pickupLocation,
        LocalDateTime pickupWindowStart, LocalDateTime pickupWindowEnd,
        Double pickupLat, Double pickupLng, String deliveryLocation,
        LocalDateTime deliveryWindowStart, LocalDateTime deliveryWindowEnd,
        Double deliveryLat, Double deliveryLng,
        boolean allowSharedLoads, String specialInstructions,
        OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    public static ShipmentResponse from(Shipment s) {
        return new ShipmentResponse(s.getId(), s.getShipmentNumber(),
                s.getOrder() == null ? null : s.getOrder().getId(),
                s.getOrder() == null ? null : s.getOrder().getOrderNumber(), s.getOrganization().getId(),
                s.getTrip() == null ? null : s.getTrip().getId(), s.getStatus(),
                s.getTrackingReference(), s.getNotes(), s.getCargoDescription(), s.getCargoCategory(),
                s.getWeightKg(), s.getVolumeM3(), s.getHandlingRequirements(),
                s.getPickupLocation(), s.getPickupWindowStart(), s.getPickupWindowEnd(),
                s.getPickupLat(), s.getPickupLng(), s.getDeliveryLocation(),
                s.getDeliveryWindowStart(), s.getDeliveryWindowEnd(),
                s.getDeliveryLat(), s.getDeliveryLng(), s.isAllowSharedLoads(),
                s.getSpecialInstructions(), s.getCreatedAt(), s.getUpdatedAt());
    }
}
