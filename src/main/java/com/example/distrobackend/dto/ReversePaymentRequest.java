package com.example.distrobackend.dto;

import jakarta.validation.constraints.Size;

public record ReversePaymentRequest(@Size(max = 2000) String note) {}
