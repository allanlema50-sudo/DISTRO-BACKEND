package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.OrderStatus;

import java.math.BigDecimal;
import java.util.Map;

public record OrderStatsResponse(Map<OrderStatus, Long> counts, BigDecimal totalAmount) {}
