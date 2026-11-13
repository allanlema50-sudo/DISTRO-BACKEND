package com.example.distrobackend.controller;

import com.example.distrobackend.dto.CatalogManufacturerResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.ProductCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/distributor/catalog")
@RequiredArgsConstructor
public class DistributorCatalogController {

    private final ProductCatalogService productCatalogService;

    @GetMapping("/manufacturers")
    @PreAuthorize("hasAnyRole('DISTRIBUTOR_ADMIN','DISTRIBUTOR_STAFF') and @tenantAccess.hasOrganization(authentication)")
    public List<CatalogManufacturerResponse> manufacturers(
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return productCatalogService.manufacturers(actor);
    }
}
