package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.StockMovementType;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.dto.StockAdjustmentRequest;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.StockMovementRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock private StockItemRepository stockItemRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private StockService stockService;

    @Test
    void adjustmentCannotMakeStockNegative() {
        UUID organizationId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        StockItem item = new StockItem();
        item.setId(itemId);
        item.setOrganization(organization(organizationId));
        item.setQuantityOnHand(2);
        when(stockItemRepository.findByIdAndOrganizationIdForUpdate(itemId, organizationId))
                .thenReturn(Optional.of(item));

        assertThatThrownBy(() -> stockService.adjust(principal(organizationId), itemId,
                new StockAdjustmentRequest(-3, StockMovementType.SALE_OUT, "invalid withdrawal")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("negative");
        verify(stockItemRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    private AuthenticatedUser principal(UUID organizationId) {
        return new AuthenticatedUser(UUID.randomUUID(), UserRole.DISTRIBUTOR_ADMIN,
                organizationId, Organizationtype.DISTRIBUTOR, Instant.now().plus(Duration.ofMinutes(5)));
    }

    private Organization organization(UUID id) {
        Organization organization = new Organization();
        organization.setId(id);
        organization.setType(Organizationtype.DISTRIBUTOR);
        return organization;
    }
}
