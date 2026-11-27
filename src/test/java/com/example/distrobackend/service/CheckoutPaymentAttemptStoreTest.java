package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.CheckoutPayment;
import com.example.distrobackend.Domain.enums.PaymentProvider;
import com.example.distrobackend.repository.CheckoutPaymentRepository;
import com.example.distrobackend.repository.CheckoutRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutPaymentAttemptStoreTest {
    @Mock
    private CheckoutRepository checkoutRepository;

    @Mock
    private CheckoutPaymentRepository checkoutPaymentRepository;

    @InjectMocks
    private CheckoutPaymentAttemptStore attemptStore;

    @Test
    void preparePaystackSnapshotsTheModeBeforeProviderInitialization() {
        UUID paymentId = UUID.randomUUID();
        CheckoutPayment payment = new CheckoutPayment();
        when(checkoutPaymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        attemptStore.preparePaystack(paymentId, "LF-CHECKOUT-123", "TEST");

        assertThat(payment.getProvider()).isEqualTo(PaymentProvider.PAYSTACK);
        assertThat(payment.getProviderMode()).isEqualTo("TEST");
        assertThat(payment.getProviderReference()).isEqualTo("LF-CHECKOUT-123");
        verify(checkoutPaymentRepository).saveAndFlush(payment);
    }
}
