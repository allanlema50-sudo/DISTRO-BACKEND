package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.SettlementBatch;
import com.example.distrobackend.Domain.entity.SupplierSettlement;
import com.example.distrobackend.Domain.enums.SettlementBatchStatus;
import com.example.distrobackend.Domain.enums.SupplierSettlementStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.SettlementBatchResponse;
import com.example.distrobackend.dto.SupplierSettlementAdminResponse;
import com.example.distrobackend.repository.SettlementBatchRepository;
import com.example.distrobackend.repository.SupplierSettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Commits a payout attempt before any external transfer request is sent. */
@Service
@RequiredArgsConstructor
public class PaystackSettlementDispatchStore {
    private final SettlementBatchRepository batchRepository;
    private final SupplierSettlementRepository settlementRepository;

    @Transactional
    public List<UUID> beginBatch(UUID batchId) {
        SettlementBatch batch = lockBatch(batchId);
        if (batch.getStatus() != SettlementBatchStatus.APPROVED) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "Only an approved settlement batch can be executed");
        }
        batch.setStatus(SettlementBatchStatus.PROCESSING);
        batchRepository.saveAndFlush(batch);
        return settlementRepository.findBySettlementBatch_IdOrderByCreatedAtAsc(batchId).stream()
                .map(SupplierSettlement::getId).toList();
    }

    @Transactional
    public Attempt prepareAttempt(UUID settlementId, boolean retry) {
        SupplierSettlement observed = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Supplier settlement not found"));
        if (observed.getSettlementBatch() == null) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "A settlement must belong to a batch before transfer execution");
        }
        UUID batchId = observed.getSettlementBatch().getId();
        SettlementBatch batch = lockBatch(batchId);
        SupplierSettlement settlement = settlementRepository.findByIdForUpdate(settlementId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Supplier settlement not found"));

        if (retry) {
            if (settlement.getStatus() != SupplierSettlementStatus.FAILED
                    && settlement.getStatus() != SupplierSettlementStatus.REVERSED) {
                throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                        "Only a failed or reversed settlement can be retried");
            }
            settlement.setPayoutReference("lf_settlement_" + UUID.randomUUID().toString().replace("-", ""));
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

        // Commit PROCESSING and the attempt timestamp before contacting Paystack.
        // A crash from this point onward is discovered by the transfer verifier.
        settlement.setStatus(SupplierSettlementStatus.PROCESSING);
        settlement.setPayoutAttempts(settlement.getPayoutAttempts() + 1);
        settlement.setLastAttemptAt(OffsetDateTime.now());
        settlement.setLastStatusCheckAt(null);
        settlement.setFailureReason(null);
        settlementRepository.saveAndFlush(settlement);
        batch.setStatus(SettlementBatchStatus.PROCESSING);
        batchRepository.save(batch);
        return new Attempt(settlement.getId(), batchId, settlement.getPayoutReference(),
                settlement.getAmount(), settlement.getCurrency(),
                settlement.getBeneficiary().getProviderRecipientCode());
    }

    @Transactional
    public void complete(Attempt attempt, PaystackPaymentService.Transfer transfer,
                         String definitiveFailure, boolean outcomeUnknown) {
        SettlementBatch batch = lockBatch(attempt.batchId());
        SupplierSettlement settlement = settlementRepository.findByIdForUpdate(attempt.settlementId())
                .orElse(null);
        if (settlement == null || settlement.getStatus() != SupplierSettlementStatus.PROCESSING
                || !attempt.reference().equals(settlement.getPayoutReference())) return;

        if (outcomeUnknown) {
            settlement.setFailureReason("Paystack transfer outcome is unknown; reconciliation is required");
        } else if (definitiveFailure != null) {
            settlement.setStatus(SupplierSettlementStatus.FAILED);
            settlement.setFailureReason(definitiveFailure);
        } else if (transfer != null) {
            settlement.setProviderTransferCode(transfer.transferCode());
            if ("success".equalsIgnoreCase(transfer.status())) {
                long expected = toMinor(settlement.getAmount());
                if (transfer.amountMinor() == null || transfer.currency() == null
                        || transfer.amountMinor() != expected
                        || !settlement.getCurrency().equalsIgnoreCase(transfer.currency())) {
                    settlement.setFailureReason("Paystack transfer response amount or currency requires verification");
                } else {
                    settlement.setStatus(SupplierSettlementStatus.SETTLED);
                    settlement.setSettledAt(OffsetDateTime.now());
                    settlement.setFailureReason(null);
                }
            } else if ("failed".equalsIgnoreCase(transfer.status())) {
                settlement.setStatus(SupplierSettlementStatus.FAILED);
                settlement.setFailureReason("Paystack reported a failed transfer");
            } else {
                settlement.setFailureReason("Waiting for Paystack transfer status: " + transfer.status());
            }
        }
        settlementRepository.saveAndFlush(settlement);
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
        boolean allSettled = members.stream().allMatch(s -> s.getStatus() == SupplierSettlementStatus.SETTLED);
        boolean anyActive = members.stream().anyMatch(s -> s.getStatus() == SupplierSettlementStatus.PENDING
                || s.getStatus() == SupplierSettlementStatus.PROCESSING);
        boolean anyFailed = members.stream().anyMatch(s -> s.getStatus() == SupplierSettlementStatus.FAILED
                || s.getStatus() == SupplierSettlementStatus.REVERSED);
        batch.setStatus(allSettled ? SettlementBatchStatus.COMPLETED
                : anyActive ? SettlementBatchStatus.PROCESSING
                : anyFailed ? SettlementBatchStatus.PARTIALLY_FAILED : SettlementBatchStatus.FAILED);
        batchRepository.save(batch);
    }

    private static long toMinor(BigDecimal amount) {
        try {
            return amount.movePointRight(2).longValueExact();
        } catch (ArithmeticException ex) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Settlement amount must have at most two decimals");
        }
    }

    public record Attempt(UUID settlementId, UUID batchId, String reference, BigDecimal amount,
                          String currency, String recipientCode) {}
}
