package com.example.distrobackend.dto;

public record PayoutDestinationOptionResponse(
        String name,
        String code,
        String currency,
        String type,
        boolean active
) {}
