package com.example.distrobackend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateVehicleRequest(
        @NotBlank @Size(max = 30) String registrationNumber,
        @NotBlank @Size(max = 50) String vehicleType,
        @Size(max = 80) String make,
        @Size(max = 80) String model,
        @Min(1) Integer capacityKg,
        UUID organizationId
) {
    public CreateVehicleRequest(String registrationNumber, String vehicleType, String make,
                                String model, Integer capacityKg) {
        this(registrationNumber, vehicleType, make, model, capacityKg, null);
    }
}
