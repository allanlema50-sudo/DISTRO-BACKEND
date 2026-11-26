package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.Vehicle;
import com.example.distrobackend.Domain.enums.VehicleStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record VehicleResponse(
        UUID id,
        UUID organizationId,
        String organizationName,
        String registrationNumber,
        String vehicleType,
        String make,
        String model,
        Integer capacityKg,
        BigDecimal capacityM3,
        UUID driverId,
        String assignedDriverName,
        VehicleStatus status,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static VehicleResponse from(Vehicle vehicle) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getOrganization() == null ? null : vehicle.getOrganization().getId(),
                vehicle.getOrganization() == null ? null : vehicle.getOrganization().getName(),
                vehicle.getRegistrationNumber(),
                vehicle.getVehicleType(),
                vehicle.getMake(),
                vehicle.getModel(),
                vehicle.getCapacityKg(),
                vehicle.getCapacityM3(),
                vehicle.getAssignedDriver() == null ? null : vehicle.getAssignedDriver().getId(),
                vehicle.getAssignedDriver() == null ? null : vehicle.getAssignedDriver().getFullName(),
                vehicle.getStatus(),
                vehicle.getVersion(),
                vehicle.getCreatedAt(),
                vehicle.getUpdatedAt());
    }
}
