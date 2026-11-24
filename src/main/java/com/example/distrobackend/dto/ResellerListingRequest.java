package com.example.distrobackend.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;
public record ResellerListingRequest(@NotNull UUID manufacturerProductId, @NotNull @DecimalMin(value="0.01") BigDecimal retailPrice) {}
