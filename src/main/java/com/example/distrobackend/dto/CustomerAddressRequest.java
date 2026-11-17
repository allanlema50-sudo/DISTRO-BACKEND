package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerAddressRequest(
        @NotBlank @Size(max = 80) String label,
        @NotBlank @Size(max = 150) String recipientName,
        @NotBlank @Size(max = 20) String phoneNumber,
        @NotBlank @Size(max = 2000) String addressLine,
        @Size(max = 100) String city,
        @Size(max = 100) String county,
        Double latitude,
        Double longitude,
        boolean defaultAddress
) { }
