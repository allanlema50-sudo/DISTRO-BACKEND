package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.CheckoutPayment;
import com.example.distrobackend.Domain.enums.CheckoutPaymentStatus;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.repository.CheckoutPaymentRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaystackTransactionMatchingServiceTest {
    @Mock PaystackPaymentService paystack;
    @Mock CheckoutPaymentRepository paymentRepository;
    @InjectMocks PaystackTransactionMatchingService service;

    @Test
    void acceptsReversedProviderTransactionForRefundedLocalPayment() {
        assertCompatible("reversed", CheckoutPaymentStatus.REFUNDED);
    }

    @Test
    void acceptsOngoingAndProcessingProviderTransactionsForPendingLocalPayment() {
        assertCompatible("ongoing", CheckoutPaymentStatus.PENDING);
        assertCompatible("processing", CheckoutPaymentStatus.PENDING);
    }

    @Test
    void keepsSuccessfulProviderTransactionCompatibleWithPendingRefund() {
        assertCompatible("success", CheckoutPaymentStatus.REFUND_PENDING);
    }

    private void assertCompatible(String providerStatus, CheckoutPaymentStatus localStatus) {
        String reference = "PAYSTACK-REF-" + UUID.randomUUID();
        CheckoutPayment payment = new CheckoutPayment();
        payment.setId(UUID.randomUUID());
        payment.setProviderReference(reference);
        payment.setStatus(localStatus);
        payment.setAmount(new BigDecimal("125.50"));
        payment.setCurrency("KES");

        OffsetDateTime from = OffsetDateTime.parse("2026-11-01T00:00:00Z");
        OffsetDateTime to = from.plusDays(1);
        when(paystack.listTransactions(from, to, 1, 50)).thenReturn(
                new PaystackPaymentService.ProviderTransactionPage(List.of(
                        new PaystackPaymentService.ProviderTransaction("provider-id", reference,
                                providerStatus, 12550L, "KES", null)), 1, 50, 1));
        when(paymentRepository.findByProviderAndProviderReferenceIn(any(), eq(List.of(reference))))
                .thenReturn(List.of(payment));

        var result = service.compare(new AuthenticatedUser(UUID.randomUUID(), UserRole.SUPER_ADMIN,
                        null, null, null), from, to, 1, 50);

        assertThat(result.transactions()).singleElement()
                .satisfies(match -> assertThat(match.issues()).isEmpty());
    }
}
