package com.example.distrobackend.dto;

import java.util.UUID;

public record StockLocationResponse(UUID id, UUID stockItemId, String sku,
                                    String locationCode, int quantity) {}
