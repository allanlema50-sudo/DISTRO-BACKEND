package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record PickOrderItemRequest(@NotNull UUID orderItemId, @Positive int pickedQuantity) {}
