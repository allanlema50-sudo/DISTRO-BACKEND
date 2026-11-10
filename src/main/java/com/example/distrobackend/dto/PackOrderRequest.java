package com.example.distrobackend.dto;

import jakarta.validation.constraints.Positive;

public record PackOrderRequest(@Positive int packageCount) {}
