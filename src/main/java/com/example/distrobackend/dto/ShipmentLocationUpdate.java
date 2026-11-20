package com.example.distrobackend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ShipmentLocationUpdate(
        @NotBlank @Size(max = 300) String pickupLocation,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") @JsonAlias("pickupLatitude") Double pickupLat,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") @JsonAlias("pickupLongitude") Double pickupLng,
        @NotBlank @Size(max = 300) @JsonAlias("deliveryLocation") String deliveryLocation,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") @JsonAlias("deliveryLatitude") Double deliveryLat,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") @JsonAlias("deliveryLongitude") Double deliveryLng
) {}
