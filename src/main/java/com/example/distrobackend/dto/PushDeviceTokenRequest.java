package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record PushDeviceTokenRequest(
        @NotBlank @Size(max = 4096) String token,
        @Pattern(regexp = "(?i)android|ios|web") String platform
) { }
