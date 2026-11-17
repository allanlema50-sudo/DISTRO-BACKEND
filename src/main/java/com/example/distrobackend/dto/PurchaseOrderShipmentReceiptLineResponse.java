package com.example.distrobackend.dto;

import java.util.UUID;

public record PurchaseOrderShipmentReceiptLineResponse(UUID purchaseOrderItemId,
        UUID stockItemId, String productName, int expectedQuantity, int acceptedQuantity,
        int damagedQuantity, int missingQuantity) {}
