package com.example.distrobackend.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.UUID;

public record ReceiveShipmentRequest(@Size(max = 2000) String note,
                                    @Size(max = 100) List<@Valid Line> lines) {
    public record Line(@NotNull UUID purchaseOrderItemId,
                       @PositiveOrZero int acceptedQuantity,
                       @PositiveOrZero int damagedQuantity,
                       @PositiveOrZero int missingQuantity) {}
}
