package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PushDeviceTokenDeleteRequest(@NotBlank @Size(max = 4096) String token) { }
