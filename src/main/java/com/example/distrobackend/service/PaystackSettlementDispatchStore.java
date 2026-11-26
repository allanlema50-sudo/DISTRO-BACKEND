package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.SettlementBatch;
import com.example.distrobackend.Domain.entity.SupplierPayoutAttempt;
import com.example.distrobackend.Domain.entity.SupplierSettlement;
import com.example.distrobackend.Domain.enums.SettlementBatchStatus;
import com.example.distrobackend.Domain.enums.SupplierPayoutAttemptStatus;
import com.example.distrobackend.Domain.enums.SupplierSettlementStatus;
import com.example.distrobackend.Domain.enums.RefundStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.SettlementBatchResponse;
import com.example.distrobackend.dto.SupplierSettlementAdminResponse;
import com.example.distrobackend.repository.SettlementBatchRepository;
import com.example.distrobackend.repository.SupplierSettlementRepository;
import com.example.distrobackend.repository.CheckoutPaymentRepository;
import com.example.distrobackend.repository.PaymentDisputeRepository;
import com.example.distrobackend.repository.PaymentRefundRepository;
import com.example.distrobackend.repository.SupplierPayoutAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Commits a payout attempt before any external transfer request is sent. */
@Service
@RequiredArgsConstructor
public class PaystackSettlementDispatchStore {
    private final SettlementBatchRepository batchRepository;
    private final SupplierSettlementRepository settlementRepository;
    private final CheckoutPaymentRepository paymentRepository;
    private final PaymentDisputeRepository disputeRepository;
    private final PaymentRefundRepository refundRepository;
    private final FinancialLedgerService financialLedgerService;
    private final SupplierPayoutAttemptRepository attemptRepository;

