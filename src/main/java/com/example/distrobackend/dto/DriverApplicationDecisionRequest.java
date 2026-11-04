package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotBlank;

public record DriverApplicationDecisionRequest(@NotBlank String decision, String note) {}
