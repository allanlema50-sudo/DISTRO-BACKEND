package com.example.distrobackend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateVehicleRequest(
        @Size(max = 30) String registrationNumber,
        @Size(max = 50) String vehicleType,
        @Size(max = 80) String make,
        @Size(max = 80) String model,
        @Min(1) Integer capacityKg,
        @DecimalMin("0.01") @JsonAlias({"cargoVolumeM3", "volumeM3", "capacityVolumeM3"}) BigDecimal capacityM3
) {
}