    @Transactional
    public List<UUID> beginBatch(UUID batchId, String providerMode) {
        SettlementBatch batch = lockBatch(batchId);
        if (!java.util.Objects.equals(providerMode, batch.getProviderMode())) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "Settlement batch belongs to a different or unknown Paystack mode");
        }
        if (batch.getStatus() != SettlementBatchStatus.APPROVED) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "Only an approved settlement batch can be executed");
        }
        List<SupplierSettlement> observed = settlementRepository
                .findBySettlementBatch_IdOrderByCreatedAtAsc(batchId);
        observed.stream().map(s -> s.getAllocation().getPayment().getId()).distinct().sorted()
                .forEach(paymentId -> paymentRepository.findByIdForUpdate(paymentId)
                        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Checkout payment not found")));
        List<SupplierSettlement> settlements = observed.stream()
                .map(s -> settlementRepository.findByIdForUpdate(s.getId())
                        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Supplier settlement not found")))
                .toList();
        if (settlements.stream().anyMatch(this::hasOpenRefundOrDispute)) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "A settlement in this batch is blocked by an open refund or payment dispute");
        }
        batch.setStatus(SettlementBatchStatus.PROCESSING);
        batchRepository.saveAndFlush(batch);
        return settlements.stream().map(SupplierSettlement::getId).toList();
    }

    @Transactional
    public Attempt prepareAttempt(UUID settlementId, boolean retry, String providerMode) {
        SupplierSettlement observed = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Supplier settlement not found"));
        if (observed.getSettlementBatch() == null) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "A settlement must belong to a batch before transfer execution");
        }
        UUID batchId = observed.getSettlementBatch().getId();
        SettlementBatch batch = lockBatch(batchId);
        UUID paymentId = observed.getAllocation().getPayment().getId();
        com.example.distrobackend.Domain.entity.CheckoutPayment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Checkout payment not found"));
        SupplierSettlement settlement = settlementRepository.findByIdForUpdate(settlementId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Supplier settlement not found"));
        if (!java.util.Objects.equals(providerMode, batch.getProviderMode())) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "Settlement batch belongs to a different or unknown Paystack mode");
        }

        if (hasOpenRefundOrDispute(settlement, payment)) {
            if (!retry && batch.getStatus() == SettlementBatchStatus.PROCESSING
                    && settlement.getStatus() == SupplierSettlementStatus.PENDING) {
                return null;
            }
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "This settlement is blocked by an open refund or payment dispute");
        }

        if (retry) {
            if (settlement.getStatus() != SupplierSettlementStatus.FAILED
                    && settlement.getStatus() != SupplierSettlementStatus.REVERSED) {
                throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                        "Only a failed or reversed settlement can be retried");
            }
            if (attemptRepository.existsBySettlement_IdAndStatusIn(settlementId,
                    Set.of(SupplierPayoutAttemptStatus.SUCCEEDED,
                            SupplierPayoutAttemptStatus.PROCESSING,
                            SupplierPayoutAttemptStatus.OUTCOME_UNKNOWN))) {
                throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                        "A successful or unresolved payout attempt prevents another transfer");
            }
            if (financialLedgerService.hasOpenSupplierPayout(settlementId)) {
                throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                        "An unreversed payout journal prevents another transfer");
            }
        } else if (batch.getStatus() != SettlementBatchStatus.PROCESSING
                || settlement.getStatus() != SupplierSettlementStatus.PENDING) {
            return null;
        }

        if (settlement.getBeneficiary() == null
                || settlement.getBeneficiary().getStatus() != com.example.distrobackend.Domain.enums.PayoutBeneficiaryStatus.ACTIVE) {
            settlement.setStatus(SupplierSettlementStatus.FAILED);
            settlement.setFailureReason("No active payout beneficiary is attached");
            settlementRepository.saveAndFlush(settlement);
            refreshBatch(batch);
            return null;
        }
        if (!java.util.Objects.equals(providerMode, settlement.getBeneficiary().getProviderMode())) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "The payout beneficiary belongs to a different or unknown Paystack mode");
        }

        int attemptNumber = Math.max(settlement.getPayoutAttempts(), attemptRepository
                .findTopBySettlement_IdOrderByAttemptNumberDesc(settlementId)
                .map(SupplierPayoutAttempt::getAttemptNumber).orElse(0)) + 1;
        String reference = "lfp_" + settlementId.toString().replace("-", "") + "_" + attemptNumber;
        OffsetDateTime now = OffsetDateTime.now();
        SupplierPayoutAttempt payoutAttempt = new SupplierPayoutAttempt();
        payoutAttempt.setSettlement(settlement);
        payoutAttempt.setAttemptNumber(attemptNumber);
        payoutAttempt.setProviderReference(reference);
        payoutAttempt.setProviderMode(providerMode);
        payoutAttempt.setStatus(SupplierPayoutAttemptStatus.PROCESSING);
        payoutAttempt.setAmount(settlement.getAmount());
        payoutAttempt.setCurrency(settlement.getCurrency());
        payoutAttempt.setStartedAt(now);
        attemptRepository.saveAndFlush(payoutAttempt);

        // This method commits the attempt identity and PROCESSING state before the caller
        // sends the transfer request. A restart can then verify this exact reference.
        settlement.setPayoutReference(reference);
        settlement.setStatus(SupplierSettlementStatus.PROCESSING);
        settlement.setPayoutAttempts(attemptNumber);
        settlement.setLastAttemptAt(now);
        settlement.setLastStatusCheckAt(null);
        settlement.setFailureReason(null);
        settlementRepository.saveAndFlush(settlement);
        batch.setStatus(SettlementBatchStatus.PROCESSING);
        batchRepository.save(batch);
        return new Attempt(settlement.getId(), batchId, attemptNumber, reference, providerMode,
                settlement.getAmount(), settlement.getCurrency(),
                settlement.getBeneficiary().getProviderRecipientCode());
    }

    @Transactional
    public void complete(Attempt attempt, PaystackPaymentService.Transfer transfer,
                         String definitiveFailure, boolean outcomeUnknown) {
        SettlementBatch batch = lockBatch(attempt.batchId());
        SupplierSettlement settlement = settlementRepository.findByIdForUpdate(attempt.settlementId())
                .orElse(null);
        SupplierPayoutAttempt payoutAttempt = attemptRepository.findByProviderReferenceForUpdate(attempt.reference())
                .orElse(null);
        if (settlement == null || payoutAttempt == null
                || payoutAttempt.getStatus() != SupplierPayoutAttemptStatus.PROCESSING
                && payoutAttempt.getStatus() != SupplierPayoutAttemptStatus.OUTCOME_UNKNOWN) return;

        if (outcomeUnknown) {
            payoutAttempt.setStatus(SupplierPayoutAttemptStatus.OUTCOME_UNKNOWN);
            payoutAttempt.setFailureReason("Paystack transfer outcome is unknown; reconciliation is required");
            settlement.setFailureReason(payoutAttempt.getFailureReason());
        } else if (definitiveFailure != null) {
            payoutAttempt.setStatus(SupplierPayoutAttemptStatus.FAILED);
            payoutAttempt.setFailureReason(definitiveFailure);
            payoutAttempt.setCompletedAt(OffsetDateTime.now());
            if (attempt.reference().equals(settlement.getPayoutReference())
                    && !attemptRepository.existsBySettlement_IdAndStatusIn(settlement.getId(),
                    Set.of(SupplierPayoutAttemptStatus.SUCCEEDED))) {
                settlement.setStatus(SupplierSettlementStatus.FAILED);
                settlement.setFailureReason(definitiveFailure);
            }
        } else if (transfer != null) {
            if (transfer.transferCode() != null) {
                payoutAttempt.setProviderTransferCode(transfer.transferCode());
                if (attempt.reference().equals(settlement.getPayoutReference())) {
                    settlement.setProviderTransferCode(transfer.transferCode());
                }
            }
            if ("success".equalsIgnoreCase(transfer.status())) {
                long expected = toMinor(payoutAttempt.getAmount());
                if (transfer.amountMinor() == null || transfer.currency() == null
                        || transfer.amountMinor() != expected
                        || !payoutAttempt.getCurrency().equalsIgnoreCase(transfer.currency())) {
                    payoutAttempt.setStatus(SupplierPayoutAttemptStatus.OUTCOME_UNKNOWN);
                    payoutAttempt.setFailureReason(
                            "Paystack transfer response amount or currency requires verification");
                    settlement.setFailureReason(payoutAttempt.getFailureReason());
                } else {
                    financialLedgerService.recordSupplierPayout(settlement, attempt.attemptNumber(),
                            attempt.reference());
                    payoutAttempt.setStatus(SupplierPayoutAttemptStatus.SUCCEEDED);
                    payoutAttempt.setCompletedAt(OffsetDateTime.now());
                    payoutAttempt.setFailureReason(null);
                    settlement.setStatus(SupplierSettlementStatus.SETTLED);
                    settlement.setSettledAt(OffsetDateTime.now());
                    settlement.setFailureReason(null);
                }
            } else if ("failed".equalsIgnoreCase(transfer.status())) {
                payoutAttempt.setStatus(SupplierPayoutAttemptStatus.FAILED);
                payoutAttempt.setFailureReason("Paystack reported a failed transfer");
                payoutAttempt.setCompletedAt(OffsetDateTime.now());
                if (attempt.reference().equals(settlement.getPayoutReference())
                        && !attemptRepository.existsBySettlement_IdAndStatusIn(settlement.getId(),
                        Set.of(SupplierPayoutAttemptStatus.SUCCEEDED))) {
                    settlement.setStatus(SupplierSettlementStatus.FAILED);
                    settlement.setFailureReason(payoutAttempt.getFailureReason());
                }
            } else {
                settlement.setFailureReason("Waiting for Paystack transfer status: " + transfer.status());
            }
        }
        attemptRepository.save(payoutAttempt);
        settlementRepository.saveAndFlush(settlement);
        refreshBatch(batch);
    }

    @Transactional
    public void processPaystackWebhook(PaystackPaymentService.Webhook webhook) {
        if (webhook == null || !Set.of("transfer.success", "transfer.failed", "transfer.reversed")
                .contains(webhook.event()) || webhook.reference() == null || webhook.reference().isBlank()) return;
        SupplierPayoutAttempt observedAttempt = attemptRepository.findByProviderReference(webhook.reference())
                .orElse(null);
        if (observedAttempt == null) {
            if (webhook.reference() != null && (webhook.reference().startsWith("lfp_")
                    || webhook.reference().startsWith("lf_settlement_"))) {
                throw new IllegalStateException("Payout attempt is not committed yet; retry this webhook later");
            }
            return;
        }
        UUID settlementId = observedAttempt.getSettlement().getId();
        SupplierSettlement observedSettlement = settlementRepository.findById(settlementId).orElse(null);
        if (observedSettlement == null || observedSettlement.getSettlementBatch() == null) return;
        SettlementBatch batch = lockBatch(observedSettlement.getSettlementBatch().getId());
        SupplierSettlement settlement = settlementRepository.findByIdForUpdate(settlementId).orElse(null);
        SupplierPayoutAttempt attempt = attemptRepository
                .findByProviderReferenceForUpdate(webhook.reference()).orElse(null);
        if (settlement == null || attempt == null || settlement.getProvider() == null
                || !"PAYSTACK".equals(settlement.getProvider().name())) return;

        boolean currentAttempt = webhook.reference().equals(settlement.getPayoutReference());
        if (webhook.transferCode() != null) {
            attempt.setProviderTransferCode(webhook.transferCode());
            if (currentAttempt) settlement.setProviderTransferCode(webhook.transferCode());
        }
        long expected = toMinor(attempt.getAmount());
        if (webhook.amountMinor() == null || webhook.currency() == null
                || webhook.amountMinor() != expected
                || !attempt.getCurrency().equalsIgnoreCase(webhook.currency())) {
            if (attempt.getStatus() != SupplierPayoutAttemptStatus.SUCCEEDED
                    && attempt.getStatus() != SupplierPayoutAttemptStatus.REVERSED) {
                attempt.setStatus(SupplierPayoutAttemptStatus.OUTCOME_UNKNOWN);
                attempt.setFailureReason("Paystack transfer evidence is missing or mismatched; verify the transfer");
                if (currentAttempt) {
                    settlement.setStatus(SupplierSettlementStatus.PROCESSING);
                    settlement.setFailureReason(attempt.getFailureReason());
                }
                attemptRepository.save(attempt);
                settlementRepository.save(settlement);
                refreshBatch(batch);
            }
            return;
        }

        switch (webhook.event()) {
            case "transfer.success" -> {
                if (attempt.getStatus() == SupplierPayoutAttemptStatus.REVERSED) return;
                financialLedgerService.recordSupplierPayout(settlement, attempt.getAttemptNumber(),
                        attempt.getProviderReference());
                attempt.setStatus(SupplierPayoutAttemptStatus.SUCCEEDED);
                attempt.setCompletedAt(OffsetDateTime.now());
                attempt.setFailureReason(null);
                settlement.setStatus(SupplierSettlementStatus.SETTLED);
                settlement.setSettledAt(OffsetDateTime.now());
                settlement.setFailureReason(null);
            }
            case "transfer.failed" -> {
                if (attempt.getStatus() == SupplierPayoutAttemptStatus.SUCCEEDED
                        || attempt.getStatus() == SupplierPayoutAttemptStatus.REVERSED) return;
                attempt.setStatus(SupplierPayoutAttemptStatus.FAILED);
                attempt.setCompletedAt(OffsetDateTime.now());
                attempt.setFailureReason(webhook.gatewayResponse() == null
                        ? "Paystack reported a failed transfer" : webhook.gatewayResponse());
                if (currentAttempt && !attemptRepository.existsBySettlement_IdAndStatusIn(settlementId,
                        Set.of(SupplierPayoutAttemptStatus.SUCCEEDED))) {
                    settlement.setStatus(SupplierSettlementStatus.FAILED);
                    settlement.setFailureReason(attempt.getFailureReason());
                }
            }
            case "transfer.reversed" -> {
                if (attempt.getStatus() == SupplierPayoutAttemptStatus.REVERSED) return;
                financialLedgerService.recordSupplierPayout(settlement, attempt.getAttemptNumber(),
                        attempt.getProviderReference());
                financialLedgerService.recordSupplierPayoutReversal(settlement, attempt.getAttemptNumber(),
                        attempt.getProviderReference());
                attempt.setStatus(SupplierPayoutAttemptStatus.REVERSED);
                attempt.setCompletedAt(OffsetDateTime.now());
                attempt.setFailureReason("Paystack reversed the transfer");
                if (currentAttempt) {
                    boolean otherSuccessfulAttempt = attemptRepository.existsBySettlement_IdAndStatusIn(
                            settlementId, Set.of(SupplierPayoutAttemptStatus.SUCCEEDED));
                    settlement.setStatus(otherSuccessfulAttempt
                            ? SupplierSettlementStatus.SETTLED : SupplierSettlementStatus.REVERSED);
                    settlement.setFailureReason(otherSuccessfulAttempt
                            ? "Another payout attempt remains successful; manual reconciliation is required"
                            : attempt.getFailureReason());
                }
            }
            default -> { return; }
        }
        attemptRepository.save(attempt);
        settlementRepository.save(settlement);
        refreshBatch(batch);
    }

    @Transactional
    public Optional<AttemptVerification> claimAttemptVerification(UUID attemptId, OffsetDateTime startedBefore,
                                                                  OffsetDateTime checkedBefore,
                                                                  OffsetDateTime checkedAt) {
        SupplierPayoutAttempt observed = attemptRepository.findById(attemptId).orElse(null);
        if (observed == null) return Optional.empty();
        SupplierSettlement observedSettlement = observed.getSettlement();
        SettlementBatch batch = observedSettlement.getSettlementBatch();
        if (batch == null) return Optional.empty();
        lockBatch(batch.getId());
        settlementRepository.findByIdForUpdate(observedSettlement.getId()).orElse(null);
        SupplierPayoutAttempt attempt = attemptRepository.findByProviderReferenceForUpdate(
                observed.getProviderReference()).orElse(null);
        if (attempt == null || (attempt.getStatus() != SupplierPayoutAttemptStatus.PROCESSING
                && attempt.getStatus() != SupplierPayoutAttemptStatus.OUTCOME_UNKNOWN)
                || attempt.getStartedAt().isAfter(startedBefore)
                || attempt.getLastStatusCheckAt() != null && attempt.getLastStatusCheckAt().isAfter(checkedBefore)) {
            return Optional.empty();
        }
        attempt.setLastStatusCheckAt(checkedAt);
        attemptRepository.saveAndFlush(attempt);
        return Optional.of(new AttemptVerification(attempt.getProviderReference(), attempt.getSettlement().getId(),
                batch.getId()));
    }

    @Transactional
    public void markPaystackTransferNotFound(String reference) {
        SupplierPayoutAttempt observedAttempt = attemptRepository.findByProviderReference(reference).orElse(null);
        if (observedAttempt == null) return;
        UUID settlementId = observedAttempt.getSettlement().getId();
        SupplierSettlement observedSettlement = settlementRepository.findById(settlementId).orElse(null);
        if (observedSettlement == null || observedSettlement.getSettlementBatch() == null) return;
        SettlementBatch batch = lockBatch(observedSettlement.getSettlementBatch().getId());
        SupplierSettlement settlement = settlementRepository.findByIdForUpdate(settlementId).orElse(null);
        SupplierPayoutAttempt attempt = attemptRepository.findByProviderReferenceForUpdate(reference).orElse(null);
        if (settlement == null || attempt == null
                || (attempt.getStatus() != SupplierPayoutAttemptStatus.PROCESSING
                && attempt.getStatus() != SupplierPayoutAttemptStatus.OUTCOME_UNKNOWN)) return;
        attempt.setStatus(SupplierPayoutAttemptStatus.FAILED);
        attempt.setFailureReason("Paystack has no transfer for the stored payout reference");
        attempt.setCompletedAt(OffsetDateTime.now());
        if (reference.equals(settlement.getPayoutReference())
                && !attemptRepository.existsBySettlement_IdAndStatusIn(settlementId,
                Set.of(SupplierPayoutAttemptStatus.SUCCEEDED))) {
            settlement.setStatus(SupplierSettlementStatus.FAILED);
            settlement.setFailureReason(attempt.getFailureReason());
        }
        attemptRepository.save(attempt);
        settlementRepository.save(settlement);
        refreshBatch(batch);
    }

    @Transactional
    public SettlementBatchResponse finishBatch(UUID batchId) {
        SettlementBatch batch = lockBatch(batchId);
        refreshBatch(batch);
        batch.setExecutedAt(OffsetDateTime.now());
        return SettlementBatchResponse.from(batchRepository.saveAndFlush(batch));
    }

    @Transactional(readOnly = true)
    public SupplierSettlementAdminResponse getSettlement(UUID settlementId) {
        return settlementRepository.findById(settlementId).map(SupplierSettlementAdminResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Supplier settlement not found"));
    }

    @Transactional(readOnly = true)
    public SettlementBatchResponse getBatch(UUID batchId) {
        return batchRepository.findById(batchId).map(SettlementBatchResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Settlement batch not found"));
    }

    private SettlementBatch lockBatch(UUID batchId) {
        return batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Settlement batch not found"));
    }

    private void refreshBatch(SettlementBatch batch) {
        List<SupplierSettlement> members = settlementRepository
                .findBySettlementBatch_IdOrderByCreatedAtAsc(batch.getId());
        if (members.isEmpty()) return;
        boolean activeAttemptExists = members.stream().anyMatch(s -> attemptRepository
                .existsBySettlement_IdAndStatusIn(s.getId(), Set.of(SupplierPayoutAttemptStatus.PROCESSING,
                        SupplierPayoutAttemptStatus.OUTCOME_UNKNOWN)));
        boolean allSettled = members.stream().allMatch(s -> s.getStatus() == SupplierSettlementStatus.SETTLED)
                && !activeAttemptExists;
        boolean anyActive = activeAttemptExists || members.stream().anyMatch(s ->
                s.getStatus() == SupplierSettlementStatus.PENDING
                        || s.getStatus() == SupplierSettlementStatus.PROCESSING);
        boolean anyFailed = members.stream().anyMatch(s -> s.getStatus() == SupplierSettlementStatus.FAILED
                || s.getStatus() == SupplierSettlementStatus.REVERSED);
        batch.setStatus(allSettled ? SettlementBatchStatus.COMPLETED
                : anyActive ? SettlementBatchStatus.PROCESSING
                : anyFailed ? SettlementBatchStatus.PARTIALLY_FAILED : SettlementBatchStatus.FAILED);
        batchRepository.save(batch);
    }

    private boolean hasOpenRefundOrDispute(SupplierSettlement settlement) {
        com.example.distrobackend.Domain.entity.CheckoutPayment payment = paymentRepository
                .findByIdForUpdate(settlement.getAllocation().getPayment().getId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Checkout payment not found"));
        return hasOpenRefundOrDispute(settlement, payment);
    }

    private boolean hasOpenRefundOrDispute(SupplierSettlement settlement,
                                           com.example.distrobackend.Domain.entity.CheckoutPayment payment) {
        boolean openDispute = disputeRepository.existsUnclearedForPaymentOrReference(
                payment.getId(), payment.getProviderReference());
        boolean openRefund = refundRepository.existsByPayment_IdAndStatusIn(payment.getId(),
                Set.of(RefundStatus.REQUESTED, RefundStatus.PENDING, RefundStatus.PROCESSING));
        return openDispute || openRefund;
    }

    private static long toMinor(BigDecimal amount) {
        try {
            return amount.movePointRight(2).longValueExact();
        } catch (ArithmeticException ex) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Settlement amount must have at most two decimals");
        }
    }

    public record Attempt(UUID settlementId, UUID batchId, int attemptNumber, String reference, String providerMode,
                          BigDecimal amount,
                          String currency, String recipientCode) {}

    public record AttemptVerification(String reference, UUID settlementId, UUID batchId) {}
}
