package com.example.distrobackend.dto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
public record RetailPriceUpdateRequest(@NotNull @DecimalMin(value="0.01") BigDecimal retailPrice) {}
