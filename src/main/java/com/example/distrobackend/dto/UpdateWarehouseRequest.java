package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Versioned metadata update. Warehouse codes remain stable identifiers. */
public record UpdateWarehouseRequest(
        @Size(max = 150) String name,
        @Size(max = 2000) String address,
        @NotNull Long version
) {}
