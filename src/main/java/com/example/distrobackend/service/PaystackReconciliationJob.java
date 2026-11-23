package com.example.distrobackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaystackReconciliationJob {
    private final PaystackReconciliationService reconciliationService;

    @Value("${app.payments.reconciliation-batch-size:25}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${PAYMENT_RECONCILIATION_POLL_INTERVAL_MS:60000}")
    public void reconcileDuePaystackRecords() {
        int safeBatchSize = Math.max(1, Math.min(batchSize, 100));
        reconciliationService.claimPayments(safeBatchSize)
                .forEach(reconciliationService::reconcilePayment);
        reconciliationService.claimTransfers(safeBatchSize)
                .forEach(reconciliationService::reconcileTransfer);
    }
}
