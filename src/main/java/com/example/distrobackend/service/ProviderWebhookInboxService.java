package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.ProviderWebhookEvent;
import com.example.distrobackend.Domain.enums.ProviderWebhookStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.ProviderWebhookEventResponse;
import com.example.distrobackend.repository.ProviderWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProviderWebhookInboxService {
    private static final Set<ProviderWebhookStatus> DUE = Set.of(
            ProviderWebhookStatus.RECEIVED, ProviderWebhookStatus.RETRY);

    private final ProviderWebhookEventRepository repository;

    @Value("${app.payments.webhook-max-attempts:8}")
    private int maxAttempts;

    @Value("${app.payments.webhook-processing-timeout:5m}")
    private java.time.Duration processingTimeout;

    @Transactional
    public void accept(String provider, PaystackPaymentService.Webhook webhook, byte[] rawBody) {
        if (rawBody == null || rawBody.length == 0 || rawBody.length > 1_048_576) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Webhook payload must be between 1 byte and 1 MiB");
        }
        String key = sha256(rawBody);
        if (repository.existsByEventKey(key)) return;
        ProviderWebhookEvent event = new ProviderWebhookEvent();
        event.setProvider(provider);
        event.setEventKey(key);
        event.setEventName(webhook.event());
        event.setProviderReference(webhook.reference());
        event.setProviderStatus(webhook.status());
        event.setAmountMinor(webhook.amountMinor());
        event.setCurrency(webhook.currency());
        event.setGatewayResponse(webhook.gatewayResponse());
        event.setTransferCode(webhook.transferCode());
        repository.saveAndFlush(event);
    }

    @Transactional
    public int recoverStaleProcessing() {
        if (processingTimeout.isZero() || processingTimeout.isNegative()) {
            throw new IllegalStateException("app.payments.webhook-processing-timeout must be positive");
        }
        return repository.recoverStaleProcessing(OffsetDateTime.now().minus(processingTimeout));
    }

    @Transactional
    public UUID claimNext() {
        if (maxAttempts < 1) throw new IllegalStateException("app.payments.webhook-max-attempts must be positive");
        return repository.lockDue(DUE, OffsetDateTime.now(), PageRequest.of(0, 1)).stream().findFirst()
                .map(event -> {
                    event.setStatus(ProviderWebhookStatus.PROCESSING);
                    event.setAttemptCount(event.getAttemptCount() + 1);
                    event.setLastError(null);
                    return repository.save(event).getId();
                }).orElse(null);
    }

    @Transactional(readOnly = true)
    public ProviderWebhookEvent load(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    @Transactional
    public void markProcessed(UUID id) {
        ProviderWebhookEvent event = lock(id);
        if (event.getStatus() != ProviderWebhookStatus.PROCESSING) return;
        event.setStatus(ProviderWebhookStatus.PROCESSED);
        event.setProcessedAt(OffsetDateTime.now());
        event.setLastError(null);
    }

    @Transactional
    public void markFailed(UUID id, Exception failure) {
        ProviderWebhookEvent event = lock(id);
        if (event.getStatus() != ProviderWebhookStatus.PROCESSING) return;
        event.setLastError(safeMessage(failure));
        if (event.getAttemptCount() >= maxAttempts) {
            event.setStatus(ProviderWebhookStatus.DEAD_LETTER);
            return;
        }
        event.setStatus(ProviderWebhookStatus.RETRY);
        long delaySeconds = Math.min(3600L, 15L << Math.min(event.getAttemptCount() - 1, 8));
        event.setNextAttemptAt(OffsetDateTime.now().plusSeconds(delaySeconds));
    }

    @Transactional
    public ProviderWebhookEventResponse retryDeadLetter(UUID id) {
        ProviderWebhookEvent event = lock(id);
        if (event.getStatus() != ProviderWebhookStatus.DEAD_LETTER) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "Only dead-lettered webhook events can be retried manually");
        }
        event.setStatus(ProviderWebhookStatus.RETRY);
        event.setAttemptCount(0);
        event.setNextAttemptAt(OffsetDateTime.now());
        event.setLastError(null);
        return ProviderWebhookEventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public Page<ProviderWebhookEventResponse> list(ProviderWebhookStatus status, Pageable pageable) {
        Page<ProviderWebhookEvent> events = status == null
                ? repository.findAllByOrderByReceivedAtDesc(pageable)
                : repository.findByStatusOrderByReceivedAtAsc(status, pageable);
        return events.map(ProviderWebhookEventResponse::from);
    }

    private ProviderWebhookEvent lock(UUID id) {
        return repository.findByIdForUpdate(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private static String sha256(byte[] payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) return ex.getClass().getSimpleName();
        String sanitized = message.replaceAll("(?i)(sk_live_|sk_test_)[A-Za-z0-9]+", "[redacted]");
        return sanitized.substring(0, Math.min(sanitized.length(), 1000));
    }
}
