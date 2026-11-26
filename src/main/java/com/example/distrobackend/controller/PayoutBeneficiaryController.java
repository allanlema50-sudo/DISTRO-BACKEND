package com.example.distrobackend.controller;

import com.example.distrobackend.dto.CreatePayoutBeneficiaryRequest;
import com.example.distrobackend.dto.PayoutBeneficiaryResponse;
import com.example.distrobackend.dto.PayoutDestinationOptionResponse;
import com.example.distrobackend.dto.ResolvePayoutBankAccountRequest;
import com.example.distrobackend.dto.ResolvedPayoutBankAccountResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.PaystackSettlementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/finance/beneficiaries")
@PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','DISTRIBUTOR_ADMIN')")
@RequiredArgsConstructor
public class PayoutBeneficiaryController {
    private final PaystackSettlementService settlementService;

    @PostMapping
    public PayoutBeneficiaryResponse create(@AuthenticationPrincipal AuthenticatedUser actor,
                                            @Valid @RequestBody CreatePayoutBeneficiaryRequest request) {
        return settlementService.createBeneficiary(actor, request);
    }

    @GetMapping
    public Page<PayoutBeneficiaryResponse> list(@AuthenticationPrincipal AuthenticatedUser actor,
                                                Pageable pageable) {
        return settlementService.listOwnBeneficiaries(actor, pageable);
    }

    @GetMapping("/banks")
    public List<PayoutDestinationOptionResponse> banks(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(defaultValue = "KES") String currency) {
        return settlementService.listBankDestinations(actor, currency);
    }

    @GetMapping("/mobile-money-providers")
    public List<PayoutDestinationOptionResponse> mobileMoneyProviders(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(defaultValue = "KES") String currency) {
        return settlementService.listMobileMoneyDestinations(actor, currency);
    }

    @PostMapping("/resolve-bank-account")
    public ResolvedPayoutBankAccountResponse resolveBankAccount(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody ResolvePayoutBankAccountRequest request) {
        return settlementService.resolveBankAccount(actor, request);
    }
}
