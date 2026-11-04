package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotBlank;

public record DriverOperationalStatusRequest(@NotBlank String status) {}
