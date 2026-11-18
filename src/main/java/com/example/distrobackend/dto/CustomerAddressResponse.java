package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.CustomerAddress;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomerAddressResponse(
        UUID id, String label, String recipientName, String phoneNumber, String addressLine,
        String city, String county, Double latitude, Double longitude, boolean defaultAddress,
        OffsetDateTime createdAt, OffsetDateTime updatedAt
) {
    public static CustomerAddressResponse from(CustomerAddress address) {
        return new CustomerAddressResponse(address.getId(), address.getLabel(), address.getRecipientName(),
                address.getPhoneNumber(), address.getAddressLine(), address.getCity(), address.getCounty(),
                address.getLatitude(), address.getLongitude(), address.isDefaultAddress(),
                address.getCreatedAt(), address.getUpdatedAt());
    }
}
