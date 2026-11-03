package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.DriverApprovalStatus;
import java.util.UUID;

public record DriverApprovalResponse(UUID driverId, DriverApprovalStatus status) {}
