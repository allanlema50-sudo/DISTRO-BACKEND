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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaystackSettlementServiceTest {
    @Mock PaystackPaymentService paystackPaymentService;
    @Mock PayoutBeneficiaryRepository beneficiaryRepository;
    @Mock SettlementBatchRepository batchRepository;
    @Mock SupplierSettlementRepository settlementRepository;
    @Mock OrganizationRepository organizationRepository;
    @Mock UserRepository userRepository;
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
        PayoutBeneficiary beneficiary = new PayoutBeneficiary();
        beneficiary.setId(UUID.randomUUID());
        beneficiary.setOrganization(supplier);
        beneficiary.setStatus(PayoutBeneficiaryStatus.ACTIVE);
        when(settlementRepository.findByIdForUpdate(settlementId)).thenReturn(Optional.of(settlement));
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
}
