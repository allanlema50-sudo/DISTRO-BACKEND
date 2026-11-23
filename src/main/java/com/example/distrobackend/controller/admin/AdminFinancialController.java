package com.example.distrobackend.controller.admin;

import com.example.distrobackend.dto.CommissionRuleResponse;
import com.example.distrobackend.dto.CreateCommissionRuleRequest;
import com.example.distrobackend.dto.LedgerJournalResponse;
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
}
