package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.entity.Warehouse;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.dto.CreateStockAuditRequest;
import com.example.distrobackend.repository.StockAuditRepository;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.StockMovementRepository;
import com.example.distrobackend.repository.OrganizationRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockAuditServiceTest {

    @Mock private StockAuditRepository auditRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private StockItemRepository stockItemRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private StockAuditService stockAuditService;

    @Test
    void auditCannotCountBelowReservedQuantity() {
        UUID organizationId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID stockItemId = UUID.randomUUID();
        Organization organization = organization(organizationId);
        Warehouse warehouse = warehouse(warehouseId, organization);
        StockItem stockItem = new StockItem();
        stockItem.setId(stockItemId);
        stockItem.setOrganization(organization);
        stockItem.setWarehouse(warehouse);
        stockItem.setQuantityOnHand(10);
        stockItem.setReservedQuantity(4);
        when(organizationRepository.findById(organizationId)).thenReturn(Optional.of(organization));
        when(warehouseRepository.findByIdAndOrganization_IdForUpdate(warehouseId, organizationId))
                .thenReturn(Optional.of(warehouse));
        when(stockItemRepository.findByIdAndOrganizationIdForUpdateWithWarehouse(stockItemId, organizationId))
                .thenReturn(Optional.of(stockItem));

        assertThatThrownBy(() -> stockAuditService.record(
                principal(organizationId, UserRole.DISTRIBUTOR_STAFF, organizationId),
                new CreateStockAuditRequest(warehouseId, stockItemId, 3, "Counted damaged units")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("reserved");
    }

    @Test
    void auditRejectsStockFromAnotherWarehouse() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = organization(organizationId);
        Warehouse selectedWarehouse = warehouse(UUID.randomUUID(), organization);
        Warehouse actualWarehouse = warehouse(UUID.randomUUID(), organization);
        StockItem stockItem = new StockItem();
        stockItem.setOrganization(organization);
        stockItem.setWarehouse(actualWarehouse);
        stockItem.setQuantityOnHand(5);
        when(organizationRepository.findById(organizationId)).thenReturn(Optional.of(organization));
        when(warehouseRepository.findByIdAndOrganization_IdForUpdate(
                selectedWarehouse.getId(), organizationId)).thenReturn(Optional.of(selectedWarehouse));
        UUID stockItemId = UUID.randomUUID();
        when(stockItemRepository.findByIdAndOrganizationIdForUpdateWithWarehouse(stockItemId, organizationId))
                .thenReturn(Optional.of(stockItem));

        assertThatThrownBy(() -> stockAuditService.record(
                principal(organizationId, UserRole.DISTRIBUTOR_STAFF, organizationId),
                new CreateStockAuditRequest(selectedWarehouse.getId(), stockItemId, 5, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("warehouse");
    }

    private Organization organization(UUID id) {
        Organization organization = new Organization();
        organization.setId(id);
        organization.setType(Organizationtype.DISTRIBUTOR);
        return organization;
    }

    private Warehouse warehouse(UUID id, Organization organization) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setOrganization(organization);
        warehouse.setActive(true);
        return warehouse;
    }

    private AuthenticatedUser principal(UUID userId, UserRole role, UUID organizationId) {
        return new AuthenticatedUser(userId, role, organizationId,
                role.organizationtype(), Instant.now().plus(Duration.ofMinutes(5)));
    }
}
