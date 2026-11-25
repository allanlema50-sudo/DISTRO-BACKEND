package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.entity.StockLocation;
import com.example.distrobackend.Domain.entity.StockMovement;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.entity.Warehouse;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.StockMovementType;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.dto.PutAwayStockRequest;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.StockLocationRepository;
import com.example.distrobackend.repository.StockMovementRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockLocationServiceTest {
    @Mock private StockItemRepository stockItemRepository;
    @Mock private StockLocationRepository stockLocationRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private StockLocationService service;

    @Test
    void putAwayAssignsOnlyUnassignedStockToALocationAndReturnsMovedQuantity() {
        UUID organizationId = UUID.randomUUID();
        UUID stockItemId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID movementId = UUID.randomUUID();
        Organization distributor = new Organization();
        distributor.setId(organizationId);
        distributor.setType(Organizationtype.DISTRIBUTOR);
        StockItem source = new StockItem();
        source.setId(UUID.randomUUID());
        Warehouse warehouse = new Warehouse();
        warehouse.setActive(true);
        warehouse.setOrganization(distributor);
        StockItem item = new StockItem();
        item.setId(stockItemId);
        item.setOrganization(distributor);
        item.setSourceStockItem(source);
        item.setWarehouse(warehouse);
        item.setSku("OIL-1L");
        item.setQuantityOnHand(10);
        AuthenticatedUser actor = new AuthenticatedUser(actorId, UserRole.DISTRIBUTOR_STAFF,
                organizationId, Organizationtype.DISTRIBUTOR, Instant.now().plusSeconds(300));
        User performer = new User();
        performer.setId(actorId);
        when(stockItemRepository.findByIdAndOrganizationIdForUpdateWithWarehouse(stockItemId, organizationId))
                .thenReturn(Optional.of(item));
        when(stockLocationRepository.sumQuantityByStockItemIdAndOrganizationId(stockItemId, organizationId))
                .thenReturn(6L);
        when(stockLocationRepository.findForUpdate(stockItemId, "A-01")).thenReturn(Optional.empty());
        when(stockLocationRepository.save(any(StockLocation.class))).thenAnswer(invocation -> {
            StockLocation location = invocation.getArgument(0);
            location.setId(locationId);
            return location;
        });
        when(userRepository.getReferenceById(actorId)).thenReturn(performer);
        when(stockMovementRepository.saveAndFlush(any(StockMovement.class))).thenAnswer(invocation -> {
            StockMovement movement = invocation.getArgument(0);
            movement.setId(movementId);
            return movement;
        });

        var result = service.putAway(actor, stockItemId, new PutAwayStockRequest(" a-01 ", 4));

        assertThat(result.locationCode()).isEqualTo("A-01");
        assertThat(result.quantity()).isEqualTo(4);
        assertThat(result.movedQuantity()).isEqualTo(4);
        assertThat(result.movementId()).isEqualTo(movementId);
        assertThat(item.getQuantityOnHand()).isEqualTo(10);

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).saveAndFlush(movementCaptor.capture());
        StockMovement movement = movementCaptor.getValue();
        assertThat(movement.getMovementType()).isEqualTo(StockMovementType.PUT_AWAY);
        assertThat(movement.getQuantityDelta()).isZero();
        assertThat(movement.getLocationQuantityDelta()).isEqualTo(4);
        assertThat(movement.getExternalReference()).isEqualTo("A-01");
        assertThat(movement.getReferenceId()).isEqualTo(locationId);
    }

    @Test
    void putAwayToExistingLocationKeepsPerEventMovedQuantitySeparateFromBalance() {
        UUID organizationId = UUID.randomUUID();
        UUID stockItemId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Organization distributor = new Organization();
        distributor.setId(organizationId);
        Warehouse warehouse = new Warehouse();
        warehouse.setActive(true);
        warehouse.setOrganization(distributor);
        StockItem source = new StockItem();
        source.setId(UUID.randomUUID());
        StockItem item = new StockItem();
        item.setId(stockItemId);
        item.setOrganization(distributor);
        item.setSourceStockItem(source);
        item.setWarehouse(warehouse);
        item.setSku("OIL-1L");
        item.setQuantityOnHand(1000);
        StockLocation existing = new StockLocation();
        existing.setId(UUID.randomUUID());
        existing.setStockItem(item);
        existing.setLocationCode("A-01");
        existing.setQuantity(500);
        AuthenticatedUser actor = new AuthenticatedUser(actorId, UserRole.DISTRIBUTOR_ADMIN,
                organizationId, Organizationtype.DISTRIBUTOR, Instant.now().plusSeconds(300));
        User performer = new User();
        performer.setId(actorId);
        when(stockItemRepository.findByIdAndOrganizationIdForUpdateWithWarehouse(stockItemId, organizationId))
                .thenReturn(Optional.of(item));
        when(stockLocationRepository.sumQuantityByStockItemIdAndOrganizationId(stockItemId, organizationId))
                .thenReturn(500L);
        when(stockLocationRepository.findForUpdate(stockItemId, "A-01")).thenReturn(Optional.of(existing));
        when(stockLocationRepository.save(any(StockLocation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.getReferenceById(actorId)).thenReturn(performer);
        when(stockMovementRepository.saveAndFlush(any(StockMovement.class))).thenAnswer(invocation -> {
            StockMovement movement = invocation.getArgument(0);
            movement.setId(UUID.randomUUID());
            return movement;
        });

        var result = service.putAway(actor, stockItemId, new PutAwayStockRequest("A-01", 200));

        assertThat(result.quantity()).isEqualTo(700);
        assertThat(result.movedQuantity()).isEqualTo(200);
        assertThat(item.getQuantityOnHand()).isEqualTo(1000);
    }

    @Test
    void putAwayRejectsQuantityAboveUnassignedStockWithoutPersisting() {
        UUID organizationId = UUID.randomUUID();
        UUID stockItemId = UUID.randomUUID();
        Organization distributor = new Organization();
        distributor.setId(organizationId);
        StockItem source = new StockItem();
        source.setId(UUID.randomUUID());
        Warehouse warehouse = new Warehouse();
        warehouse.setActive(true);
        warehouse.setOrganization(distributor);
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
        when(stockLocationRepository.sumQuantityByStockItemIdAndOrganizationId(stockItemId, organizationId))
                .thenReturn(8L);

        assertThatThrownBy(() -> service.putAway(actor, stockItemId,
                new PutAwayStockRequest("A-02", 3)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("available assignable quantity is 2");

        verify(stockLocationRepository, never()).save(any());
        verify(stockMovementRepository, never()).saveAndFlush(any());
    }
}
