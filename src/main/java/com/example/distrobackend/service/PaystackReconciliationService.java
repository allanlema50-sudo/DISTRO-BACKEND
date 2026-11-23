package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.CheckoutPayment;
import com.example.distrobackend.Domain.entity.SupplierSettlement;
import com.example.distrobackend.repository.CheckoutPaymentRepository;
import com.example.distrobackend.repository.SupplierSettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaystackReconciliationService {
    private final CheckoutPaymentRepository paymentRepository;
    private final SupplierSettlementRepository settlementRepository;
    private final PaystackPaymentService paystack;
    private final CentralizedCheckoutService checkoutService;
    private final PaystackSettlementService settlementService;

    @Transactional
    public List<PaymentCandidate> claimPayments(int limit) {
        OffsetDateTime now = OffsetDateTime.now();
        List<CheckoutPayment> due = paymentRepository.lockDuePaystackReconciliation(now, PageRequest.of(0, limit));
        return due.stream().map(payment -> {
            payment.setReconciliationAttempts(payment.getReconciliationAttempts() + 1);
            payment.setNextReconciliationAt(now.plusSeconds(backoff(payment.getReconciliationAttempts())));
            return new PaymentCandidate(payment.getId(), payment.getProviderReference());
        }).toList();
    }

    @Transactional
    public List<TransferCandidate> claimTransfers(int limit) {
        OffsetDateTime now = OffsetDateTime.now();
        List<SupplierSettlement> due = settlementRepository
                .lockDuePaystackReconciliation(now, PageRequest.of(0, limit));
        return due.stream().map(settlement -> {
            settlement.setReconciliationAttempts(settlement.getReconciliationAttempts() + 1);
            settlement.setNextReconciliationAt(now.plusSeconds(backoff(settlement.getReconciliationAttempts())));
            return new TransferCandidate(settlement.getId(), settlement.getPayoutReference());
        }).toList();
    }

    public void reconcilePayment(PaymentCandidate candidate) {
        if (candidate.reference() == null || candidate.reference().isBlank()) return;
        try {
            PaystackPaymentService.Verification result = paystack.verifyTransaction(candidate.reference());
            if (!candidate.reference().equals(result.reference())) {
                log.warn("Paystack returned a mismatched transaction reference for payment {}", candidate.id());
                return;
            }
            String event = switch (result.status().toLowerCase(java.util.Locale.ROOT)) {
                case "success" -> "charge.success";
                case "failed", "abandoned", "timeout" -> "charge.failed";
                default -> null;
            };
            if (event == null) return;
            checkoutService.processPaystackWebhook(new PaystackPaymentService.Webhook(event,
                    result.reference(), result.status(), result.amountMinor(), result.currency(),
                    result.gatewayResponse(), null));
        } catch (Exception ex) {
            log.warn("Paystack transaction reconciliation failed for payment {}", candidate.id(), ex);
        }
    }

    public void reconcileTransfer(TransferCandidate candidate) {
        if (candidate.reference() == null || candidate.reference().isBlank()) return;
        try {
            PaystackPaymentService.Verification result = paystack.verifyTransfer(candidate.reference());
            if (!candidate.reference().equals(result.reference())) {
                log.warn("Paystack returned a mismatched transfer reference for settlement {}", candidate.id());
                return;
            }
            String event = switch (result.status().toLowerCase(java.util.Locale.ROOT)) {
                case "success" -> "transfer.success";
                case "failed" -> "transfer.failed";
                case "reversed" -> "transfer.reversed";
                default -> null;
            };
            if (event == null) return;
            settlementService.processPaystackWebhook(new PaystackPaymentService.Webhook(event,
                    result.reference(), result.status(), result.amountMinor(), result.currency(),
                    result.gatewayResponse(), result.transferCode()));
        } catch (Exception ex) {
            log.warn("Paystack transfer reconciliation failed for settlement {}", candidate.id(), ex);
        }
    }

    private static long backoff(int attempts) {
        return Math.min(21_600L, 60L << Math.min(Math.max(attempts - 1, 0), 8));
    }

    public record PaymentCandidate(UUID id, String reference) {}
    public record TransferCandidate(UUID id, String reference) {}
}
