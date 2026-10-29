package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.Vehicle;
import com.example.distrobackend.Domain.enums.VehicleStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VehicleResponse(
        UUID id,
        UUID organizationId,
        String registrationNumber,
        String vehicleType,
        String make,
        String model,
        Integer capacityKg,
        VehicleStatus status,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static VehicleResponse from(Vehicle vehicle) {
        return new VehicleResponse(
                vehicle.getId(), vehicle.getOrganization().getId(), vehicle.getRegistrationNumber(),
                vehicle.getVehicleType(), vehicle.getMake(), vehicle.getModel(), vehicle.getCapacityKg(),
                vehicle.getStatus(), vehicle.getVersion(), vehicle.getCreatedAt(), vehicle.getUpdatedAt());
    }
}
