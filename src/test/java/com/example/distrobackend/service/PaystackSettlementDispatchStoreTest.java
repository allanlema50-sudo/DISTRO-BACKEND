package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.SettlementBatch;
import com.example.distrobackend.Domain.entity.SupplierSettlement;
import com.example.distrobackend.Domain.enums.SettlementBatchStatus;
import com.example.distrobackend.Domain.enums.SupplierSettlementStatus;
import com.example.distrobackend.repository.CheckoutPaymentRepository;
import com.example.distrobackend.repository.PaymentDisputeRepository;
import com.example.distrobackend.repository.PaymentRefundRepository;
import com.example.distrobackend.repository.SettlementBatchRepository;
import com.example.distrobackend.repository.SupplierSettlementRepository;
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
                settlementId, batchId, settlement.getPayoutReference(), settlement.getAmount(), "KES", "RCP_1");
        PaystackPaymentService.Transfer transfer = new PaystackPaymentService.Transfer(
                settlement.getPayoutReference(), "TRF_1", "success", 10000L, "KES");
        when(batchRepository.findByIdForUpdate(batchId)).thenReturn(Optional.of(batch));
        when(settlementRepository.findByIdForUpdate(settlementId)).thenReturn(Optional.of(settlement));
        when(settlementRepository.findBySettlementBatch_IdOrderByCreatedAtAsc(batchId))
                .thenReturn(List.of(settlement));

        dispatchStore.complete(attempt, transfer, null, false);

        verify(financialLedgerService).recordSupplierPayout(settlement);
        verify(settlementRepository).findByIdForUpdate(settlementId);
        assertThat(settlement.getStatus()).isEqualTo(SupplierSettlementStatus.SETTLED);
        assertThat(batch.getStatus()).isEqualTo(SettlementBatchStatus.COMPLETED);
    }
}
