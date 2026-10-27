package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.DistributorProductAuthorization;
import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.ProductAuthorizationStatus;
import com.example.distrobackend.Domain.enums.ProductPublicationStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.Exception.ResourceNotFoundException;
import com.example.distrobackend.dto.CreateProductAuthorizationRequest;
import com.example.distrobackend.dto.ProductAuthorizationDecisionRequest;
import com.example.distrobackend.dto.ProductAuthorizationResponse;
import com.example.distrobackend.repository.DistributorProductAuthorizationRepository;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductAuthorizationService {

    private final DistributorProductAuthorizationRepository authorizationRepository;
    private final StockItemRepository stockItemRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<ProductAuthorizationResponse> list(AuthenticatedUser actor, Pageable pageable) {
        UUID organizationId = requireOrganization(actor, Organizationtype.DISTRIBUTOR);
        return authorizationRepository.findByDistributorOrganization_IdOrderByCreatedAtDesc(organizationId, pageable)
                .map(ProductAuthorizationResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<ProductAuthorizationResponse> listForManufacturer(AuthenticatedUser actor,
                                                                   Pageable pageable) {
        UUID organizationId = requireOrganization(actor, Organizationtype.MANUFACTURER);
        return authorizationRepository.findByManufacturerOrganization_IdOrderByCreatedAtDesc(organizationId, pageable)
                .map(ProductAuthorizationResponse::from);
    }

    @Transactional
    public ProductAuthorizationResponse request(AuthenticatedUser actor,
                                                CreateProductAuthorizationRequest request) {
        Organization distributor = organization(actor, Organizationtype.DISTRIBUTOR);
        StockItem product = stockItemRepository.findById(request.manufacturerProductId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_ITEM_NOT_FOUND));
        if (product.getOrganization() == null
                || product.getOrganization().getType() != Organizationtype.MANUFACTURER
                || product.getSourceStockItem() != null
                || !product.isActive()
                || product.getPublicationStatus() != ProductPublicationStatus.PUBLISHED) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Authorization requests must target a published manufacturer product");
        }
        Organization manufacturer = product.getOrganization();
        DistributorProductAuthorization authorization = authorizationRepository
                .findByProduct_IdAndDistributorOrganization_Id(product.getId(), distributor.getId())
                .orElseGet(DistributorProductAuthorization::new);
        if (authorization.getStatus() == ProductAuthorizationStatus.PENDING
                || authorization.getStatus() == ProductAuthorizationStatus.APPROVED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "An active authorization request already exists for this product");
        }
        authorization.setProduct(product);
        authorization.setManufacturerOrganization(manufacturer);
        authorization.setDistributorOrganization(distributor);
        authorization.setStatus(ProductAuthorizationStatus.PENDING);
        authorization.setDecisionNote(null);
        authorization.setDecidedBy(null);
        authorization.setDecidedAt(null);
        authorization.setRequestedBy(userRepository.getReferenceById(actor.userId()));
        return ProductAuthorizationResponse.from(authorizationRepository.saveAndFlush(authorization));
    }

    @Transactional
    public ProductAuthorizationResponse decide(AuthenticatedUser actor, UUID authorizationId,
                                               ProductAuthorizationDecisionRequest request) {
        UUID manufacturerId = requireOrganization(actor, Organizationtype.MANUFACTURER);
        if (request.status() != ProductAuthorizationStatus.APPROVED
                && request.status() != ProductAuthorizationStatus.REJECTED
                && request.status() != ProductAuthorizationStatus.REVOKED) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Decision must be APPROVED, REJECTED, or REVOKED");
        }
        DistributorProductAuthorization authorization = authorizationRepository
                .findByIdAndManufacturerOrganizationForUpdate(authorizationId, manufacturerId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND,
                        "Product authorization not found"));
        ProductAuthorizationStatus current = authorization.getStatus();
        if (current == ProductAuthorizationStatus.REVOKED
                || current == ProductAuthorizationStatus.REJECTED
                || (current == ProductAuthorizationStatus.PENDING
                && request.status() == ProductAuthorizationStatus.REVOKED)
                || (current == ProductAuthorizationStatus.APPROVED
                && request.status() != ProductAuthorizationStatus.REVOKED)) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "The authorization status transition is not allowed");
        }
        authorization.setStatus(request.status());
        authorization.setDecisionNote(request.note() == null ? null : request.note().trim());
        authorization.setDecidedBy(userRepository.getReferenceById(actor.userId()));
        authorization.setDecidedAt(OffsetDateTime.now());
        return ProductAuthorizationResponse.from(authorizationRepository.save(authorization));
    }

    public boolean isApproved(UUID productId, UUID distributorId) {
        return authorizationRepository.existsByProduct_IdAndDistributorOrganization_IdAndStatus(
                productId, distributorId, ProductAuthorizationStatus.APPROVED);
    }

    private Organization organization(AuthenticatedUser actor, Organizationtype type) {
        UUID id = requireOrganization(actor, type);
        return organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND,
                        "Organization not found"));
    }

    private static UUID requireOrganization(AuthenticatedUser actor, Organizationtype type) {
        if (actor == null || actor.organizationId() == null || actor.organizationType() != type) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return actor.organizationId();
    }
}
