package com.example.distrobackend.controller.admin;

import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.CreateOrganizationRequest;
import com.example.distrobackend.dto.OrganizationResponse;
import com.example.distrobackend.dto.UpdateOrganizationProfileRequest;
import com.example.distrobackend.dto.UpdateOrganizationStatusRequest;
import com.example.distrobackend.dto.UserResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.OrganizationAdministrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/distributors")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class DistributorAdminController {
    private static final Organizationtype TYPE = Organizationtype.DISTRIBUTOR;
    private final OrganizationAdministrationService service;

    @GetMapping public Page<OrganizationResponse> list(@AuthenticationPrincipal AuthenticatedUser actor, Pageable pageable) {
        return service.list(actor, TYPE, pageable);
    }
    @GetMapping("/{id}") public OrganizationResponse get(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        OrganizationResponse response = service.get(actor, id);
        if (response.type() != TYPE) throw new ApiException(ErrorCode.NOT_FOUND, "Distributor not found");
        return response;
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public OrganizationResponse create(@AuthenticationPrincipal AuthenticatedUser actor, @Valid @RequestBody CreateOrganizationRequest request) {
        if (request.type() != TYPE) throw new ApiException(ErrorCode.BAD_REQUEST, "type must be DISTRIBUTOR");
        return service.create(actor, TYPE, request);
    }
    @PutMapping("/{id}") public OrganizationResponse update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id, @Valid @RequestBody UpdateOrganizationProfileRequest request) {
        return service.update(actor, TYPE, id, request);
    }
    @PatchMapping("/{id}/status") public OrganizationResponse status(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id, @Valid @RequestBody UpdateOrganizationStatusRequest request) {
        return service.updateStatus(actor, TYPE, id, request);
    }
    @GetMapping("/{id}/users") public Page<UserResponse> users(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id, Pageable pageable) {
        return service.users(actor, TYPE, id, pageable);
    }
}
