package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.*;
import com.example.distrobackend.Domain.enums.*;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.dto.ResellerListingRequest;
import com.example.distrobackend.repository.*;
import com.example.distrobackend.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResellerListingServiceTest {
 @Mock ResellerListingRepository listings;
 @Mock StockItemRepository products;
 @Mock OrganizationRepository organizations;
 @Mock PurchaseOrderRepository purchaseOrders;
 @Mock CheckoutPaymentRepository payments;
 @Mock PurchaseOrderShipmentRepository receipts;
 @Mock ProductImageRepository productImages;
 @InjectMocks ResellerListingService service;

 @Test void unpaidPurchaseCannotCreateListing() {
  UUID distributorId=UUID.randomUUID(), productId=UUID.randomUUID();
  AuthenticatedUser actor=actor(distributorId);
  StockItem product=product(productId);
  PurchaseOrder order=new PurchaseOrder(); order.setBuyerOrganization(organization(distributorId,Organizationtype.DISTRIBUTOR));
  order.setCheckout(new Checkout());
  order.addItem(line(product));
  when(products.findByIdForUpdate(productId)).thenReturn(Optional.of(product));
  when(purchaseOrders.findByBuyerOrganizationId(eq(distributorId),any(Pageable.class))).thenReturn(new PageImpl<>(List.of(order)));
  assertThatThrownBy(()->service.create(actor,new ResellerListingRequest(productId,new BigDecimal("42.00"))))
    .isInstanceOf(ApiException.class).hasMessageContaining("paid wholesale purchase");
  verify(listings,never()).save(any());
 }

 @Test void paidPurchaseCreatesCanonicalListingAndRestoresAcceptedReceiptStock() {
  UUID distributorId=UUID.randomUUID(), productId=UUID.randomUUID();
  AuthenticatedUser actor=actor(distributorId);
  StockItem product=product(productId);
  Checkout checkout=new Checkout(); CheckoutPayment payment=new CheckoutPayment(); payment.setStatus(CheckoutPaymentStatus.SUCCESS); payment.setInitiatedAt(OffsetDateTime.now()); checkout.setPayment(payment);
  PurchaseOrder order=new PurchaseOrder(); order.setBuyerOrganization(organization(distributorId,Organizationtype.DISTRIBUTOR)); order.setCheckout(checkout); order.addItem(line(product));
  when(products.findByIdForUpdate(productId)).thenReturn(Optional.of(product));
  when(purchaseOrders.findByBuyerOrganizationId(eq(distributorId),any(Pageable.class))).thenReturn(new PageImpl<>(List.of(order)));
  when(receipts.sumAcceptedQuantityForDistributorProduct(distributorId,productId)).thenReturn(100L);
  when(organizations.getReferenceById(distributorId)).thenReturn(organization(distributorId,Organizationtype.DISTRIBUTOR));
  when(listings.save(any())).thenAnswer(invocation->{ ResellerListing listing=invocation.getArgument(0); listing.setId(UUID.randomUUID()); return listing; });
  var response=service.create(actor,new ResellerListingRequest(productId,new BigDecimal("55.00")));
  assertThat(response.manufacturerProductId()).isEqualTo(productId);
  assertThat(response.distributorOrganizationId()).isEqualTo(distributorId);
  assertThat(response.retailPrice()).isEqualByComparingTo("55.00");
  assertThat(response.quantityOnHand()).isEqualTo(100);
  assertThat(response.availableQuantity()).isEqualTo(100);
 }

 @Test void repeatPaidPurchasesReuseTheSameListingAndDoNotResetItsInventory() {
  UUID distributorId=UUID.randomUUID(), productId=UUID.randomUUID(), listingId=UUID.randomUUID();
  AuthenticatedUser actor=actor(distributorId); StockItem product=product(productId);
  Checkout checkout=new Checkout(); CheckoutPayment payment=new CheckoutPayment(); payment.setStatus(CheckoutPaymentStatus.SUCCESS); payment.setInitiatedAt(OffsetDateTime.now()); checkout.setPayment(payment);
  PurchaseOrder first=new PurchaseOrder(); first.setBuyerOrganization(organization(distributorId,Organizationtype.DISTRIBUTOR)); first.setCheckout(checkout); first.addItem(line(product));
  PurchaseOrder second=new PurchaseOrder(); second.setBuyerOrganization(organization(distributorId,Organizationtype.DISTRIBUTOR)); second.setCheckout(checkout); second.addItem(line(product));
  ResellerListing listing=new ResellerListing(); listing.setId(listingId); listing.setDistributor(organization(distributorId,Organizationtype.DISTRIBUTOR)); listing.setManufacturerProduct(product); listing.setQuantityOnHand(100);
  when(products.findByIdForUpdate(productId)).thenReturn(Optional.of(product));
  when(purchaseOrders.findByBuyerOrganizationId(eq(distributorId),any(Pageable.class))).thenReturn(new PageImpl<>(List.of(first,second)));
  when(listings.findByDistributor_IdAndManufacturerProduct_Id(distributorId,productId)).thenReturn(Optional.of(listing));
  when(organizations.getReferenceById(distributorId)).thenReturn(organization(distributorId,Organizationtype.DISTRIBUTOR));
  when(listings.save(any())).thenAnswer(invocation->invocation.getArgument(0));
  var response=service.create(actor,new ResellerListingRequest(productId,new BigDecimal("60.00")));
  assertThat(response.id()).isEqualTo(listingId);
  assertThat(response.quantityOnHand()).isEqualTo(100);
  assertThat(response.retailPrice()).isEqualByComparingTo("60.00");
  verify(receipts,never()).sumAcceptedQuantityForDistributorProduct(any(),any());
 }

 private static AuthenticatedUser actor(UUID org) { return new AuthenticatedUser(UUID.randomUUID(),UserRole.DISTRIBUTOR_ADMIN,org,Organizationtype.DISTRIBUTOR,Instant.now().plusSeconds(600)); }
 private static Organization organization(UUID id,Organizationtype type) { Organization o=new Organization(); o.setId(id); o.setType(type); o.setName("Test"); return o; }
 private static StockItem product(UUID id) { StockItem p=new StockItem(); p.setId(id); p.setOrganization(organization(UUID.randomUUID(),Organizationtype.MANUFACTURER)); p.setActive(true); p.setPublicationStatus(ProductPublicationStatus.PUBLISHED); p.setName("Canonical product"); return p; }
 private static PurchaseOrderItem line(StockItem p) { PurchaseOrderItem i=new PurchaseOrderItem(); i.setStockItem(p); return i; }
}
