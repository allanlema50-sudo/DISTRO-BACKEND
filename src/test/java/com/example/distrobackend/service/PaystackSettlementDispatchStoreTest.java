package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.SettlementBatch;
import com.example.distrobackend.Domain.entity.SupplierPayoutAttempt;
import com.example.distrobackend.Domain.entity.SupplierSettlement;
import com.example.distrobackend.Domain.enums.SettlementBatchStatus;
import com.example.distrobackend.Domain.enums.SupplierSettlementStatus;
import com.example.distrobackend.Domain.enums.SupplierPayoutAttemptStatus;
import com.example.distrobackend.repository.CheckoutPaymentRepository;
import com.example.distrobackend.repository.PaymentDisputeRepository;
import com.example.distrobackend.repository.PaymentRefundRepository;
import com.example.distrobackend.repository.SettlementBatchRepository;
import com.example.distrobackend.repository.SupplierSettlementRepository;
import com.example.distrobackend.repository.SupplierPayoutAttemptRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaystackSettlementDispatchStoreTest {
    @Mock SettlementBatchRepository batchRepository;
    @Mock SupplierSettlementRepository settlementRepository;
    @Mock CheckoutPaymentRepository paymentRepository;
    @Mock PaymentDisputeRepository disputeRepository;
    @Mock PaymentRefundRepository refundRepository;
    @Mock FinancialLedgerService financialLedgerService;
    @Mock SupplierPayoutAttemptRepository attemptRepository;
    @InjectMocks PaystackSettlementDispatchStore dispatchStore;

    @Test
    void successfulTransferPostsLedgerWhileTheSettlementIsLocked() {
        UUID batchId = UUID.randomUUID();
        UUID settlementId = UUID.randomUUID();
        SettlementBatch batch = new SettlementBatch();
        batch.setId(batchId);
        batch.setStatus(SettlementBatchStatus.PROCESSING);
        SupplierSettlement settlement = new SupplierSettlement();
        settlement.setId(settlementId);
        settlement.setSettlementBatch(batch);
        settlement.setStatus(SupplierSettlementStatus.PROCESSING);
        settlement.setAmount(new BigDecimal("100.00"));
        settlement.setCurrency("KES");
        settlement.setPayoutReference("lf_settlement_transfer_1");
        PaystackSettlementDispatchStore.Attempt attempt = new PaystackSettlementDispatchStore.Attempt(
                settlementId, batchId, 1, settlement.getPayoutReference(), settlement.getAmount(), "KES", "RCP_1");
        SupplierPayoutAttempt payoutAttempt = new SupplierPayoutAttempt();
        payoutAttempt.setSettlement(settlement);
        payoutAttempt.setAttemptNumber(1);
        payoutAttempt.setProviderReference(settlement.getPayoutReference());
        payoutAttempt.setStatus(SupplierPayoutAttemptStatus.PROCESSING);
        payoutAttempt.setAmount(settlement.getAmount());
        payoutAttempt.setCurrency("KES");
        PaystackPaymentService.Transfer transfer = new PaystackPaymentService.Transfer(
                settlement.getPayoutReference(), "TRF_1", "success", 10000L, "KES");
        when(batchRepository.findByIdForUpdate(batchId)).thenReturn(Optional.of(batch));
        when(settlementRepository.findByIdForUpdate(settlementId)).thenReturn(Optional.of(settlement));
        when(attemptRepository.findByProviderReferenceForUpdate(settlement.getPayoutReference()))
                .thenReturn(Optional.of(payoutAttempt));
        when(settlementRepository.findBySettlementBatch_IdOrderByCreatedAtAsc(batchId))
                .thenReturn(List.of(settlement));

        dispatchStore.complete(attempt, transfer, null, false);

        verify(financialLedgerService).recordSupplierPayout(settlement, 1, settlement.getPayoutReference());
        verify(settlementRepository).findByIdForUpdate(settlementId);
        assertThat(settlement.getStatus()).isEqualTo(SupplierSettlementStatus.SETTLED);
        assertThat(batch.getStatus()).isEqualTo(SettlementBatchStatus.COMPLETED);
    }

    @Test
    void unmatchedLogiflowTransferWebhookRemainsRetryable() {
        String reference = "lfp_" + UUID.randomUUID().toString().replace("-", "") + "_1";
        when(attemptRepository.findByProviderReference(reference)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dispatchStore.processPaystackWebhook(new PaystackPaymentService.Webhook(
                "transfer.success", reference, "success", 10000L, "KES", null, "TRF_1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not committed yet");
    }

    @Test
    void oldAttemptFailureDoesNotOverwriteCurrentSettlementTransferCode() {
        UUID batchId = UUID.randomUUID();
        UUID settlementId = UUID.randomUUID();
        SettlementBatch batch = new SettlementBatch();
        batch.setId(batchId);
        SupplierSettlement settlement = new SupplierSettlement();
        settlement.setId(settlementId);
        settlement.setSettlementBatch(batch);
        settlement.setProvider(com.example.distrobackend.Domain.enums.PaymentProvider.PAYSTACK);
        settlement.setStatus(SupplierSettlementStatus.PROCESSING);
        settlement.setPayoutReference("lfp_current_attempt");
        settlement.setProviderTransferCode("current-transfer-code");
        settlement.setAmount(new BigDecimal("10.00"));
        settlement.setCurrency("KES");

        SupplierPayoutAttempt oldAttempt = new SupplierPayoutAttempt();
        oldAttempt.setSettlement(settlement);
        oldAttempt.setAttemptNumber(1);
        oldAttempt.setProviderReference("lfp_old_attempt");
        oldAttempt.setStatus(SupplierPayoutAttemptStatus.PROCESSING);
        oldAttempt.setAmount(new BigDecimal("10.00"));
        oldAttempt.setCurrency("KES");

        when(attemptRepository.findByProviderReference("lfp_old_attempt")).thenReturn(Optional.of(oldAttempt));
        when(settlementRepository.findById(settlementId)).thenReturn(Optional.of(settlement));
        when(batchRepository.findByIdForUpdate(batchId)).thenReturn(Optional.of(batch));
        when(settlementRepository.findByIdForUpdate(settlementId)).thenReturn(Optional.of(settlement));
        when(attemptRepository.findByProviderReferenceForUpdate("lfp_old_attempt"))
                .thenReturn(Optional.of(oldAttempt));
        when(settlementRepository.findBySettlementBatch_IdOrderByCreatedAtAsc(batchId))
                .thenReturn(List.of(settlement));

        dispatchStore.processPaystackWebhook(new PaystackPaymentService.Webhook(
                "transfer.failed", "lfp_old_attempt", "failed", 1000L, "KES", "declined", "old-transfer-code"));

        assertThat(oldAttempt.getProviderTransferCode()).isEqualTo("old-transfer-code");
        assertThat(settlement.getProviderTransferCode()).isEqualTo("current-transfer-code");
        assertThat(settlement.getStatus()).isEqualTo(SupplierSettlementStatus.PROCESSING);
    }

    @Test
    void batchRemainsProcessingWhileLaterMembersAreStillPending() {
        UUID batchId = UUID.randomUUID();
        UUID settlementId = UUID.randomUUID();
        SettlementBatch batch = new SettlementBatch();
        batch.setId(batchId);
        batch.setStatus(SettlementBatchStatus.PROCESSING);
        SupplierSettlement failedSettlement = new SupplierSettlement();
        failedSettlement.setId(settlementId);
        failedSettlement.setSettlementBatch(batch);
        failedSettlement.setStatus(SupplierSettlementStatus.PROCESSING);
        failedSettlement.setPayoutReference("lfp_attempt_1");

        SupplierSettlement pendingSettlement = new SupplierSettlement();
        pendingSettlement.setId(UUID.randomUUID());
        pendingSettlement.setSettlementBatch(batch);
        pendingSettlement.setStatus(SupplierSettlementStatus.PENDING);

        SupplierPayoutAttempt attempt = new SupplierPayoutAttempt();
        attempt.setSettlement(failedSettlement);
        attempt.setAttemptNumber(1);
        attempt.setProviderReference("lfp_attempt_1");
        attempt.setStatus(SupplierPayoutAttemptStatus.PROCESSING);

        when(batchRepository.findByIdForUpdate(batchId)).thenReturn(Optional.of(batch));
        when(settlementRepository.findByIdForUpdate(settlementId)).thenReturn(Optional.of(failedSettlement));
        when(attemptRepository.findByProviderReferenceForUpdate("lfp_attempt_1"))
                .thenReturn(Optional.of(attempt));
        when(settlementRepository.findBySettlementBatch_IdOrderByCreatedAtAsc(batchId))
                .thenReturn(List.of(failedSettlement, pendingSettlement));

        dispatchStore.complete(new PaystackSettlementDispatchStore.Attempt(settlementId, batchId, 1,
                        "lfp_attempt_1", BigDecimal.ONE, "KES", "RCP_1"), null, "rejected", false);

        assertThat(failedSettlement.getStatus()).isEqualTo(SupplierSettlementStatus.FAILED);
        assertThat(batch.getStatus()).isEqualTo(SettlementBatchStatus.PROCESSING);
    }
}
