package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.OrganizationStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrganizationStatusRequest(@NotNull OrganizationStatus status) {}
