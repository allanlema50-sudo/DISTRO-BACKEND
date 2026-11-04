package com.example.distrobackend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ShipmentRequest(
        @NotNull @JsonAlias({"requestingOrganizationId", "organization_id"}) UUID organizationId,
        UUID orderId,
        UUID tripId,
        @NotBlank @Size(max = 500) @JsonAlias("cargoDetails") String cargoDescription,
        @NotBlank @Size(max = 80) @JsonAlias("cargoType") String cargoCategory,
        @NotNull @Positive @JsonAlias({"weight", "weightInKg", "weight_kg"}) BigDecimal weightKg,
        @NotNull @Positive @JsonAlias({"volume", "volumeCubicMeters", "volume_m3"}) BigDecimal volumeM3,
        @NotEmpty @JsonAlias({"handling_requirements", "requirements"}) List<String> handlingRequirements,
        @NotBlank @Size(max = 300) @JsonAlias({"deliveryLocation", "delivery_location"}) String deliveryLocation,
        @NotNull @Future @JsonAlias({"deliveryWindowStarts", "windowStart"}) LocalDateTime deliveryWindowStart,
        @NotNull @Future @JsonAlias({"deliveryWindowEnds", "windowEnd"}) LocalDateTime deliveryWindowEnd,
        @JsonAlias({"sharedLoadConsideration", "allowSharedLoad"}) Boolean allowSharedLoads,
        @Size(max = 4000) @JsonAlias("instructions") String specialInstructions,
        String trackingReference,
        String notes) {}
