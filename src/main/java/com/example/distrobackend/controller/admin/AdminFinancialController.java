package com.example.distrobackend.controller.admin;

import com.example.distrobackend.dto.CommissionRuleResponse;
import com.example.distrobackend.dto.CreateCommissionRuleRequest;
import com.example.distrobackend.dto.LedgerJournalResponse;
import com.example.distrobackend.dto.CreateSettlementBatchRequest;
import com.example.distrobackend.dto.PayoutBeneficiaryResponse;
import com.example.distrobackend.dto.ReviewPayoutBeneficiaryRequest;
import com.example.distrobackend.dto.SettlementBatchResponse;
import com.example.distrobackend.dto.SupplierSettlementAdminResponse;
import com.example.distrobackend.dto.ProviderWebhookEventResponse;
import com.example.distrobackend.Domain.enums.SupplierSettlementStatus;
import com.example.distrobackend.Domain.enums.ProviderWebhookStatus;
import com.example.distrobackend.service.ProviderWebhookInboxService;
import com.example.distrobackend.service.PaystackSettlementService;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.CommissionRuleService;
import com.example.distrobackend.service.FinancialLedgerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/financial")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminFinancialController {
    private final CommissionRuleService commissionRuleService;
    private final FinancialLedgerService financialLedgerService;
    private final PaystackSettlementService paystackSettlementService;
    private final ProviderWebhookInboxService webhookInboxService;

    @GetMapping("/commission-rules")
    public List<CommissionRuleResponse> commissionRules(@AuthenticationPrincipal AuthenticatedUser actor) {
        return commissionRuleService.list(actor);
    }

    @PostMapping("/commission-rules")
    @ResponseStatus(HttpStatus.CREATED)
    public CommissionRuleResponse createCommissionRule(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody CreateCommissionRuleRequest request) {
        return commissionRuleService.create(actor, request);
    }

    @PostMapping("/commission-rules/{ruleId}/deactivate")
    public CommissionRuleResponse deactivateCommissionRule(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID ruleId) {
        return commissionRuleService.deactivate(actor, ruleId);
    }

    @GetMapping("/ledger/journals")
    public Page<LedgerJournalResponse> ledgerJournals(
            @AuthenticationPrincipal AuthenticatedUser actor, Pageable pageable) {
        return financialLedgerService.list(actor, pageable);
    }

    @GetMapping("/ledger/journals/{journalId}")
    public LedgerJournalResponse ledgerJournal(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID journalId) {
        return financialLedgerService.get(actor, journalId);
    }

    @GetMapping("/beneficiaries")
    public Page<PayoutBeneficiaryResponse> beneficiaries(
            @AuthenticationPrincipal AuthenticatedUser actor, Pageable pageable) {
        return paystackSettlementService.listBeneficiaries(actor, pageable);
    }

    @PatchMapping("/beneficiaries/{beneficiaryId}/review")
    public PayoutBeneficiaryResponse reviewBeneficiary(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID beneficiaryId,
            @Valid @RequestBody ReviewPayoutBeneficiaryRequest request) {
        return paystackSettlementService.reviewBeneficiary(actor, beneficiaryId, request);
    }

    @GetMapping("/settlements")
    public Page<SupplierSettlementAdminResponse> settlements(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) SupplierSettlementStatus status,
            Pageable pageable) {
        return paystackSettlementService.listSettlements(actor, status, pageable);
    }

    @GetMapping("/settlement-batches")
    public Page<SettlementBatchResponse> settlementBatches(
            @AuthenticationPrincipal AuthenticatedUser actor, Pageable pageable) {
        return paystackSettlementService.listBatches(actor, pageable);
    }

    @PostMapping("/settlement-batches")
    @ResponseStatus(HttpStatus.CREATED)
    public SettlementBatchResponse createSettlementBatch(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody CreateSettlementBatchRequest request) {
        return paystackSettlementService.createBatch(actor, request);
    }

    @PostMapping("/settlement-batches/{batchId}/approve")
    public SettlementBatchResponse approveSettlementBatch(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID batchId) {
        return paystackSettlementService.approveBatch(actor, batchId);
    }

    @PostMapping("/settlement-batches/{batchId}/execute")
    public SettlementBatchResponse executeSettlementBatch(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID batchId) {
        return paystackSettlementService.executeBatch(actor, batchId);
    }

    @PostMapping("/settlements/{settlementId}/retry")
    public SupplierSettlementAdminResponse retrySettlement(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID settlementId) {
        return paystackSettlementService.retrySettlement(actor, settlementId);
    }

    @GetMapping("/webhook-events")
    public Page<ProviderWebhookEventResponse> webhookEvents(
            @RequestParam(required = false) ProviderWebhookStatus status, Pageable pageable) {
        return webhookInboxService.list(status, pageable);
    }

    @PostMapping("/webhook-events/{eventId}/retry")
    public ProviderWebhookEventResponse retryWebhookEvent(@PathVariable UUID eventId) {
        return webhookInboxService.retryDeadLetter(eventId);
    }
}
