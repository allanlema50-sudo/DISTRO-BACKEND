package com.example.distrobackend.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
public record CreateCheckoutRequest(@NotEmpty @Valid List<SupplierCheckoutRequest> supplierOrders) {
 public record SupplierCheckoutRequest(@NotNull UUID supplierOrganizationId, @NotNull UUID destinationWarehouseId, @NotEmpty @Valid List<Item> items) {}
 public record Item(@NotNull UUID stockItemId, @Min(1) int quantity, UUID distributorOfferId) {}
}
