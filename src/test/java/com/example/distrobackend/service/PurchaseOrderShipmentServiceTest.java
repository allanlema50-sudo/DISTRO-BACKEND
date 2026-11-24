package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.Checkout;
import com.example.distrobackend.Domain.entity.CheckoutPayment;
import com.example.distrobackend.Domain.entity.PaymentAllocation;
import com.example.distrobackend.Domain.entity.PurchaseOrder;
import com.example.distrobackend.Domain.entity.PurchaseOrderSettlement;
import com.example.distrobackend.Domain.entity.PurchaseOrderItem;
import com.example.distrobackend.Domain.entity.PurchaseOrderShipment;
import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.entity.StockMovement;
import com.example.distrobackend.Domain.entity.Warehouse;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.PurchaseOrderStatus;
import com.example.distrobackend.Domain.enums.PurchaseOrderShipmentStatus;
import com.example.distrobackend.Domain.enums.SettlementStatus;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.dto.CreateShipmentRequest;
import com.example.distrobackend.dto.ReceiveShipmentRequest;
import com.example.distrobackend.dto.ShipmentStatusRequest;
import com.example.distrobackend.repository.PurchaseOrderRepository;
import com.example.distrobackend.repository.PurchaseOrderSettlementRepository;
import com.example.distrobackend.repository.PurchaseOrderShipmentRepository;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.StockMovementRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.repository.WarehouseRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderShipmentServiceTest {

    @Mock private PurchaseOrderShipmentRepository shipmentRepository;
    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private PurchaseOrderSettlementRepository settlementRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private StockItemRepository stockItemRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private com.example.distrobackend.repository.PaymentAllocationRepository paymentAllocationRepository;
    @Mock private com.example.distrobackend.repository.CheckoutItemRepository checkoutItemRepository;
    @Mock private com.example.distrobackend.repository.PurchaseOrderShipmentGroupRepository shipmentGroupRepository;
    @Mock private com.example.distrobackend.repository.TripRepository tripRepository;
    @Mock private com.example.distrobackend.repository.TripStatusHistoryRepository tripStatusHistoryRepository;
    @Mock private com.example.distrobackend.repository.ResellerListingRepository resellerListingRepository;

    @InjectMocks private PurchaseOrderShipmentService shipmentService;

    @Test
    void verifiedSettlementRemainsSuccessfulWhenInventoryIsInsufficient() {
        UUID poId = UUID.randomUUID();
        UUID manufacturerId = UUID.randomUUID();
        Organization manufacturer = organization(manufacturerId, Organizationtype.MANUFACTURER);
        PurchaseOrder order = purchaseOrder(poId, manufacturerId);
        order.setStatus(PurchaseOrderStatus.APPROVED);
        order.setTotalAmount(new BigDecimal("50.00"));
        order.setCurrency("KES");
        StockItem stock = new StockItem();
        stock.setId(UUID.randomUUID());
        stock.setOrganization(manufacturer);
        stock.setActive(true);
        stock.setQuantityOnHand(2);
        Warehouse origin = new Warehouse();
        origin.setOrganization(manufacturer);
        origin.setActive(true);
        stock.setWarehouse(origin);
        PurchaseOrderItem line = new PurchaseOrderItem();
        line.setStockItem(stock);
        line.setQuantity(5);
        order.addItem(line);
        PurchaseOrderSettlement settlement = new PurchaseOrderSettlement();
        settlement.setStatus(SettlementStatus.SETTLED);
        settlement.setAmount(new BigDecimal("50.00"));
        settlement.setCurrency("KES");
        when(purchaseOrderRepository.findByIdForUpdate(poId)).thenReturn(Optional.of(order));
        when(purchaseOrderRepository.findByIdWithItems(poId)).thenReturn(Optional.of(order));
        when(settlementRepository.findByPurchaseOrderIdOrderByCreatedAtDesc(poId))
                .thenReturn(List.of(settlement));
        when(stockItemRepository.findByIdAndOrganizationIdForUpdate(stock.getId(), manufacturerId))
                .thenReturn(Optional.of(stock));
        when(purchaseOrderRepository.save(order)).thenReturn(order);

        shipmentService.handleSettledPurchaseOrder(poId);

        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.SETTLED);
        assertThat(order.isStockReserved()).isFalse();
        assertThat(order.getFulfilmentStatus()).endsWith("_EXCEPTION");
        assertThat(stock.getReservedQuantity()).isZero();
        verify(stockMovementRepository, org.mockito.Mockito.never()).save(any(StockMovement.class));
    }

    @Test
    void confirmedCheckoutDoesNotCreateShipmentWithoutReservedInventory() {
        UUID checkoutId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        UUID poId = UUID.randomUUID();
        Organization buyer = organization(buyerId, Organizationtype.DISTRIBUTOR);
        Organization supplier = organization(supplierId, Organizationtype.MANUFACTURER);
        Warehouse warehouse = new Warehouse();
        warehouse.setId(UUID.randomUUID());
        warehouse.setOrganization(buyer);
        warehouse.setActive(true);
        PurchaseOrder order = new PurchaseOrder();
        order.setId(poId);
        order.setStatus(PurchaseOrderStatus.APPROVED);
        order.setBuyerOrganization(buyer);
        order.setSupplierOrganization(supplier);
        order.setDestinationWarehouse(warehouse);
        order.setTotalAmount(new BigDecimal("120.00"));
        order.setCurrency("KES");
        order.setCreatedBy(new User());
        Checkout checkout = new Checkout();
        checkout.setId(checkoutId);
        order.setCheckout(checkout);
        CheckoutPayment payment = new CheckoutPayment();
        payment.setId(UUID.randomUUID());
        payment.setCheckout(checkout);
        payment.setStatus(com.example.distrobackend.Domain.enums.CheckoutPaymentStatus.SUCCESS);
        PaymentAllocation allocation = new PaymentAllocation();
        allocation.setPayment(payment);
        allocation.setGrossAmount(new BigDecimal("120.00"));
        allocation.setCurrency("KES");
        when(purchaseOrderRepository.findByCheckout_Id(checkoutId)).thenReturn(List.of(order));
        when(paymentAllocationRepository.findByPurchaseOrder_Id(poId)).thenReturn(List.of(allocation));
        when(shipmentRepository.findByPurchaseOrderId(poId)).thenReturn(Optional.empty());
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(call -> call.getArgument(0));

        shipmentService.markCheckoutOrdersReady(payment);

        verify(shipmentRepository, org.mockito.Mockito.never()).save(any(PurchaseOrderShipment.class));
        assertThat(order.getFulfilmentStatus()).endsWith("_EXCEPTION");
    }

    @Test
    void shipmentCannotBeCreatedBeforeSettlement() {
        UUID purchaseOrderId = UUID.randomUUID();
        UUID manufacturerId = UUID.randomUUID();
        PurchaseOrder purchaseOrder = purchaseOrder(purchaseOrderId, manufacturerId);
        purchaseOrder.setStockReserved(true);
        purchaseOrder.setPackageCount(1);
        StockItem source = new StockItem();
        source.setId(UUID.randomUUID());
        source.setOrganization(organization(manufacturerId, Organizationtype.MANUFACTURER));
        source.setActive(true);
        source.setReservedQuantity(1);
        Warehouse origin = new Warehouse();
        origin.setId(UUID.randomUUID());
        origin.setOrganization(source.getOrganization());
        origin.setActive(true);
        source.setWarehouse(origin);
        PurchaseOrderItem line = new PurchaseOrderItem();
        line.setStockItem(source);
        line.setQuantity(1);
        line.setPickedQuantity(1);
        line.setPackedQuantity(1);
        purchaseOrder.addItem(line);
        PurchaseOrderSettlement settlement = new PurchaseOrderSettlement();
        settlement.setStatus(SettlementStatus.PENDING);
        when(purchaseOrderRepository.findByIdForUpdate(purchaseOrderId))
                .thenReturn(Optional.of(purchaseOrder));
        when(purchaseOrderRepository.findByIdWithItems(purchaseOrderId))
                .thenReturn(Optional.of(purchaseOrder));
        when(settlementRepository.findByPurchaseOrderIdOrderByCreatedAtDesc(purchaseOrderId))
                .thenReturn(List.of(settlement));
        when(stockItemRepository.findByIdAndOrganizationIdForUpdate(source.getId(), manufacturerId))
                .thenReturn(Optional.of(source));

        assertThatThrownBy(() -> shipmentService.create(
                principal(manufacturerId, UserRole.MANUFACTURER_ADMIN, manufacturerId),
                new CreateShipmentRequest(purchaseOrderId, UUID.randomUUID(), null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("settled");
    }

    @Test
    void receiptAddsOnlyAcceptedQuantitiesAndIsIdempotent() {
        UUID distributorId = UUID.randomUUID();
        UUID manufacturerId = UUID.randomUUID();
        UUID shipmentId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID poId = UUID.randomUUID();
        Organization distributor = organization(distributorId, Organizationtype.DISTRIBUTOR);
        Organization manufacturer = organization(manufacturerId, Organizationtype.MANUFACTURER);
        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        warehouse.setOrganization(distributor);
        warehouse.setActive(true);
        StockItem source = new StockItem();
        source.setId(sourceId);
        source.setOrganization(manufacturer);
        source.setSku("SOURCE-1");
        source.setName("Rice");
        StockItem offer = new StockItem();
        offer.setId(offerId);
        offer.setOrganization(distributor);
        offer.setWarehouse(warehouse);
        offer.setSourceStockItem(source);
        offer.setSku("RICE-1");
        offer.setName("Rice");
        offer.setQuantityOnHand(10);
        com.example.distrobackend.Domain.entity.ResellerListing listing = new com.example.distrobackend.Domain.entity.ResellerListing();
        listing.setId(UUID.randomUUID());
        listing.setDistributor(distributor);
        listing.setManufacturerProduct(source);
        listing.setQuantityOnHand(3);
        PurchaseOrder po = new PurchaseOrder();
        po.setId(poId);
        po.setPurchaseOrderNumber("PO-17");
        po.setBuyerOrganization(distributor);
        po.setSupplierOrganization(manufacturer);
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(UUID.randomUUID());
        item.setStockItem(source);
        item.setDistributorOffer(offer);
        item.setQuantity(5);
        po.addItem(item);
        PurchaseOrderShipment shipment = new PurchaseOrderShipment();
        shipment.setId(shipmentId);
        shipment.setPurchaseOrder(po);
        shipment.setDestinationWarehouse(warehouse);
        shipment.setStatus(com.example.distrobackend.Domain.enums.PurchaseOrderShipmentStatus.DELIVERED);
        AuthenticatedUser actor = principal(UUID.randomUUID(), UserRole.DISTRIBUTOR_ADMIN, distributorId);
        when(shipmentRepository.findByIdWithDetailsForUpdate(shipmentId)).thenReturn(Optional.of(shipment));
        when(warehouseRepository.findByIdAndOrganization_IdForUpdate(warehouseId, distributorId))
                .thenReturn(Optional.of(warehouse));
        when(stockItemRepository.findByIdAndOrganizationIdForUpdateWithWarehouse(offerId, distributorId))
                .thenReturn(Optional.of(offer));
        when(stockItemRepository.findByIdForUpdate(sourceId)).thenReturn(Optional.of(source));
        when(resellerListingRepository.findByDistributor_IdAndManufacturerProduct_Id(distributorId, sourceId))
                .thenReturn(Optional.of(listing));
        when(shipmentRepository.save(any(PurchaseOrderShipment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findByOrganization_IdAndRole(any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        var response = shipmentService.receive(actor, shipmentId, new ReceiveShipmentRequest("Short receipt", List.of(
                new ReceiveShipmentRequest.Line(item.getId(), 2, 1, 2))));

        assertThat(response.status()).isEqualTo(com.example.distrobackend.Domain.enums.PurchaseOrderShipmentStatus.RECEIVED);
        assertThat(offer.getQuantityOnHand()).isEqualTo(12);
        assertThat(listing.getQuantityOnHand()).isEqualTo(5);
        assertThat(response.receiptLines()).singleElement().satisfies(line -> {
            assertThat(line.acceptedQuantity()).isEqualTo(2);
            assertThat(line.damagedQuantity()).isEqualTo(1);
            assertThat(line.missingQuantity()).isEqualTo(2);
        });
        verify(stockMovementRepository).save(any(StockMovement.class));

        shipmentService.receive(actor, shipmentId, new ReceiveShipmentRequest("Retry", List.of()));
        assertThat(offer.getQuantityOnHand()).isEqualTo(12);
        assertThat(listing.getQuantityOnHand()).isEqualTo(5);
        verify(stockMovementRepository).save(any(StockMovement.class));
    }

    @Test
    void dispatchedShipmentCannotBeCancelledReplacedOrDeductedTwice() {
        UUID manufacturerId = UUID.randomUUID();
        UUID distributorId = UUID.randomUUID();
        UUID shipmentId = UUID.randomUUID();
        UUID purchaseOrderId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Organization manufacturer = organization(manufacturerId, Organizationtype.MANUFACTURER);
        Organization distributor = organization(distributorId, Organizationtype.DISTRIBUTOR);
        Warehouse warehouse = new Warehouse();
        warehouse.setId(UUID.randomUUID());
        warehouse.setOrganization(distributor);
        warehouse.setActive(true);

        StockItem source = new StockItem();
        source.setId(sourceId);
        source.setOrganization(manufacturer);
        source.setSku("SOURCE-1");
        source.setName("Rice");
        source.setQuantityOnHand(20);
        source.setReservedQuantity(5);

        PurchaseOrder purchaseOrder = new PurchaseOrder();
        purchaseOrder.setId(purchaseOrderId);
        purchaseOrder.setPurchaseOrderNumber("PO-42");
        purchaseOrder.setBuyerOrganization(distributor);
        purchaseOrder.setSupplierOrganization(manufacturer);
        purchaseOrder.setStatus(PurchaseOrderStatus.APPROVED);
        purchaseOrder.setTotalAmount(BigDecimal.valueOf(50));
        purchaseOrder.setCurrency("KES");
        purchaseOrder.setStockReserved(true);
        PurchaseOrderItem line = new PurchaseOrderItem();
        line.setId(UUID.randomUUID());
        line.setStockItem(source);
        line.setQuantity(5);
        line.setPickedQuantity(5);
        line.setPackedQuantity(5);
        purchaseOrder.addItem(line);
        purchaseOrder.setPackageCount(1);

        PurchaseOrderShipment shipment = new PurchaseOrderShipment();
        shipment.setId(shipmentId);
        shipment.setPurchaseOrder(purchaseOrder);
        shipment.setDestinationWarehouse(warehouse);
        shipment.setStatus(PurchaseOrderShipmentStatus.READY_FOR_DISPATCH);
        User actorUser = new User();
        actorUser.setId(userId);
        AuthenticatedUser actor = principal(userId, UserRole.MANUFACTURER_ADMIN, manufacturerId);

        when(shipmentRepository.findByIdWithDetailsForUpdate(shipmentId)).thenReturn(Optional.of(shipment));
        when(stockItemRepository.findByIdAndOrganizationIdForUpdate(sourceId, manufacturerId))
                .thenReturn(Optional.of(source));
        when(userRepository.getReferenceById(userId)).thenReturn(actorUser);
        when(shipmentRepository.save(any(PurchaseOrderShipment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        shipmentService.updateStatus(actor, shipmentId,
                new ShipmentStatusRequest(PurchaseOrderShipmentStatus.IN_TRANSIT, "Dispatched"));
        assertThat(source.getQuantityOnHand()).isEqualTo(15);
        assertThat(source.getReservedQuantity()).isZero();

        assertThatThrownBy(() -> shipmentService.updateStatus(actor, shipmentId,
                new ShipmentStatusRequest(PurchaseOrderShipmentStatus.CANCELLED, "Cancel after dispatch")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cannot move from IN_TRANSIT");
        assertThat(shipment.getStatus()).isEqualTo(PurchaseOrderShipmentStatus.IN_TRANSIT);

        shipmentService.updateStatus(actor, shipmentId,
                new ShipmentStatusRequest(PurchaseOrderShipmentStatus.IN_TRANSIT, "Retry"));

        PurchaseOrderSettlement settlement = new PurchaseOrderSettlement();
        settlement.setStatus(SettlementStatus.SETTLED);
        settlement.setAmount(BigDecimal.valueOf(50));
        settlement.setCurrency("KES");
        when(purchaseOrderRepository.findByIdForUpdate(purchaseOrderId)).thenReturn(Optional.of(purchaseOrder));
        when(purchaseOrderRepository.findByIdWithItems(purchaseOrderId)).thenReturn(Optional.of(purchaseOrder));
        when(shipmentRepository.findByPurchaseOrderId(purchaseOrderId)).thenReturn(Optional.of(shipment));

        assertThatThrownBy(() -> shipmentService.create(actor,
                new CreateShipmentRequest(purchaseOrderId, warehouse.getId(), "REPLACEMENT", "Carrier")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("A shipment already exists");

        assertThat(source.getQuantityOnHand()).isEqualTo(15);
        assertThat(source.getReservedQuantity()).isZero();
        verify(stockMovementRepository, times(1)).save(any(StockMovement.class));
    }
    private PurchaseOrder purchaseOrder(UUID purchaseOrderId, UUID manufacturerId) {
        PurchaseOrder purchaseOrder = new PurchaseOrder();
        Organization buyer = organization(UUID.randomUUID(), Organizationtype.DISTRIBUTOR);
        Organization supplier = organization(manufacturerId, Organizationtype.MANUFACTURER);
        purchaseOrder.setBuyerOrganization(buyer);
        purchaseOrder.setSupplierOrganization(supplier);
        purchaseOrder.setStatus(PurchaseOrderStatus.APPROVED);
        purchaseOrder.setId(purchaseOrderId);
        return purchaseOrder;
    }

    private Organization organization(UUID id, Organizationtype type) {
        Organization organization = new Organization();
        organization.setId(id);
        organization.setType(type);
        return organization;
    }

    private AuthenticatedUser principal(UUID userId, UserRole role, UUID organizationId) {
        return new AuthenticatedUser(userId, role, organizationId,
                role.organizationtype(), Instant.now().plus(Duration.ofMinutes(5)));
    }
}
