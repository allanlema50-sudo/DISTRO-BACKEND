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
    private OffsetDateTime cycleUpperExpiresAt;
    private UUID cycleUpperId;

    @Scheduled(fixedDelayString = "${app.orders.reservation-expiry-scan-ms:60000}")
    public synchronized void releaseExpiredReservations() {
        int safeBatchSize = Math.max(1, batchSize);
        OffsetDateTime now = OffsetDateTime.now();
        List<OrderRepository.ExpiredReservationCandidate> candidates;
        if (cursorExpiresAt == null) {
            List<OrderRepository.ExpiredReservationCandidate> latest =
                    orderRepository.findLatestExpiredReservationCandidates(
                            OrderStatus.PENDING, now, PageRequest.of(0, 1));
            if (latest.isEmpty()) {
                return;
            }
            OrderRepository.ExpiredReservationCandidate upperBound = latest.get(0);
            cycleUpperExpiresAt = upperBound.getReservationExpiresAt();
            cycleUpperId = upperBound.getId();
            candidates = orderRepository.findExpiredReservationCandidatesThrough(
                    OrderStatus.PENDING, now, cycleUpperExpiresAt, cycleUpperId,
                    PageRequest.of(0, safeBatchSize));
        } else {
            candidates = orderRepository.findExpiredReservationCandidatesAfterThrough(
                    OrderStatus.PENDING, now, cursorExpiresAt, cursorId,
                    cycleUpperExpiresAt, cycleUpperId,
                    PageRequest.of(0, safeBatchSize));
        }

        if (candidates.isEmpty()) {
            // Reached the end of the ordered set. The next invocation starts
            // at the beginning, retrying rows skipped after provider failures.
            cursorExpiresAt = null;
            cursorId = null;
            cycleUpperExpiresAt = null;
            cycleUpperId = null;
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
