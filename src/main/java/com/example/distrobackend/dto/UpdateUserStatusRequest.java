package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(@NotNull UserStatus status) {
}
