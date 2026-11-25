package com.example.distrobackend.controller.admin;

import com.example.distrobackend.dto.CreditDefaultResponse;
import com.example.distrobackend.dto.PlatformFeeReportResponse;
import com.example.distrobackend.dto.PlatformFeeSettingResponse;
import com.example.distrobackend.dto.UpdateCreditDefaultRequest;
import com.example.distrobackend.dto.UpdatePlatformFeeRequest;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.CreditTermsService;
import com.example.distrobackend.service.PlatformFeeService;
import com.example.distrobackend.service.SettlementProofDocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Platform-wide purchase-order credit defaults, fees and payment proofs. Super admin only. */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminPurchaseOrderFeeController {

    private final PlatformFeeService platformFeeService;
    private final CreditTermsService creditTermsService;
    private final SettlementProofDocumentService proofService;

    @GetMapping("/fees/purchase-orders")
    public PlatformFeeSettingResponse getFee(@AuthenticationPrincipal AuthenticatedUser actor) {
        return platformFeeService.get(actor);
    }

    @PutMapping("/fees/purchase-orders")
    public PlatformFeeSettingResponse putFee(@AuthenticationPrincipal AuthenticatedUser actor,
                                             @Valid @RequestBody UpdatePlatformFeeRequest request) {
        return platformFeeService.update(actor, request);
    }

    @GetMapping("/reports/platform-fees")
    public PlatformFeeReportResponse feeReport(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String currency) {
        return platformFeeService.report(actor, from, to, currency);
    }

    @GetMapping("/credit-defaults")
    public List<CreditDefaultResponse> creditDefaults(@AuthenticationPrincipal AuthenticatedUser actor) {
        return creditTermsService.listDefaults(actor);
    }

    @PutMapping("/credit-defaults")
    public CreditDefaultResponse putCreditDefault(@AuthenticationPrincipal AuthenticatedUser actor,
                                                  @Valid @RequestBody UpdateCreditDefaultRequest request) {
        return creditTermsService.putDefault(actor, request);
    }

    @GetMapping("/purchase-orders/{purchaseOrderId}/settlement/proofs/{documentId}")
    public ResponseEntity<byte[]> proof(@AuthenticationPrincipal AuthenticatedUser actor,
                                        @PathVariable UUID purchaseOrderId,
                                        @PathVariable UUID documentId) {
        return com.example.distrobackend.controller.ProofDownloadResponses
                .of(proofService.download(actor, purchaseOrderId, documentId));
    }
}