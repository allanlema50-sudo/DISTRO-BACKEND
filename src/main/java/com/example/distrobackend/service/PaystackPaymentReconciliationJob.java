package com.example.distrobackend.service;

import com.example.distrobackend.repository.CheckoutPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "paystack.enabled", havingValue = "true")
public class PaystackPaymentReconciliationJob {
    private static final int MAX_BATCH_SIZE = 100;

    private final CheckoutPaymentRepository paymentRepository;
    private final PaystackPaymentService paystackPaymentService;
    private final CentralizedCheckoutService checkoutService;
    private final CheckoutPaymentAttemptStore attemptStore;

    @Scheduled(fixedDelayString = "${paystack.reconciliation-scan-ms:60000}")
    public void reconcileUnresolvedAttempts() {
        var paymentIds = paymentRepository.findPaystackPaymentsDueForVerification(
                OffsetDateTime.now(), PageRequest.of(0, MAX_BATCH_SIZE));
        for (var paymentId : paymentIds) {
            try {
                var payment = paymentRepository.findById(paymentId).orElse(null);
                if (payment == null || payment.getProviderReference() == null) continue;
                var result = paystackPaymentService.verifyTransaction(payment.getProviderReference());
                if ("not_found".equals(result.status())
                        && attemptStore.expireUninitializedPaystackAttempt(
                        paymentId, OffsetDateTime.now().minusMinutes(10))) continue;
                boolean terminal = checkoutService.applyPaystackVerification(
                        paymentId, payment.getProviderReference(), result);
                if (!terminal) {
                    attemptStore.schedulePaystackVerificationRetry(paymentId,
                            "Paystack has not reported a final transaction status");
                }
            } catch (RuntimeException ex) {
                log.warn("Could not verify Paystack checkout payment {}; it remains blocked from retry",
                        paymentId, ex);
                try {
                    attemptStore.schedulePaystackVerificationRetry(paymentId,
                            "Paystack verification failed; see server logs");
                } catch (RuntimeException stateFailure) {
                    log.error("Could not persist Paystack verification retry for payment {}", paymentId,
                            stateFailure);
                }
            }
        }
    }
}
