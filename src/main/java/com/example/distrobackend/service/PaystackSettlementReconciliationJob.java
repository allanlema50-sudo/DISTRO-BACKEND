package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.SupplierSettlement;
import com.example.distrobackend.repository.SupplierSettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaystackSettlementReconciliationJob {
    private final SupplierSettlementRepository settlementRepository;
    private final PaystackPaymentService paystackPaymentService;
    private final PaystackSettlementService settlementService;

    @Value("${paystack.reconciliation.initial-delay-ms:300000}")
    private long initialDelayMs;
    @Value("${paystack.reconciliation.repeat-delay-ms:300000}")
    private long repeatDelayMs;
    @Value("${paystack.reconciliation.batch-size:100}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${paystack.reconciliation.scan-ms:60000}")
    public void reconcileProcessingTransfers() {
        if (!paystackPaymentService.transfersEnabled()) return;
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime attemptCutoff = now.minusNanos(initialDelayMs * 1_000_000L);
        OffsetDateTime checkedBefore = now.minusNanos(repeatDelayMs * 1_000_000L);
        List<SupplierSettlement> candidates = settlementRepository.findPaystackTransfersNeedingVerification(
                attemptCutoff, checkedBefore, Math.max(1, Math.min(batchSize, 500)));
        for (SupplierSettlement candidate : candidates) {
            try {
                var reference = settlementService.claimTransferVerification(candidate.getId(),
                        attemptCutoff, checkedBefore, now);
                if (reference.isEmpty()) continue;
                var verified = paystackPaymentService.verifyTransfer(reference.get());
                if (verified.isEmpty()) {
                    settlementService.markPaystackTransferNotFound(reference.get());
                    continue;
                }
                var transfer = verified.get();
                String event = switch (transfer.status().toLowerCase(Locale.ROOT)) {
                    case "success" -> "transfer.success";
                    case "failed" -> "transfer.failed";
                    case "reversed" -> "transfer.reversed";
                    default -> null;
                };
                if (event != null) {
                    settlementService.processPaystackWebhook(new PaystackPaymentService.Webhook(
                            event, transfer.reference(), transfer.status(), transfer.amountMinor(),
                            transfer.currency(), transfer.failureReason(), transfer.transferCode()));
                }
            } catch (RuntimeException ex) {
                log.warn("Paystack transfer reconciliation failed for settlement {}: {}",
                        candidate.getId(), ex.getMessage());
            }
        }
    }
}
