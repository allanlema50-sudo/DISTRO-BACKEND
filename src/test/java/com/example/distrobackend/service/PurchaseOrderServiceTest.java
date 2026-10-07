package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.PurchaseOrder;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.PurchaseOrderRepository;
import com.example.distrobackend.repository.PurchaseOrderSettlementRepository;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceTest {

    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private PurchaseOrderSettlementRepository settlementRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private StockItemRepository stockItemRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private PurchaseOrderService purchaseOrderService;

    @Test
    void manufacturerCannotReadAnotherManufacturersPurchaseOrder() {
        UUID ownerId = UUID.randomUUID();
        UUID otherManufacturerId = UUID.randomUUID();
        PurchaseOrder purchaseOrder = new PurchaseOrder();
        Organization buyer = organization(UUID.randomUUID(), Organizationtype.DISTRIBUTOR);
        Organization supplier = organization(ownerId, Organizationtype.MANUFACTURER);
        purchaseOrder.setBuyerOrganization(buyer);
        purchaseOrder.setSupplierOrganization(supplier);
        UUID purchaseOrderId = UUID.randomUUID();
        when(purchaseOrderRepository.findByIdWithItems(purchaseOrderId)).thenReturn(Optional.of(purchaseOrder));

        assertThatThrownBy(() -> purchaseOrderService.getForManufacturer(
                principal(otherManufacturerId, UserRole.MANUFACTURER_STAFF, otherManufacturerId), purchaseOrderId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("permission");
    }

    @Test
    void distributorCannotCreatePurchaseOrderAgainstAnotherDistributor() {
        UUID distributorId = UUID.randomUUID();
        Organization supplier = organization(UUID.randomUUID(), Organizationtype.DISTRIBUTOR);
        when(organizationRepository.findById(distributorId))
                .thenReturn(Optional.of(organization(distributorId, Organizationtype.DISTRIBUTOR)));
        when(organizationRepository.findById(supplier.getId())).thenReturn(Optional.of(supplier));

        assertThatThrownBy(() -> purchaseOrderService.create(
                principal(distributorId, UserRole.DISTRIBUTOR_ADMIN, distributorId),
                new com.example.distrobackend.dto.CreatePurchaseOrderRequest(
                        supplier.getId(), java.util.List.of())))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("manufacturer");
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
