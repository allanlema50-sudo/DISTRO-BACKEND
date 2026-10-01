package com.example.distrobackend.dto;

public record TripItemResponse(
        String id,
        String productName,
        String brand,
        String unitLabel,
        Integer quantity
) {
}
