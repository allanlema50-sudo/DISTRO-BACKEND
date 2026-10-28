package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.entity.Warehouse;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.dto.UpdateStockItemRequest;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.StockMovementRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.repository.WarehouseRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminStockServiceTest {

    @Mock
    private StockItemRepository stockItemRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Test
    void platformAdminCannotActivateOfferInInactiveWarehouse() {
        UUID inventoryId = UUID.randomUUID();
        UUID distributorId = UUID.randomUUID();
        Organization distributor = organization(distributorId, Organizationtype.DISTRIBUTOR);
        Warehouse warehouse = new Warehouse();
        warehouse.setId(UUID.randomUUID());
        warehouse.setOrganization(distributor);
        warehouse.setActive(false);

        StockItem offer = new StockItem();
        offer.setId(inventoryId);
        offer.setOrganization(distributor);
        offer.setWarehouse(warehouse);
        offer.setSourceStockItem(new StockItem());
        offer.setActive(false);

        when(stockItemRepository.findByIdForUpdate(inventoryId)).thenReturn(Optional.of(offer));
        when(warehouseRepository.findByIdAndOrganization_IdForUpdate(
                warehouse.getId(), distributorId)).thenReturn(Optional.of(warehouse));

        AdminStockService service = new AdminStockService(
                stockItemRepository, stockMovementRepository, organizationRepository,
                userRepository, warehouseRepository);
        AuthenticatedUser admin = new AuthenticatedUser(
                UUID.randomUUID(), UserRole.SUPER_ADMIN, null, null,
                Instant.now().plusSeconds(300));

        assertThatThrownBy(() -> service.update(admin, inventoryId,
                new UpdateStockItemRequest(null, null, null, null, true)))
                .isInstanceOf(ApiException.class)
                .hasMessage("An offer cannot be activated while its warehouse is inactive");
    }

    private static Organization organization(UUID id, Organizationtype type) {
        Organization organization = new Organization();
        organization.setId(id);
        organization.setType(type);
        return organization;
    }
}
