package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.CheckoutPayment;
import com.example.distrobackend.Domain.enums.PaymentProvider;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.repository.CheckoutPaymentRepository;
import com.example.distrobackend.repository.LedgerJournalRepository;
import com.example.distrobackend.repository.PaymentAllocationRepository;
import com.example.distrobackend.repository.PaymentDisputeRepository;
import com.example.distrobackend.repository.PaymentRefundRepository;
import com.example.distrobackend.repository.SupplierSettlementRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialReconciliationReportServiceTest {
    @Mock CheckoutPaymentRepository paymentRepository;
    @Mock PaymentAllocationRepository allocationRepository;
    @Mock SupplierSettlementRepository settlementRepository;
    @Mock PaymentRefundRepository refundRepository;
    @Mock PaymentDisputeRepository disputeRepository;
    @Mock LedgerJournalRepository journalRepository;
    @InjectMocks FinancialReconciliationReportService service;

    @Test
    void boundsPageAndUsesStableNewestFirstOrdering() {
        AuthenticatedUser actor = new AuthenticatedUser(UUID.randomUUID(), UserRole.SUPER_ADMIN,
                null, null, null);
        OffsetDateTime from = OffsetDateTime.parse("2026-11-01T00:00:00Z");
        OffsetDateTime to = from.plusDays(7);
        when(paymentRepository.findByProviderAndDemoFalseAndStatusInAndInitiatedAtGreaterThanEqualAndInitiatedAtLessThan(
                eq(PaymentProvider.PAYSTACK), any(), eq(from), eq(to), any(Pageable.class)))
                .thenAnswer(invocation -> Page.<CheckoutPayment>empty(invocation.getArgument(4)));

        service.report(actor, from, to, PageRequest.of(0, 500));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentRepository).findByProviderAndDemoFalseAndStatusInAndInitiatedAtGreaterThanEqualAndInitiatedAtLessThan(
                eq(PaymentProvider.PAYSTACK), any(), eq(from), eq(to), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getSort().toList()).containsExactly(
                Sort.Order.desc("initiatedAt"), Sort.Order.desc("id"));
    }

    @Test
    void readsTheReportFromOneRepeatableReadSnapshot() throws Exception {
        Transactional transactional = FinancialReconciliationReportService.class
                .getMethod("report", AuthenticatedUser.class, OffsetDateTime.class,
                        OffsetDateTime.class, Pageable.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }
}
