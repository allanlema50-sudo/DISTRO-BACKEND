package com.example.distrobackend.service;

import com.example.distrobackend.Domain.enums.OrderStatus;
import com.example.distrobackend.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Releases stock held by unpaid orders after the configured reservation window. */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderReservationExpiryJob {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final PaymentService paymentService;

    @Value("${app.orders.reservation-expiry-batch-size:100}")
    private int batchSize;

    private OffsetDateTime cursorExpiresAt;
    private UUID cursorId;

    @Scheduled(fixedDelayString = "${app.orders.reservation-expiry-scan-ms:60000}")
    public synchronized void releaseExpiredReservations() {
        int safeBatchSize = Math.max(1, batchSize);
        OffsetDateTime now = OffsetDateTime.now();
        List<OrderRepository.ExpiredReservationCandidate> candidates;
        if (cursorExpiresAt == null) {
            candidates = orderRepository.findExpiredReservationCandidates(
                    OrderStatus.PENDING, now, PageRequest.of(0, safeBatchSize));
        } else {
            candidates = orderRepository.findExpiredReservationCandidatesAfter(
                    OrderStatus.PENDING, now, cursorExpiresAt, cursorId,
                    PageRequest.of(0, safeBatchSize));
        }

        if (candidates.isEmpty()) {
            // Reached the end of the ordered set. The next invocation starts
            // at the beginning, retrying rows skipped after provider failures.
            cursorExpiresAt = null;
            cursorId = null;
            return;
        }

        for (OrderRepository.ExpiredReservationCandidate candidate : candidates) {
            UUID orderId = candidate.getId();
            try {
                if (!paymentService.reconcileExpiredPayment(orderId)) {
                    orderService.expireReservationAndFailOrder(orderId, now);
                }
            } catch (RuntimeException ex) {
                // The deadline remains expired, so a later scan retries
                // provider reconciliation; one bad row must not stop the batch.
                log.warn("Could not reconcile or expire unpaid order {}", orderId, ex);
            }
        }

        OrderRepository.ExpiredReservationCandidate last = candidates.get(candidates.size() - 1);
        cursorExpiresAt = last.getReservationExpiresAt();
        cursorId = last.getId();
    }
}
