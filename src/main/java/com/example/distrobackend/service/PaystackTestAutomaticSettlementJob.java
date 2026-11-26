package com.example.distrobackend.service;

import com.example.distrobackend.repository.SettlementBatchRepository;
import com.example.distrobackend.repository.SupplierSettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * TEST-only convenience worker. It creates auditable automatic batches only after receipt
 * and the configured hold, and resumes batches committed before a process restart.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "paystack.test-auto-settlement-enabled", havingValue = "true")
public class PaystackTestAutomaticSettlementJob {
    private final SupplierSettlementRepository settlementRepository;
    private final SettlementBatchRepository batchRepository;
    private final PaystackSettlementService settlementService;

    @Value("${logiflow.settlements.delivery-hold-days:7}")
    private int deliveryHoldDays;

    @Value("${paystack.test-auto-settlement.batch-size:50}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${paystack.test-auto-settlement.scan-ms:30000}")
    public void dispatchEligibleAndResumeInterruptedBatches() {
        int limit = Math.max(1, Math.min(batchSize, 100));
        for (var batchId : batchRepository.findApprovedAutomaticTestBatchIds(limit)) {
            execute(batchId);
        }

        OffsetDateTime eligibleBefore = OffsetDateTime.now().minusDays(Math.max(0, deliveryHoldDays));
        for (var settlementId : settlementRepository
                .findEligibleUnbatchedPaystackSettlementIds(eligibleBefore, limit)) {
            try {
                var batchId = settlementService.createAutomaticTestBatchIfEligible(settlementId);
                if (batchId != null) execute(batchId);
            } catch (RuntimeException ex) {
                // Eligibility can change while a candidate is being claimed. The next scan retries safely.
                log.debug("TEST automatic payout skipped for settlement {}: {}", settlementId, ex.getMessage());
            }
        }
    }

    private void execute(java.util.UUID batchId) {
        try {
            settlementService.executeAutomaticTestBatch(batchId);
        } catch (RuntimeException ex) {
            log.warn("Could not execute TEST automatic settlement batch {}", batchId, ex);
        }
    }
}
