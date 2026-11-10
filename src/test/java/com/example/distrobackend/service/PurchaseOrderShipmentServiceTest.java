package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.PurchaseOrder;
import com.example.distrobackend.Domain.entity.PurchaseOrderSettlement;
import com.example.distrobackend.Domain.entity.PurchaseOrderItem;
import com.example.distrobackend.Domain.entity.PurchaseOrderShipment;
import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.entity.StockMovement;
import com.example.distrobackend.Domain.entity.Warehouse;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.PurchaseOrderStatus;
import com.example.distrobackend.Domain.enums.SettlementStatus;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.dto.CreateShipmentRequest;
import com.example.distrobackend.dto.ReceiveShipmentRequest;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

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

    @InjectMocks private PurchaseOrderShipmentService shipmentService;

    @Test
    void shipmentCannotBeCreatedBeforeSettlement() {
        UUID purchaseOrderId = UUID.randomUUID();
        UUID manufacturerId = UUID.randomUUID();
        PurchaseOrder purchaseOrder = purchaseOrder(purchaseOrderId, manufacturerId);
        PurchaseOrderSettlement settlement = new PurchaseOrderSettlement();
        settlement.setStatus(SettlementStatus.PENDING);
        when(purchaseOrderRepository.findByIdForUpdate(purchaseOrderId))
                .thenReturn(Optional.of(purchaseOrder));
        when(purchaseOrderRepository.findByIdWithItems(purchaseOrderId))
                .thenReturn(Optional.of(purchaseOrder));
        when(settlementRepository.findByPurchaseOrderIdOrderByCreatedAtDesc(purchaseOrderId))
                .thenReturn(List.of(settlement));

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
        assertThat(response.receiptLines()).singleElement().satisfies(line -> {
            assertThat(line.acceptedQuantity()).isEqualTo(2);
            assertThat(line.damagedQuantity()).isEqualTo(1);
            assertThat(line.missingQuantity()).isEqualTo(2);
        });
        verify(stockMovementRepository).save(any(StockMovement.class));

        shipmentService.receive(actor, shipmentId, new ReceiveShipmentRequest("Retry", List.of()));
        assertThat(offer.getQuantityOnHand()).isEqualTo(12);
        verify(stockMovementRepository).save(any(StockMovement.class));
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
