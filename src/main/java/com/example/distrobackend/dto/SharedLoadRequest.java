package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SharedLoadRequest(@NotNull UUID proposedTripId, @NotNull UUID candidateTripId,
                                String note) {}
