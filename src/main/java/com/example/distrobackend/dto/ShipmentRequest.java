package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ShipmentRequest(@NotNull UUID orderId, UUID tripId, String trackingReference, String notes) {}
