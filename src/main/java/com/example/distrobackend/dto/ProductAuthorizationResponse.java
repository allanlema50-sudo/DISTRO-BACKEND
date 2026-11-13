package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.DistributorProductAuthorization;
import com.example.distrobackend.Domain.enums.ProductAuthorizationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductAuthorizationResponse(
        UUID id,
        UUID manufacturerProductId,
        UUID manufacturerOrganizationId,
        UUID distributorOrganizationId,
        ProductAuthorizationStatus status,
        String requestNote,
        String decisionNote,
        String targetMarket,
        String salesChannels,
        String geographicalCoverage,
        String distributionStrategy,
        java.math.BigDecimal agreedWholesalePrice,
        Integer minimumOrderQuantity,
        OffsetDateTime createdAt,
        OffsetDateTime decidedAt
) {
    public static ProductAuthorizationResponse from(DistributorProductAuthorization authorization) {
        return new ProductAuthorizationResponse(
                authorization.getId(),
                authorization.getProduct().getId(),
                authorization.getManufacturerOrganization().getId(),
                authorization.getDistributorOrganization().getId(),
                authorization.getStatus(),
                authorization.getRequestNote(),
                authorization.getDecisionNote(),
                authorization.getTargetMarket(),
                authorization.getSalesChannels(),
                authorization.getGeographicalCoverage(),
                authorization.getDistributionStrategy(),
                authorization.getAgreedWholesalePrice(),
                authorization.getMinimumOrderQuantity(),
                authorization.getCreatedAt(),
                authorization.getDecidedAt());
    }
}
