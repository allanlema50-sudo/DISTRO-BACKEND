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

    @Scheduled(fixedDelayString = "${app.orders.reservation-expiry-scan-ms:60000}")
    public void releaseExpiredReservations() {
        int safeBatchSize = Math.max(1, batchSize);
        OffsetDateTime now = OffsetDateTime.now();
        for (UUID orderId : orderRepository.findExpiredReservationOrderIds(
                OrderStatus.PENDING, now, PageRequest.of(0, safeBatchSize))) {
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
    }
}
