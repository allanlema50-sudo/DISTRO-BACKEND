package com.example.distrobackend.dto;

import jakarta.validation.constraints.Size;

public record ReceiveShipmentRequest(@Size(max = 2000) String note) {
}
