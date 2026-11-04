package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.DriverApprovalStatus;
import jakarta.validation.constraints.NotNull;

public record DriverApprovalRequest(@NotNull DriverApprovalStatus status, String reason) {}
