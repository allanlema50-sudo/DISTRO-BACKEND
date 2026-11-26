package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.*;
import com.example.distrobackend.Domain.enums.*;
import com.example.distrobackend.dto.CreateSettlementBatchRequest;
import com.example.distrobackend.dto.CreatePayoutBeneficiaryRequest;
import com.example.distrobackend.dto.SettlementBatchResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
    @Mock PurchaseOrderShipmentRepository purchaseOrderShipmentRepository;
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
        PurchaseOrder purchaseOrder = new PurchaseOrder();
        purchaseOrder.setId(UUID.randomUUID());
        PaymentAllocation allocation = new PaymentAllocation();
        allocation.setPayment(payment);
        allocation.setPurchaseOrder(purchaseOrder);
        settlement.setAllocation(allocation);
        PurchaseOrderShipment shipment = new PurchaseOrderShipment();
        shipment.setStatus(PurchaseOrderShipmentStatus.RECEIVED);
        shipment.setReceivedAt(OffsetDateTime.now().minusDays(8));
        when(purchaseOrderShipmentRepository.findByPurchaseOrderIdForUpdate(purchaseOrder.getId()))
                .thenReturn(Optional.of(shipment));
        PayoutBeneficiary beneficiary = new PayoutBeneficiary();
        beneficiary.setId(UUID.randomUUID());
        beneficiary.setOrganization(supplier);
        beneficiary.setStatus(PayoutBeneficiaryStatus.ACTIVE);
        beneficiary.setProviderMode("TEST");
        when(paystackPaymentService.mode()).thenReturn("TEST");
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
        assertThat(result.providerMode()).isEqualTo("TEST");
        assertThat(result.executionMode()).isEqualTo("MANUAL");
        assertThat(result.totalAmount()).isEqualByComparingTo("100.00");
        assertThat(settlement.getBeneficiary()).isSameAs(beneficiary);
        assertThat(settlement.getPayoutReference()).startsWith("lf_settlement_");
        verify(batchRepository).saveAndFlush(any(SettlementBatch.class));
    }

    @Test
    void automaticTestBatchHasExplicitModeAndNoImpersonatedHumanApprover() {
        UUID settlementId = UUID.randomUUID();
        UUID purchaseOrderId = UUID.randomUUID();
        SupplierSettlement settlement = settlement(settlementId, purchaseOrderId);
        CheckoutPayment payment = settlement.getAllocation().getPayment();
        PurchaseOrderShipment shipment = new PurchaseOrderShipment();
        shipment.setStatus(PurchaseOrderShipmentStatus.RECEIVED);
        shipment.setReceivedAt(OffsetDateTime.now().minusDays(1));
        PayoutBeneficiary beneficiary = new PayoutBeneficiary();
        beneficiary.setOrganization(settlement.getSupplierOrganization());
        beneficiary.setStatus(PayoutBeneficiaryStatus.ACTIVE);
        beneficiary.setCurrency("KES");
        beneficiary.setProviderMode("TEST");

        org.springframework.test.util.ReflectionTestUtils.setField(service, "testAutoSettlementEnabled", true);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "deliveryHoldDays", 0);
        when(paystackPaymentService.enabled()).thenReturn(true);
        when(paystackPaymentService.transfersEnabled()).thenReturn(true);
        when(paystackPaymentService.mode()).thenReturn("TEST");
        when(settlementRepository.findById(settlementId)).thenReturn(Optional.of(settlement));
        when(checkoutPaymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(settlementRepository.findByIdForUpdate(settlementId)).thenReturn(Optional.of(settlement));
        when(purchaseOrderShipmentRepository.findByPurchaseOrderIdForUpdate(purchaseOrderId))
                .thenReturn(Optional.of(shipment));
        when(disputeRepository.existsUnclearedForPaymentOrReference(payment.getId(), "TX-123"))
                .thenReturn(false);
        when(paymentRefundRepository.existsByPayment_IdAndStatusIn(eq(payment.getId()), anyCollection()))
                .thenReturn(false);
        when(beneficiaryRepository.findByOrganization_IdAndStatusAndCurrency(
                settlement.getSupplierOrganization().getId(), PayoutBeneficiaryStatus.ACTIVE, "KES"))
                .thenReturn(Optional.of(beneficiary));
        when(batchRepository.saveAndFlush(any(SettlementBatch.class))).thenAnswer(invocation -> {
            SettlementBatch batch = invocation.getArgument(0);
            batch.setId(UUID.randomUUID());
            return batch;
        });
        when(settlementRepository.saveAndFlush(settlement)).thenReturn(settlement);

        UUID batchId = service.createAutomaticTestBatchIfEligible(settlementId);

        assertThat(batchId).isNotNull();
        assertThat(settlement.getSettlementBatch().getExecutionMode()).isEqualTo("TEST_AUTOMATIC");
        assertThat(settlement.getSettlementBatch().getProviderMode()).isEqualTo("TEST");
        assertThat(settlement.getSettlementBatch().getCreatedBy()).isNull();
        assertThat(settlement.getSettlementBatch().getApprovedBy()).isNull();
    }

    @Test
    void automaticSettlementRejectsLiveModeBeforeReadingOrChangingSettlements() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "testAutoSettlementEnabled", true);
        when(paystackPaymentService.enabled()).thenReturn(true);
        when(paystackPaymentService.transfersEnabled()).thenReturn(true);
        when(paystackPaymentService.mode()).thenReturn("LIVE");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.createAutomaticTestBatchIfEligible(UUID.randomUUID()))
                .isInstanceOf(com.example.distrobackend.Exception.ApiException.class)
                .hasMessageContaining("TEST mode");
        verifyNoInteractions(settlementRepository, batchRepository, checkoutPaymentRepository);
    }

    @Test
    void createsBankBeneficiaryUsingFreshlyResolvedNameWithoutReviewByDefault() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Organization organization = new Organization();
        organization.setId(organizationId);
        User user = new User();
        user.setId(userId);
        AuthenticatedUser actor = new AuthenticatedUser(
                userId, UserRole.DISTRIBUTOR_ADMIN, organizationId, null, null);
        when(paystackPaymentService.listBankDestinations("KES")).thenReturn(java.util.List.of(
                new PaystackPaymentService.DestinationOption("Equity Bank", "68", "KES", "bank", true)));
        when(paystackPaymentService.resolveBankAccount("0123456789", "68", "KES"))
                .thenReturn(new PaystackPaymentService.ResolvedBankAccount("Jane Doe", "68", "KES"));
        when(organizationRepository.findByIdForUpdate(organizationId)).thenReturn(Optional.of(organization));
        when(beneficiaryRepository.findByOrganization_IdAndStatusAndCurrency(
                organizationId, PayoutBeneficiaryStatus.ACTIVE, "KES")).thenReturn(Optional.empty());
        when(paystackPaymentService.createRecipient(PayoutMethod.BANK_ACCOUNT, "Jane Doe", "0123456789",
                "68", "KES", null)).thenReturn(new PaystackPaymentService.Recipient("RCP_TEST"));
        when(paystackPaymentService.mode()).thenReturn("TEST");
        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(beneficiaryRepository.save(any(PayoutBeneficiary.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createBeneficiary(actor, new CreatePayoutBeneficiaryRequest(
                "BANK_ACCOUNT", "Jane Doe", "KES", null, "0123456789", "68", null, true));

        assertThat(response.accountName()).isEqualTo("Jane Doe");
        assertThat(response.accountMask()).isEqualTo("****6789");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.providerMode()).isEqualTo("TEST");
        verify(paystackPaymentService).resolveBankAccount("0123456789", "68", "KES");
    }

    @Test
    void doesNotCreateBankRecipientWhenConfirmedNameDiffersFromResolvedName() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AuthenticatedUser actor = new AuthenticatedUser(
                userId, UserRole.DISTRIBUTOR_ADMIN, organizationId, null, null);
        when(paystackPaymentService.listBankDestinations("KES")).thenReturn(java.util.List.of(
                new PaystackPaymentService.DestinationOption("Equity Bank", "68", "KES", "bank", true)));
        when(paystackPaymentService.resolveBankAccount("0123456789", "68", "KES"))
                .thenReturn(new PaystackPaymentService.ResolvedBankAccount("Jane Doe", "68", "KES"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.createBeneficiary(actor,
                        new CreatePayoutBeneficiaryRequest("BANK_ACCOUNT", "Different Name", "KES",
                                null, "0123456789", "68", null, true)))
                .isInstanceOf(com.example.distrobackend.Exception.ApiException.class)
                .hasMessageContaining("does not match");
        verify(paystackPaymentService, never()).createRecipient(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(organizationRepository);
    }

    @Test
    void createsMpesaBeneficiaryFromPaystackMobileMoneyOptions() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Organization organization = new Organization();
        organization.setId(organizationId);
        User user = new User();
        user.setId(userId);
        AuthenticatedUser actor = new AuthenticatedUser(
                userId, UserRole.MANUFACTURER_ADMIN, organizationId, null, null);
        when(paystackPaymentService.listMobileMoneyDestinations("KES")).thenReturn(java.util.List.of(
                new PaystackPaymentService.DestinationOption("M-PESA", "MPESA", "KES", "mobile_money", true)));
        when(organizationRepository.findByIdForUpdate(organizationId)).thenReturn(Optional.of(organization));
        when(beneficiaryRepository.findByOrganization_IdAndStatusAndCurrency(
                organizationId, PayoutBeneficiaryStatus.ACTIVE, "KES")).thenReturn(Optional.empty());
        when(paystackPaymentService.createRecipient(PayoutMethod.MOBILE_MONEY, "Supplier Name", "254700000001",
                "MPESA", "KES", null)).thenReturn(new PaystackPaymentService.Recipient("RCP_MPESA"));
        when(paystackPaymentService.mode()).thenReturn("TEST");
        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(beneficiaryRepository.save(any(PayoutBeneficiary.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createBeneficiary(actor, new CreatePayoutBeneficiaryRequest(
                "MOBILE_MONEY", "Supplier Name", "KES", "+254700000001", null, "MPESA", null, true));

        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.bankCode()).isEqualTo("MPESA");
        verify(paystackPaymentService, never()).resolveBankAccount(any(), any(), any());
    }

    @Test
    void reviewFlagKeepsVerifiedBeneficiaryPendingForSuperAdmin() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Organization organization = new Organization();
        organization.setId(organizationId);
        User user = new User();
        user.setId(userId);
        AuthenticatedUser actor = new AuthenticatedUser(
                userId, UserRole.DISTRIBUTOR_ADMIN, organizationId, null, null);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "beneficiaryReviewRequired", true);
        when(paystackPaymentService.listBankDestinations("KES")).thenReturn(java.util.List.of(
                new PaystackPaymentService.DestinationOption("Equity Bank", "68", "KES", "bank", true)));
        when(paystackPaymentService.resolveBankAccount("0123456789", "68", "KES"))
                .thenReturn(new PaystackPaymentService.ResolvedBankAccount("Jane Doe", "68", "KES"));
        when(organizationRepository.findByIdForUpdate(organizationId)).thenReturn(Optional.of(organization));
        when(paystackPaymentService.createRecipient(PayoutMethod.BANK_ACCOUNT, "Jane Doe", "0123456789",
                "68", "KES", null)).thenReturn(new PaystackPaymentService.Recipient("RCP_PENDING"));
        when(paystackPaymentService.mode()).thenReturn("TEST");
        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(beneficiaryRepository.save(any(PayoutBeneficiary.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createBeneficiary(actor, new CreatePayoutBeneficiaryRequest(
                "BANK_ACCOUNT", "Jane Doe", "KES", null, "0123456789", "68", null, true));

        assertThat(response.status()).isEqualTo("PENDING_REVIEW");
        assertThat(response.reviewNote()).isEqualTo("Submitted for platform review");
        verify(beneficiaryRepository, never()).findByOrganization_IdAndStatusAndCurrency(
                organizationId, PayoutBeneficiaryStatus.ACTIVE, "KES");
    }

    @Test
    void doesNotBatchSettlementBeforeDistributorConfirmsReceipt() {
        UUID settlementId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID purchaseOrderId = UUID.randomUUID();
        SupplierSettlement settlement = settlement(settlementId, purchaseOrderId);
        CheckoutPayment payment = settlement.getAllocation().getPayment();
        PurchaseOrderShipment shipment = new PurchaseOrderShipment();
        shipment.setStatus(PurchaseOrderShipmentStatus.DELIVERED);
        shipment.setDeliveredAt(OffsetDateTime.now().minusDays(8));
        when(settlementRepository.findAllById(java.util.List.of(settlementId))).thenReturn(java.util.List.of(settlement));
        when(settlementRepository.findByIdForUpdate(settlementId)).thenReturn(Optional.of(settlement));
        when(checkoutPaymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(purchaseOrderShipmentRepository.findByPurchaseOrderIdForUpdate(purchaseOrderId))
                .thenReturn(Optional.of(shipment));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.createBatch(
                new AuthenticatedUser(adminId, UserRole.SUPER_ADMIN, null, null, null),
                new CreateSettlementBatchRequest(java.util.List.of(settlementId), "weekly settlement")))
                .isInstanceOf(com.example.distrobackend.Exception.ApiException.class)
                .hasMessageContaining("confirms shipment receipt");
        verify(batchRepository, never()).saveAndFlush(any(SettlementBatch.class));
    }

    @Test
    void doesNotBatchSettlementWhilePostReceiptHoldIsActive() {
        UUID settlementId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID purchaseOrderId = UUID.randomUUID();
        SupplierSettlement settlement = settlement(settlementId, purchaseOrderId);
        CheckoutPayment payment = settlement.getAllocation().getPayment();
        PurchaseOrderShipment shipment = new PurchaseOrderShipment();
        shipment.setStatus(PurchaseOrderShipmentStatus.RECEIVED);
        shipment.setReceivedAt(OffsetDateTime.now());
        when(settlementRepository.findAllById(java.util.List.of(settlementId))).thenReturn(java.util.List.of(settlement));
        when(settlementRepository.findByIdForUpdate(settlementId)).thenReturn(Optional.of(settlement));
        when(checkoutPaymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(purchaseOrderShipmentRepository.findByPurchaseOrderIdForUpdate(purchaseOrderId))
                .thenReturn(Optional.of(shipment));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.createBatch(
                new AuthenticatedUser(adminId, UserRole.SUPER_ADMIN, null, null, null),
                new CreateSettlementBatchRequest(java.util.List.of(settlementId), "weekly settlement")))
                .isInstanceOf(com.example.distrobackend.Exception.ApiException.class)
                .hasMessageContaining("post-receipt return period ends");
        verify(batchRepository, never()).saveAndFlush(any(SettlementBatch.class));
    }

    private static SupplierSettlement settlement(UUID settlementId, UUID purchaseOrderId) {
        Organization supplier = new Organization();
        supplier.setId(UUID.randomUUID());
        CheckoutPayment payment = new CheckoutPayment();
        payment.setId(UUID.randomUUID());
        payment.setProviderReference("TX-123");
        PurchaseOrder purchaseOrder = new PurchaseOrder();
        purchaseOrder.setId(purchaseOrderId);
        PaymentAllocation allocation = new PaymentAllocation();
        allocation.setPayment(payment);
        allocation.setPurchaseOrder(purchaseOrder);
        SupplierSettlement settlement = new SupplierSettlement();
        settlement.setId(settlementId);
        settlement.setSupplierOrganization(supplier);
        settlement.setAllocation(allocation);
        settlement.setStatus(SupplierSettlementStatus.PENDING);
        settlement.setAmount(new BigDecimal("100.00"));
        settlement.setCurrency("KES");
        return settlement;
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
