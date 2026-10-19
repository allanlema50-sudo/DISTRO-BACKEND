package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotNull;

public record ProductStatusRequest(@NotNull Boolean active) {}
