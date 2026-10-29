package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.PurchaseOrder;
import com.example.distrobackend.Domain.entity.PurchaseOrderSettlement;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.PurchaseOrderStatus;
import com.example.distrobackend.Domain.enums.SettlementStatus;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.dto.CreateShipmentRequest;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentServiceTest {

    @Mock private PurchaseOrderShipmentRepository shipmentRepository;
    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private PurchaseOrderSettlementRepository settlementRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private StockItemRepository stockItemRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks private ShipmentService shipmentService;

    @Test
    void shipmentCannotBeCreatedBeforeSettlement() {
        UUID purchaseOrderId = UUID.randomUUID();
        UUID manufacturerId = UUID.randomUUID();
        PurchaseOrder purchaseOrder = purchaseOrder(manufacturerId);
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

    private PurchaseOrder purchaseOrder(UUID manufacturerId) {
        PurchaseOrder purchaseOrder = new PurchaseOrder();
        Organization buyer = organization(UUID.randomUUID(), Organizationtype.DISTRIBUTOR);
        Organization supplier = organization(manufacturerId, Organizationtype.MANUFACTURER);
        purchaseOrder.setBuyerOrganization(buyer);
        purchaseOrder.setSupplierOrganization(supplier);
        purchaseOrder.setStatus(PurchaseOrderStatus.APPROVED);
        purchaseOrder.setId(UUID.randomUUID());
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
