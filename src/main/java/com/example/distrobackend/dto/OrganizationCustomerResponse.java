package com.example.distrobackend.dto;

import java.util.UUID;

public record OrganizationCustomerResponse(
        UUID id,
        String storeName,
        String destinationName,
        String destinationAddress
) {}
