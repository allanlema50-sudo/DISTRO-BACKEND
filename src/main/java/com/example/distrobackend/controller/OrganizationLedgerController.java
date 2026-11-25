package com.example.distrobackend.controller;

import com.example.distrobackend.dto.OrganizationLedgerBalanceResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.OrganizationLedgerQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organization/ledger-balance")
@RequiredArgsConstructor
public class OrganizationLedgerController {

    private final OrganizationLedgerQueryService ledgerQueryService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public OrganizationLedgerBalanceResponse balance(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(defaultValue = "KES") String currency) {
        return ledgerQueryService.balance(actor, organizationId, currency);
    }
}
