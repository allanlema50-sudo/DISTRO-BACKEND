package com.example.distrobackend.service;

import com.example.distrobackend.Domain.enums.PaymentStatus;
import com.example.distrobackend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Nightly payment-ledger consistency check. This does not read an M-Pesa
 * statement; see {@link PaymentStatus#RECONCILED}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentLedgerConsistencyJob {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    @Value("${payments.ledger-consistency-batch-size:100}")
    private int batchSize;

    @Scheduled(cron = "${payments.ledger-consistency-cron:0 0 3 * * *}", zone = "Africa/Nairobi")
    public void checkConfirmedPayments() {
        int pageSize = Math.max(1, batchSize);
        Set<UUID> failedThisRun = new HashSet<>();
        int page = 0;
        while (true) {
            List<UUID> paymentIds = paymentRepository.findIdsByStatus(
                    PaymentStatus.CONFIRMED, PageRequest.of(page, pageSize));
            if (paymentIds.isEmpty()) {
                break;
            }
            List<UUID> candidates = paymentIds.stream()
                    .filter(id -> !failedThisRun.contains(id))
                    .toList();
            if (candidates.isEmpty()) {
                // Page is only payments that already failed this run; advance past them.
                if (paymentIds.size() < pageSize) {
                    break;
                }
                page++;
                continue;
            }
            boolean anySuccess = false;
            for (UUID paymentId : candidates) {
                try {
                    paymentService.checkLedgerConsistency(paymentId);
                    anySuccess = true;
                } catch (RuntimeException ex) {
                    failedThisRun.add(paymentId);
                    log.warn("Payment-ledger consistency check skipped payment {}: {}",
                            paymentId, ex.getMessage());
                }
            }
            if (paymentIds.size() < pageSize) {
                break;
            }
            // Successes leave CONFIRMED, so re-read from the front. A page of only
            // failures advances so later eligible payments are not starved.
            page = anySuccess ? 0 : page + 1;
        }
    }
}
