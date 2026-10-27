package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateWarehouseStatusRequest(@NotNull Boolean active, @NotNull Long version) {}
