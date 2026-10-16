package com.example.distrobackend.dto;

import java.math.BigDecimal;

public record MonthlyRevenuePoint(
        String month,
        BigDecimal revenue
) {
}