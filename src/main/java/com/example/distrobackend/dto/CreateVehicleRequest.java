package com.example.distrobackend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateVehicleRequest(
        @NotBlank @Size(max = 30) @JsonAlias({"registration_number", "registrationNo", "plateNumber"}) String registrationNumber,
        @NotBlank @Size(max = 50) @JsonAlias({"type", "category", "vehicle_type"}) String vehicleType,
        @Size(max = 80) String make,
        @Size(max = 80) String model,
        @Min(1) Integer capacityKg,
        @JsonAlias({"distributorId", "organization_id", "distributor_id"}) UUID organizationId,
        @DecimalMin("0.01") @JsonAlias({"cargoVolumeM3", "volumeM3", "capacityVolumeM3"}) BigDecimal capacityM3
) {
    public CreateVehicleRequest(String registrationNumber, String vehicleType, String make,
                                String model, Integer capacityKg) {
        this(registrationNumber, vehicleType, make, model, capacityKg, null, null);
    }
}
