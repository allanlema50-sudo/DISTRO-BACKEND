package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.entity.StockLocation;
import com.example.distrobackend.Domain.entity.Warehouse;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.dto.PutAwayStockRequest;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.StockLocationRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockLocationServiceTest {
    @Mock private StockItemRepository stockItemRepository;
    @Mock private StockLocationRepository stockLocationRepository;
    @InjectMocks private StockLocationService service;

    @Test
    void putAwayAssignsOnlyUnassignedStockToALocation() {
        UUID organizationId = UUID.randomUUID();
        UUID stockItemId = UUID.randomUUID();
        Organization distributor = new Organization();
        distributor.setId(organizationId);
        distributor.setType(Organizationtype.DISTRIBUTOR);
        StockItem source = new StockItem();
        source.setId(UUID.randomUUID());
        Warehouse warehouse = new Warehouse();
        warehouse.setActive(true);
        StockItem item = new StockItem();
        item.setId(stockItemId);
        item.setOrganization(distributor);
        item.setSourceStockItem(source);
        item.setWarehouse(warehouse);
        item.setSku("OIL-1L");
        item.setQuantityOnHand(10);
        AuthenticatedUser actor = new AuthenticatedUser(UUID.randomUUID(), UserRole.DISTRIBUTOR_STAFF,
                organizationId, Organizationtype.DISTRIBUTOR, Instant.now().plusSeconds(300));
        when(stockItemRepository.findByIdAndOrganizationIdForUpdateWithWarehouse(stockItemId, organizationId))
                .thenReturn(Optional.of(item));
        when(stockLocationRepository.sumQuantityByStockItemId(stockItemId)).thenReturn(6L);
        when(stockLocationRepository.findForUpdate(stockItemId, "A-01")).thenReturn(Optional.empty());
        when(stockLocationRepository.save(any(StockLocation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.putAway(actor, stockItemId, new PutAwayStockRequest(" a-01 ", 4));

        assertThat(result.locationCode()).isEqualTo("A-01");
        assertThat(result.quantity()).isEqualTo(4);
        assertThat(item.getQuantityOnHand()).isEqualTo(10);
    }

    @Test
    void putAwayRejectsQuantityAboveUnassignedStock() {
        UUID organizationId = UUID.randomUUID();
        UUID stockItemId = UUID.randomUUID();
        Organization distributor = new Organization();
        distributor.setId(organizationId);
        StockItem source = new StockItem();
        source.setId(UUID.randomUUID());
        Warehouse warehouse = new Warehouse();
        warehouse.setActive(true);
        StockItem item = new StockItem();
        item.setId(stockItemId);
        item.setOrganization(distributor);
        item.setSourceStockItem(source);
        item.setWarehouse(warehouse);
        item.setQuantityOnHand(10);
        AuthenticatedUser actor = new AuthenticatedUser(UUID.randomUUID(), UserRole.DISTRIBUTOR_ADMIN,
                organizationId, Organizationtype.DISTRIBUTOR, Instant.now().plusSeconds(300));
        when(stockItemRepository.findByIdAndOrganizationIdForUpdateWithWarehouse(stockItemId, organizationId))
                .thenReturn(Optional.of(item));
        when(stockLocationRepository.sumQuantityByStockItemId(stockItemId)).thenReturn(8L);

        assertThatThrownBy(() -> service.putAway(actor, stockItemId,
                new PutAwayStockRequest("A-02", 3)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("unassigned on-hand stock");
    }
}
