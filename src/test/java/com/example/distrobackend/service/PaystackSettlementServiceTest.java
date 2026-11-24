package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.*;
import com.example.distrobackend.Domain.enums.*;
import com.example.distrobackend.dto.CreateSettlementBatchRequest;
import com.example.distrobackend.dto.SettlementBatchResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaystackSettlementServiceTest {
    @Mock PaystackPaymentService paystackPaymentService;
    @Mock PayoutBeneficiaryRepository beneficiaryRepository;
    @Mock SettlementBatchRepository batchRepository;
    @Mock SupplierSettlementRepository settlementRepository;
    @Mock OrganizationRepository organizationRepository;
    @Mock UserRepository userRepository;
    @Mock PaymentDisputeRepository disputeRepository;
    @Mock CheckoutPaymentRepository checkoutPaymentRepository;
    @Mock PaymentRefundRepository paymentRefundRepository;
    @Mock PaystackSettlementDispatchStore dispatchStore;
    @Mock FinancialLedgerService financialLedgerService;
    @InjectMocks PaystackSettlementService service;

    @Test
    void createsBatchOnlyFromPendingSettlementsWithActiveBeneficiary() {
        UUID settlementId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Organization supplier = new Organization();
        supplier.setId(supplierId);
        supplier.setName("Supplier");
        SupplierSettlement settlement = new SupplierSettlement();
        settlement.setId(settlementId);
        settlement.setSupplierOrganization(supplier);
        settlement.setStatus(SupplierSettlementStatus.PENDING);
        settlement.setAmount(new BigDecimal("100.00"));
        settlement.setCurrency("KES");
        CheckoutPayment payment = new CheckoutPayment();
        payment.setId(UUID.randomUUID());
        payment.setProviderReference("TX-123");
        PaymentAllocation allocation = new PaymentAllocation();
        allocation.setPayment(payment);
        settlement.setAllocation(allocation);
        PayoutBeneficiary beneficiary = new PayoutBeneficiary();
        beneficiary.setId(UUID.randomUUID());
        beneficiary.setOrganization(supplier);
        beneficiary.setStatus(PayoutBeneficiaryStatus.ACTIVE);
        when(settlementRepository.findAllById(java.util.List.of(settlementId))).thenReturn(java.util.List.of(settlement));
        when(settlementRepository.findByIdForUpdate(settlementId)).thenReturn(Optional.of(settlement));
        when(checkoutPaymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(disputeRepository.existsUnclearedForPaymentOrReference(payment.getId(), "TX-123"))
                .thenReturn(false);
        when(paymentRefundRepository.existsByPayment_IdAndStatusIn(eq(payment.getId()), anyCollection()))
                .thenReturn(false);
        when(beneficiaryRepository.findByOrganization_IdAndStatusAndCurrency(
                supplierId, PayoutBeneficiaryStatus.ACTIVE, "KES")).thenReturn(Optional.of(beneficiary));
        User admin = new User();
        admin.setId(adminId);
        when(userRepository.getReferenceById(adminId)).thenReturn(admin);
        when(batchRepository.saveAndFlush(any(SettlementBatch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(settlementRepository.save(any(SupplierSettlement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SettlementBatchResponse result = service.createBatch(
                new AuthenticatedUser(adminId, UserRole.SUPER_ADMIN, null, null, null),
                new CreateSettlementBatchRequest(java.util.List.of(settlementId), "weekly settlement"));

        assertThat(result.status()).isEqualTo("PENDING_APPROVAL");
        assertThat(result.totalAmount()).isEqualByComparingTo("100.00");
        assertThat(settlement.getBeneficiary()).isSameAs(beneficiary);
        assertThat(settlement.getPayoutReference()).startsWith("lf_settlement_");
        verify(batchRepository).saveAndFlush(any(SettlementBatch.class));
    }

    @Test
    void approvingBatchRequiresPendingApproval() {
        UUID batchId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        SettlementBatch batch = new SettlementBatch();
        batch.setId(batchId);
        batch.setStatus(SettlementBatchStatus.PENDING_APPROVAL);
        User admin = new User();
        admin.setId(adminId);
        batch.setCreatedBy(admin);
        when(batchRepository.findByIdForUpdate(batchId)).thenReturn(Optional.of(batch));
        when(userRepository.getReferenceById(adminId)).thenReturn(admin);
        when(batchRepository.save(batch)).thenReturn(batch);

        SettlementBatchResponse result = service.approveBatch(
                new AuthenticatedUser(adminId, UserRole.SUPER_ADMIN, null, null, null), batchId);

        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(batch.getApprovedBy()).isSameAs(admin);
        verify(batchRepository).save(batch);
    }

    @Test
    void reassignsFailedSettlementOnlyToActiveMatchingBeneficiary() {
        UUID settlementId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID beneficiaryId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Organization supplier = new Organization();
        supplier.setId(supplierId);
        CheckoutPayment payment = new CheckoutPayment();
        payment.setId(paymentId);
        PaymentAllocation allocation = new PaymentAllocation();
        allocation.setPayment(payment);
        SupplierSettlement settlement = new SupplierSettlement();
        settlement.setId(settlementId);
        settlement.setSupplierOrganization(supplier);
        settlement.setAllocation(allocation);
        settlement.setStatus(SupplierSettlementStatus.FAILED);
        settlement.setCurrency("KES");
        PayoutBeneficiary replacement = new PayoutBeneficiary();
        replacement.setId(beneficiaryId);
        replacement.setOrganization(supplier);
        replacement.setCurrency("KES");
        replacement.setStatus(PayoutBeneficiaryStatus.ACTIVE);
        when(settlementRepository.findById(settlementId)).thenReturn(Optional.of(settlement));
        when(checkoutPaymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(settlementRepository.findByIdForUpdate(settlementId)).thenReturn(Optional.of(settlement));
        when(beneficiaryRepository.findByIdForUpdate(beneficiaryId)).thenReturn(Optional.of(replacement));
        when(settlementRepository.save(settlement)).thenReturn(settlement);

        var response = service.reassignBeneficiary(
                new AuthenticatedUser(adminId, UserRole.SUPER_ADMIN, null, null, null), settlementId,
                new com.example.distrobackend.dto.ReassignSettlementBeneficiaryRequest(beneficiaryId));

        assertThat(response.beneficiaryId()).isEqualTo(beneficiaryId);
        assertThat(settlement.getBeneficiary()).isSameAs(replacement);
    }
}
