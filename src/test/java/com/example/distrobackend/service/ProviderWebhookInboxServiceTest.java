package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.ProviderWebhookEvent;
import com.example.distrobackend.Domain.enums.ProviderWebhookStatus;
import com.example.distrobackend.repository.ProviderWebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderWebhookInboxServiceTest {
    @Mock private ProviderWebhookEventRepository repository;

    private ProviderWebhookInboxService service;

    @BeforeEach
    void setUp() {
        service = new ProviderWebhookInboxService(repository);
        ReflectionTestUtils.setField(service, "maxAttempts", 3);
        ReflectionTestUtils.setField(service, "processingTimeout", Duration.ofMinutes(5));
    }

    @Test
    void acceptsDuplicateDeliveriesThroughAtomicInsert() {
        byte[] payload = "{\"event\":\"charge.success\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        PaystackPaymentService.Webhook webhook = new PaystackPaymentService.Webhook(
                "charge.success", "reference-1", "success", 100L, "KES", "Approved", null);

        service.accept("PAYSTACK", webhook, payload);

        verify(repository).insertIfAbsent(any(UUID.class), eq("PAYSTACK"), anyString(),
                eq("charge.success"), eq("reference-1"), eq("success"), eq(100L),
                eq("reference-1"), eq("KES"), eq("Approved"), isNull(), isNull(), eq("success"),
                isNull(), isNull());
    }

    @Test
    void staleWorkerCannotCompleteAClaimTakenOverByAnotherWorker() {
        UUID eventId = UUID.randomUUID();
        UUID activeClaim = UUID.randomUUID();
        ProviderWebhookEvent event = new ProviderWebhookEvent();
        event.setId(eventId);
        event.setStatus(ProviderWebhookStatus.PROCESSING);
        event.setClaimToken(activeClaim);
        when(repository.findByIdForUpdate(eventId)).thenReturn(Optional.of(event));

        service.markProcessed(eventId, UUID.randomUUID());

        org.assertj.core.api.Assertions.assertThat(event.getStatus())
                .isEqualTo(ProviderWebhookStatus.PROCESSING);
        org.assertj.core.api.Assertions.assertThat(event.getClaimToken()).isEqualTo(activeClaim);
    }
}
