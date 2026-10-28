package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.Warehouse;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.dto.UpdateWarehouseStatusRequest;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.StockItemRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private StockItemRepository stockItemRepository;

    @Test
    void organizationCannotUpdateAnotherOrganizationsWarehouse() {
        UUID ownOrganizationId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findByIdAndOrganization_Id(warehouseId, ownOrganizationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().get(actor(ownOrganizationId), warehouseId))
                .isInstanceOf(com.example.distrobackend.Exception.ResourceNotFoundException.class);
    }

    @Test
    void activeStockPreventsWarehouseDeactivation() {
        UUID organizationId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Warehouse warehouse = warehouse(warehouseId, organizationId);
        when(warehouseRepository.findByIdAndOrganization_IdForUpdate(warehouseId, organizationId))
                .thenReturn(Optional.of(warehouse));
        when(stockItemRepository.existsByWarehouse_IdAndActiveTrue(warehouseId)).thenReturn(true);

        assertThatThrownBy(() -> service().updateStatus(actor(organizationId), warehouseId,
                new UpdateWarehouseStatusRequest(false, 0L)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("active stock");
    }

    @Test
    void staleWarehouseVersionIsRejectedBeforeMutation() {
        UUID organizationId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Warehouse warehouse = warehouse(warehouseId, organizationId);
        warehouse.setVersion(4);
        when(warehouseRepository.findByIdAndOrganization_IdForUpdate(warehouseId, organizationId))
                .thenReturn(Optional.of(warehouse));

        assertThatThrownBy(() -> service().updateStatus(actor(organizationId), warehouseId,
                new UpdateWarehouseStatusRequest(false, 3L)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("changed by another request");
        verify(stockItemRepository, org.mockito.Mockito.never()).existsByWarehouse_IdAndActiveTrue(any());
    }

    private WarehouseService service() {
        return new WarehouseService(warehouseRepository, organizationRepository, stockItemRepository);
    }

    private static Warehouse warehouse(UUID warehouseId, UUID organizationId) {
        Organization organization = new Organization();
        organization.setId(organizationId);
        organization.setType(Organizationtype.DISTRIBUTOR);
        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        warehouse.setOrganization(organization);
        warehouse.setName("Main");
        warehouse.setCode("MAIN");
        warehouse.setActive(true);
        return warehouse;
    }

    private static AuthenticatedUser actor(UUID organizationId) {
        return new AuthenticatedUser(UUID.randomUUID(), UserRole.DISTRIBUTOR_ADMIN, organizationId,
                Organizationtype.DISTRIBUTOR, Instant.now().plusSeconds(300));
    }
}
