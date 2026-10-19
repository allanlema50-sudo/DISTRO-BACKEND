package com.example.distrobackend.dto;

public record ProductStatsResponse(
        long total,
        long active,
        long inactive,
        long lowStock
) {}
