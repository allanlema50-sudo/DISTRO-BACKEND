package com.example.distrobackend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateVehicleRequest(
        @Size(max = 30) String registrationNumber,
        @Size(max = 50) String vehicleType,
        @Size(max = 80) String make,
        @Size(max = 80) String model,
        @Min(1) Integer capacityKg
) {
}
