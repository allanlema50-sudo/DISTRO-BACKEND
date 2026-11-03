package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotNull;

public record SharedLoadReviewRequest(@NotNull Boolean compatible, String note) {}
