package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWarehouseRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 2000) String address,
        @NotBlank @Size(max = 60) String city,
        @Size(max = 20) String postalCode,
        @NotBlank @Size(max = 80) String siteManager,
        Double latitude,
        Double longitude,
        @Size(max = 500) String locationDescription
) {
    public CreateWarehouseRequest(String code, String name, String address, String city,
                                  String postalCode, String siteManager) {
        this(code, name, address, city, postalCode, siteManager, null, null, null);
    }
}
