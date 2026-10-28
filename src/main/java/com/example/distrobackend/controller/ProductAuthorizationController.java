package com.example.distrobackend.controller;

import com.example.distrobackend.dto.CreateProductAuthorizationRequest;
import com.example.distrobackend.dto.ProductAuthorizationDecisionRequest;
import com.example.distrobackend.dto.ProductAuthorizationResponse;
import com.example.distrobackend.Domain.enums.ProductAuthorizationStatus;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.ProductAuthorizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductAuthorizationController {

    private final ProductAuthorizationService service;

    @GetMapping("/authorizations")
    @PreAuthorize("hasAnyRole('DISTRIBUTOR_ADMIN','DISTRIBUTOR_STAFF') and @tenantAccess.hasOrganization(authentication)")
    public Page<ProductAuthorizationResponse> list(@AuthenticationPrincipal AuthenticatedUser actor,
                                                   Pageable pageable) {
        return service.list(actor, pageable);
    }

    @GetMapping("/authorizations/incoming")
    @PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','MANUFACTURER_STAFF') and @tenantAccess.hasOrganization(authentication)")
    public Page<ProductAuthorizationResponse> incoming(@AuthenticationPrincipal AuthenticatedUser actor,
                                                       Pageable pageable) {
        return service.listForManufacturer(actor, pageable);
    }

    @PostMapping("/{productId}/authorization-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('DISTRIBUTOR_ADMIN','DISTRIBUTOR_STAFF') and @tenantAccess.hasOrganization(authentication)")
    public ProductAuthorizationResponse request(@AuthenticationPrincipal AuthenticatedUser actor,
                                                @PathVariable UUID productId) {
        return service.request(actor, new CreateProductAuthorizationRequest(productId));
    }

    @PatchMapping("/authorizations/{authorizationId}")
    @PreAuthorize("hasRole('MANUFACTURER_ADMIN') and @tenantAccess.hasOrganization(authentication)")
    public ProductAuthorizationResponse decide(@AuthenticationPrincipal AuthenticatedUser actor,
                                               @PathVariable UUID authorizationId,
                                               @Valid @RequestBody ProductAuthorizationDecisionRequest request) {
        return service.decide(actor, authorizationId, request);
    }

    @PatchMapping("/authorizations/{authorizationId}/decision")
    @PreAuthorize("hasRole('MANUFACTURER_ADMIN') and @tenantAccess.hasOrganization(authentication)")
    public ProductAuthorizationResponse decideWithExplicitAction(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID authorizationId,
            @Valid @RequestBody ProductAuthorizationDecisionRequest request) {
        return service.decide(actor, authorizationId, request);
    }

    @PatchMapping("/authorizations/{authorizationId}/revocation")
    @PreAuthorize("hasRole('MANUFACTURER_ADMIN') and @tenantAccess.hasOrganization(authentication)")
    public ProductAuthorizationResponse revoke(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID authorizationId,
            @Valid @RequestBody(required = false) ProductAuthorizationDecisionRequest request) {
        ProductAuthorizationDecisionRequest revocation = request == null
                ? new ProductAuthorizationDecisionRequest(
                ProductAuthorizationStatus.REVOKED, null)
                : new ProductAuthorizationDecisionRequest(
                ProductAuthorizationStatus.REVOKED, request.note());
        return service.decide(actor, authorizationId, revocation);
    }
}
