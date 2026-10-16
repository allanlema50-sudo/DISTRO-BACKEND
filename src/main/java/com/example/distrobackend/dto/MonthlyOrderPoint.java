package com.example.distrobackend.dto;

public record MonthlyOrderPoint(
        String month,
        long orders
) {
}