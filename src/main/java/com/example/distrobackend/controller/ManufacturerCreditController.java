package com.example.distrobackend.controller;

import com.example.distrobackend.dto.CreditStatementResponse;
import com.example.distrobackend.dto.DistributorCreditResponse;
import com.example.distrobackend.dto.UpdateCreditTermsRequest;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.CreditTermsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/manufacturer/distributors/{distributorId}")
@PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','MANUFACTURER_STAFF')")
@RequiredArgsConstructor
public class ManufacturerCreditController {

    private final CreditTermsService creditTermsService;

    @GetMapping("/credit")
    public DistributorCreditResponse get(@AuthenticationPrincipal AuthenticatedUser actor,
                                         @PathVariable UUID distributorId) {
        return creditTermsService.getCredit(actor, distributorId);
    }

    @PutMapping("/credit")
    @PreAuthorize("hasRole('MANUFACTURER_ADMIN')")
    public DistributorCreditResponse put(@AuthenticationPrincipal AuthenticatedUser actor,
                                         @PathVariable UUID distributorId,
                                         @Valid @RequestBody UpdateCreditTermsRequest request) {
        return creditTermsService.putCredit(actor, distributorId, request);
    }

    @GetMapping("/statement")
    public CreditStatementResponse statement(@AuthenticationPrincipal AuthenticatedUser actor,
                                             @PathVariable UUID distributorId) {
        return creditTermsService.statement(actor, distributorId);
    }
}