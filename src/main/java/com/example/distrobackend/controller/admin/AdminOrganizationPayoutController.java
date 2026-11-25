package com.example.distrobackend.controller.admin;

import com.example.distrobackend.dto.OrganizationPayoutResponse;
import com.example.distrobackend.dto.RecordOrganizationPayoutRequest;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.OrganizationPayoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/admin/organization-payouts", "/api/admin/organization-payouts"})
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminOrganizationPayoutController {

    private final OrganizationPayoutService payoutService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationPayoutResponse record(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RecordOrganizationPayoutRequest request) {
        return payoutService.record(actor, request, idempotencyKey);
    }
}
