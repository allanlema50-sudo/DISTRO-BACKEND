package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.entity.StockMovement;
import com.example.distrobackend.Domain.enums.StockMovementType;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.InsufficientStockException;
import com.example.distrobackend.dto.StockAdjustmentRequest;
import com.example.distrobackend.dto.CreateRestockRequest;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.StockMovementRepository;
import com.example.distrobackend.repository.RestockRequestRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private StockItemRepository stockItemRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RestockRequestRepository restockRequestRepository;

    @InjectMocks
    private StockService stockService;

    private AuthenticatedUser user;
    private UUID organizationId;
    private UUID stockItemId;

    @BeforeEach
    void setUp() {
        organizationId = UUID.randomUUID();
        stockItemId = UUID.randomUUID();
        user = new AuthenticatedUser(
                UUID.randomUUID(), UserRole.DISTRIBUTOR_STAFF, organizationId, null,
                Instant.now().plusSeconds(60));
    }

    @Test
    void outboundMovementDecreasesQuantityAndCreatesLedgerEntry() {
        StockItem item = itemWithQuantity(10);
        when(stockItemRepository.findByIdAndOrganizationIdForUpdate(stockItemId, organizationId))
                .thenReturn(java.util.Optional.of(item));
        when(stockMovementRepository.saveAndFlush(any(StockMovement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        stockService.adjust(user, stockItemId,
                new StockAdjustmentRequest(StockMovementType.SALE_OUT, -3, "ORDER", null, "Sale"));

        assertThat(item.getQuantityOnHand()).isEqualTo(7);
        verify(stockItemRepository).save(item);
        verify(stockMovementRepository).saveAndFlush(any(StockMovement.class));
    }

    @Test
    void outboundMovementCannotCreateNegativeInventory() {
        StockItem item = itemWithQuantity(2);
        when(stockItemRepository.findByIdAndOrganizationIdForUpdate(stockItemId, organizationId))
                .thenReturn(java.util.Optional.of(item));

        assertThatThrownBy(() -> stockService.adjust(user, stockItemId,
                new StockAdjustmentRequest(StockMovementType.SALE_OUT, -3, null, null, null)))
                .isInstanceOf(InsufficientStockException.class);

        verify(stockItemRepository, never()).save(item);
        verify(stockMovementRepository, never()).saveAndFlush(any());
    }

    @Test
    void movementTypeMustMatchQuantityDirection() {
        StockItem item = itemWithQuantity(10);
        when(stockItemRepository.findByIdAndOrganizationIdForUpdate(stockItemId, organizationId))
                .thenReturn(java.util.Optional.of(item));

        assertThatThrownBy(() -> stockService.adjust(user, stockItemId,
                new StockAdjustmentRequest(StockMovementType.RESTOCK_IN, -1, null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("The stock movement type and quantity delta are inconsistent");

        verify(stockItemRepository, never()).save(item);
    }

    @Test
    void availabilityCannotBeReadAcrossOrganizations() {
        UUID requestedOrganizationId = UUID.randomUUID();

        assertThatThrownBy(() -> stockService.availability(user, requestedOrganizationId,
                org.springframework.data.domain.PageRequest.of(0, 20)))
                .isInstanceOf(ApiException.class)
                .hasMessage("You do not have permission to access this resource");

        verifyNoInteractions(organizationRepository, stockItemRepository);
    }

    @Test
    void partialUpdateMustContainAtLeastOneField() {
        assertThatThrownBy(() -> stockService.update(user, stockItemId,
                new com.example.distrobackend.dto.UpdateStockItemRequest(null, null, null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("At least one product field must be supplied");

        verifyNoInteractions(stockItemRepository);
    }

    @Test
    void distributorCanCreateRestockRequestForOwnedStock() {
        com.example.distrobackend.Domain.entity.Organization distributor = organization(
                organizationId, Organizationtype.DISTRIBUTOR);
        UUID manufacturerId = UUID.randomUUID();
        com.example.distrobackend.Domain.entity.Organization manufacturer = organization(
                manufacturerId, Organizationtype.MANUFACTURER);
        StockItem item = itemWithQuantity(2);
        item.setSku("SKU-1");
        com.example.distrobackend.Domain.entity.User requester = new com.example.distrobackend.Domain.entity.User();
        requester.setId(user.userId());

        when(organizationRepository.findById(organizationId)).thenReturn(java.util.Optional.of(distributor));
        when(organizationRepository.findById(manufacturerId)).thenReturn(java.util.Optional.of(manufacturer));
        when(stockItemRepository.findByIdAndOrganization_Id(stockItemId, organizationId))
                .thenReturn(java.util.Optional.of(item));
        when(userRepository.getReferenceById(user.userId())).thenReturn(requester);
        when(restockRequestRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = stockService.createRestockRequest(user,
                new CreateRestockRequest(stockItemId, manufacturerId, 20, "Urgent restock"));

        assertThat(response.stockItemSku()).isEqualTo("SKU-1");
        assertThat(response.requestedQuantity()).isEqualTo(20);
        assertThat(response.status()).isEqualTo(com.example.distrobackend.Domain.enums.RestockRequestStatus.PENDING);
        verify(restockRequestRepository).saveAndFlush(any());
    }

    private static com.example.distrobackend.Domain.entity.Organization organization(
            UUID id, Organizationtype type) {
        com.example.distrobackend.Domain.entity.Organization organization =
                new com.example.distrobackend.Domain.entity.Organization();
        organization.setId(id);
        organization.setType(type);
        return organization;
    }

    private StockItem itemWithQuantity(int quantity) {
        StockItem item = new StockItem();
        item.setId(stockItemId);
        item.setQuantityOnHand(quantity);
        return item;
    }
}
