package com.example.distrobackend.service;

import com.example.distrobackend.Domain.enums.OrderStatus;
import com.example.distrobackend.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderReservationExpiryJobTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderService orderService;
    @Mock private PaymentService paymentService;

    private OrderReservationExpiryJob job;

    @BeforeEach
    void setUp() {
        job = new OrderReservationExpiryJob(orderRepository, orderService, paymentService);
        ReflectionTestUtils.setField(job, "batchSize", 2);
    }

    @Test
    void advancesCursorWhenOldestBatchCannotBeReconciled() {
        OffsetDateTime firstExpiry = OffsetDateTime.parse("2026-10-08T08:00:00Z");
        OffsetDateTime secondExpiry = OffsetDateTime.parse("2026-10-08T08:01:00Z");
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UUID laterId = UUID.randomUUID();
        OrderRepository.ExpiredReservationCandidate first = candidate(firstId, firstExpiry);
        OrderRepository.ExpiredReservationCandidate second = candidate(secondId, secondExpiry);
        OrderRepository.ExpiredReservationCandidate later = candidate(
                laterId, OffsetDateTime.parse("2026-10-08T08:02:00Z"));

        when(orderRepository.findLatestExpiredReservationCandidates(
                eq(OrderStatus.PENDING), any(OffsetDateTime.class), eq(PageRequest.of(0, 1))))
                .thenReturn(List.of(later))
                .thenReturn(List.of(later));
        when(orderRepository.findExpiredReservationCandidatesThrough(
                eq(OrderStatus.PENDING), any(OffsetDateTime.class), eq(later.getReservationExpiresAt()),
                eq(laterId), eq(PageRequest.of(0, 2))))
                .thenReturn(List.of(first, second))
                .thenReturn(List.of(first, second));
        when(orderRepository.findExpiredReservationCandidatesAfterThrough(
                eq(OrderStatus.PENDING), any(OffsetDateTime.class), eq(secondExpiry), eq(secondId),
                eq(later.getReservationExpiresAt()), eq(laterId), eq(PageRequest.of(0, 2))))
                .thenReturn(List.of(later));
        when(orderRepository.findExpiredReservationCandidatesAfterThrough(
                eq(OrderStatus.PENDING), any(OffsetDateTime.class),
                eq(later.getReservationExpiresAt()), eq(laterId),
                eq(later.getReservationExpiresAt()), eq(laterId), eq(PageRequest.of(0, 2))))
                .thenReturn(List.of());
        when(paymentService.reconcileExpiredPayment(any(UUID.class)))
                .thenThrow(new RuntimeException("provider unavailable"));

        job.releaseExpiredReservations();
        job.releaseExpiredReservations();
        job.releaseExpiredReservations();
        job.releaseExpiredReservations();

        verify(paymentService, org.mockito.Mockito.times(2)).reconcileExpiredPayment(firstId);
        verify(paymentService, org.mockito.Mockito.times(2)).reconcileExpiredPayment(secondId);
        verify(paymentService).reconcileExpiredPayment(laterId);
        verify(orderRepository, org.mockito.Mockito.times(2)).findLatestExpiredReservationCandidates(
                eq(OrderStatus.PENDING), any(OffsetDateTime.class), eq(PageRequest.of(0, 1)));
    }

    private static OrderRepository.ExpiredReservationCandidate candidate(
            UUID id, OffsetDateTime expiresAt) {
        return new OrderRepository.ExpiredReservationCandidate() {
            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public OffsetDateTime getReservationExpiresAt() {
                return expiresAt;
            }
        };
    }
}
