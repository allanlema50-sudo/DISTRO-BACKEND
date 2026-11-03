package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.SharedLoadDecision;
import jakarta.validation.constraints.NotNull;

public record SharedLoadConfirmationRequest(@NotNull SharedLoadDecision decision, String note) {}
