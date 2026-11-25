package com.example.distrobackend.dto;

import jakarta.validation.constraints.Size;

public record CancelOverdueRequest(@Size(max = 1000) String note) {}