package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.CheckoutPayment;
import com.example.distrobackend.Domain.enums.CheckoutPaymentStatus;
import com.example.distrobackend.Domain.enums.PaymentProvider;
import com.example.distrobackend.repository.CheckoutPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaystackCheckoutReconciliationJob {
    private final CheckoutPaymentRepository checkoutPaymentRepository;
    private final CheckoutPaymentAttemptStore attemptStore;
    private final PaystackPaymentService paystackPaymentService;
    private final CentralizedCheckoutService centralizedCheckoutService;

    @Value("${paystack.reconciliation.initial-delay-ms:300000}")
    private long initialDelayMs;
    @Value("${paystack.reconciliation.repeat-delay-ms:300000}")
    private long repeatDelayMs;
    @Value("${paystack.reconciliation.batch-size:100}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${paystack.reconciliation.scan-ms:60000}")
    public void reconcileUnknownInitializations() {
        if (!paystackPaymentService.enabled()) return;
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime initiatedBefore = now.minusNanos(initialDelayMs * 1_000_000L);
        OffsetDateTime checkedBefore = now.minusNanos(repeatDelayMs * 1_000_000L);
        List<CheckoutPayment> candidates = checkoutPaymentRepository.findUnknownPaystackAttempts(
                initiatedBefore, checkedBefore, Math.max(1, Math.min(batchSize, 500)));
        for (CheckoutPayment candidate : candidates) {
            try {
                var reference = attemptStore.claimUnknownPaystackReference(candidate.getId(),
                        initiatedBefore, checkedBefore, now);
                if (reference.isEmpty()) continue;
                var verified = paystackPaymentService.verifyTransaction(reference.get());
                if (verified.isEmpty()) {
                    centralizedCheckoutService.markUnknownPaystackAttemptNotFound(reference.get());
                    continue;
                }
                var transaction = verified.get();
                String event = switch (transaction.status().toLowerCase(java.util.Locale.ROOT)) {
                    case "success" -> "charge.success";
                    case "failed", "abandoned" -> "charge.failed";
                    default -> null;
                };
                if (event != null) {
                    centralizedCheckoutService.processPaystackWebhook(new PaystackPaymentService.Webhook(
                            event, transaction.reference(), transaction.status(), transaction.amountMinor(),
                            transaction.currency(), transaction.gatewayResponse(), null));
                }
            } catch (RuntimeException ex) {
                log.warn("Paystack checkout reconciliation failed for payment {}: {}",
                        candidate.getId(), ex.getMessage());
            }
        }
    }
}
