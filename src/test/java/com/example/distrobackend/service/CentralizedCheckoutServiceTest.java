package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.*;
import com.example.distrobackend.Domain.enums.*;
import com.example.distrobackend.repository.*;
import com.example.distrobackend.dto.CheckoutResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CentralizedCheckoutServiceTest {
 @Mock CheckoutRepository checkoutRepository;
 @Mock CheckoutPaymentRepository checkoutPaymentRepository;
 @Mock CheckoutItemRepository checkoutItemRepository;
 @Mock PaymentAllocationRepository allocationRepository;
 @Mock SupplierSettlementRepository supplierSettlementRepository;
 @Mock PurchaseOrderRepository purchaseOrderRepository;
 @Mock StockItemRepository stockItemRepository;
 @Mock WarehouseRepository warehouseRepository;
 @Mock OrganizationRepository organizationRepository;
 @Mock UserRepository userRepository;
 @Mock PurchaseOrderService purchaseOrderService;
 @Mock CheckoutPaymentAttemptStore paymentAttemptStore;
 @Mock PurchaseOrderShipmentService purchaseOrderShipmentService;
 @InjectMocks CentralizedCheckoutService service;

 @Test void successfulDemoAllocatesFiveMillionAcrossThreeIndependentOrdersWithoutMarkingPayoutsSettled() {
  ReflectionTestUtils.setField(service,"demoEnabled",true);
  ReflectionTestUtils.setField(service,"demoOutcome","SUCCESS");
  ReflectionTestUtils.setField(service,"confirmationDelaySeconds",0L);
  Checkout checkout=new Checkout(); checkout.setId(UUID.randomUUID()); checkout.setTotalAmount(new BigDecimal("5000000.00")); checkout.setCurrency("KES");
  CheckoutPayment payment=new CheckoutPayment(); payment.setId(UUID.randomUUID()); payment.setCheckout(checkout); payment.setAmount(checkout.getTotalAmount()); payment.setCurrency("KES"); payment.setStatus(CheckoutPaymentStatus.PENDING); payment.setDemo(true); payment.setInitiatedAt(OffsetDateTime.now().minusMinutes(1));
  when(checkoutPaymentRepository.findByDemoTrueAndStatusAndInitiatedAtBefore(eq(CheckoutPaymentStatus.PENDING),any())).thenReturn(List.of(payment));
  when(checkoutPaymentRepository.findByIdForUpdate(payment.getId())).thenReturn(java.util.Optional.of(payment));
  List<PurchaseOrder> orders=List.of(order(checkout,"1000000.00"),order(checkout,"1500000.00"),order(checkout,"2500000.00"));
  when(purchaseOrderRepository.findByCheckout_Id(checkout.getId())).thenReturn(orders);
  when(allocationRepository.save(any(PaymentAllocation.class))).thenAnswer(invocation->invocation.getArgument(0));
  when(supplierSettlementRepository.save(any(SupplierSettlement.class))).thenAnswer(invocation->invocation.getArgument(0));
  service.simulatePendingDemoPayments();
  assertThat(payment.getStatus()).isEqualTo(CheckoutPaymentStatus.SUCCESS);
  assertThat(checkout.getStatus()).isEqualTo(CheckoutStatus.PAID);
  assertThat(payment.isDemo()).isTrue();
  assertThat(payment.getProviderReference()).startsWith("DEMO-");
  ArgumentCaptor<PaymentAllocation> allocationCaptor=ArgumentCaptor.forClass(PaymentAllocation.class);
  verify(allocationRepository,times(3)).save(allocationCaptor.capture());
  assertThat(allocationCaptor.getAllValues()).extracting(PaymentAllocation::getGrossAmount)
    .containsExactly(new BigDecimal("1000000.00"),new BigDecimal("1500000.00"),new BigDecimal("2500000.00"));
  assertThat(allocationCaptor.getAllValues()).allSatisfy(allocation->{
   assertThat(allocation.getPayment()).isSameAs(payment);
   assertThat(allocation.isDemo()).isTrue();
   assertThat(allocation.getProviderFee()).isZero();
   assertThat(allocation.getCommission()).isZero();
   assertThat(allocation.getSupplierAmount()).isEqualByComparingTo(allocation.getGrossAmount());
  });
  ArgumentCaptor<SupplierSettlement> settlementCaptor=ArgumentCaptor.forClass(SupplierSettlement.class);
  verify(supplierSettlementRepository,times(3)).save(settlementCaptor.capture());
  assertThat(settlementCaptor.getAllValues()).allSatisfy(settlement->{
   assertThat(settlement.getStatus()).isEqualTo(SupplierSettlementStatus.PENDING);
   assertThat(settlement.isDemo()).isTrue();
   assertThat(settlement.getNote()).contains("no supplier payout");
  });
 }

 @Test void initiationReturnsOnlyPendingAndCannotRequestAFrontendSelectedOutcome() {
  UUID buyerId=UUID.randomUUID();
  ReflectionTestUtils.setField(service,"marketplaceCheckoutEnabled",true);
  ReflectionTestUtils.setField(service,"demoEnabled",true);
  ReflectionTestUtils.setField(service,"demoOrganizationIds",buyerId.toString());
  Checkout checkout=new Checkout(); checkout.setId(UUID.randomUUID()); checkout.setBuyerOrganization(new Organization()); checkout.getBuyerOrganization().setId(buyerId); checkout.getBuyerOrganization().setName("Buyer Ltd"); checkout.setTotalAmount(new BigDecimal("5000000.00")); checkout.setCurrency("KES"); checkout.setStatus(CheckoutStatus.PAYMENT_PENDING);
  when(checkoutRepository.findByIdAndBuyerOrganization_Id(checkout.getId(),buyerId)).thenReturn(java.util.Optional.of(checkout));
  UUID paymentId=UUID.randomUUID();
  CheckoutPayment payment=new CheckoutPayment(); payment.setId(paymentId); payment.setCheckout(checkout);
  payment.setAmount(checkout.getTotalAmount()); payment.setCurrency("KES"); payment.setStatus(CheckoutPaymentStatus.PENDING);
  payment.setDemo(true); payment.setDemoLabel("DEMO \u2014 NO MONEY TRANSFERRED");
  payment.setProviderReference("DEMO-PENDING-test");
  when(paymentAttemptStore.start(eq(checkout.getId()),eq(buyerId),eq(PaymentMethod.OTHER),eq(checkout.getTotalAmount()),eq("KES"),eq(true),eq("DEMO \u2014 NO MONEY TRANSFERRED"),eq("payment-key-1")))
    .thenReturn(new CheckoutPaymentAttemptStore.StartResult(paymentId,true));
  when(checkoutPaymentRepository.findById(paymentId)).thenReturn(java.util.Optional.of(payment));
  when(purchaseOrderRepository.findByCheckout_Id(checkout.getId())).thenReturn(List.of());
  var actor=new com.example.distrobackend.security.AuthenticatedUser(UUID.randomUUID(),UserRole.DISTRIBUTOR_ADMIN,buyerId,Organizationtype.DISTRIBUTOR,java.time.Instant.now().plusSeconds(60));
  CheckoutResponse result=service.initiatePayment(actor,checkout.getId(),new com.example.distrobackend.dto.InitiateCheckoutPaymentRequest("DEMO","payment-key"),"payment-key-1");
  assertThat(result.payment()).isNotNull();
  assertThat(result.payment().method()).isEqualTo("DEMO");
  assertThat(result.payment().status()).isEqualTo("PENDING");
  assertThat(result.payment().demoLabel()).isEqualTo("DEMO \u2014 NO MONEY TRANSFERRED");
  assertThat(result.payment().providerReference()).startsWith("DEMO-PENDING-");
  assertThat(checkout.getPayment().getStatus()).isEqualTo(CheckoutPaymentStatus.PENDING);
 }
 @Test void enabledDemoAllowsTheProductionProfileWhenAnOrganizationIsAllowlisted() {
  ReflectionTestUtils.setField(service,"demoEnabled",true);
  ReflectionTestUtils.setField(service,"demoOrganizationIds",UUID.randomUUID().toString());
  ReflectionTestUtils.setField(service,"demoOutcome","SUCCESS");
  ReflectionTestUtils.setField(service,"confirmationDelaySeconds",3L);
  service.validateDemoConfiguration();
 }

 @Test void enabledDemoRequiresAnExplicitOrganizationUuidAllowlist() {
  ReflectionTestUtils.setField(service,"demoEnabled",true);
  ReflectionTestUtils.setField(service,"demoOrganizationIds"," ");
  ReflectionTestUtils.setField(service,"demoOutcome","SUCCESS");
  ReflectionTestUtils.setField(service,"confirmationDelaySeconds",3L);
  assertThatThrownBy(service::validateDemoConfiguration).isInstanceOf(IllegalStateException.class)
    .hasMessageContaining("LOGIFLOW_DEMO_ORGANIZATION_IDS");
 }

 @Test void disabledDemoDoesNotRequireAnAllowlistEvenInProduction() {
  ReflectionTestUtils.setField(service,"demoEnabled",false);
  ReflectionTestUtils.setField(service,"demoOrganizationIds","");
  ReflectionTestUtils.setField(service,"demoOutcome","SUCCESS");
  ReflectionTestUtils.setField(service,"confirmationDelaySeconds",3L);
  service.validateDemoConfiguration();
 }

 private PurchaseOrder order(Checkout checkout,String amount) {
  PurchaseOrder order=new PurchaseOrder(); order.setId(UUID.randomUUID()); order.setCheckout(checkout);
  Organization supplier=new Organization(); supplier.setId(UUID.randomUUID()); supplier.setName("Supplier");
  order.setSupplierOrganization(supplier); order.setTotalAmount(new BigDecimal(amount)); order.setCurrency("KES");
  return order;
 }
}

