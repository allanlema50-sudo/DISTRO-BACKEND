package com.example.distrobackend.service;

import com.example.distrobackend.Domain.enums.PaymentStatus;
import com.example.distrobackend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
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
        List<UUID> paymentIds = paymentRepository.findIdsByStatus(
                PaymentStatus.CONFIRMED, PageRequest.of(0, Math.max(1, batchSize)));
        for (UUID paymentId : paymentIds) {
            try {
                paymentService.checkLedgerConsistency(paymentId);
            } catch (RuntimeException ex) {
                log.warn("Payment-ledger consistency check skipped payment {}: {}", paymentId, ex.getMessage());
            }
        }
    }
}
