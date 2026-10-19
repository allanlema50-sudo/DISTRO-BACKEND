package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCatalogServiceTest {

    @Mock
    private StockItemRepository stockItemRepository;

    @Test
    void manufacturerCannotReadAnotherOrganizationsInventory() {
        ProductCatalogService service = new ProductCatalogService(stockItemRepository);
        UUID itemId = UUID.randomUUID();
        Organization other = organization(UUID.randomUUID());
        StockItem item = new StockItem();
        item.setId(itemId);
        item.setOrganization(other);
        item.setActive(true);
        when(stockItemRepository.findById(itemId)).thenReturn(java.util.Optional.of(item));

        assertThatThrownBy(() -> service.getInventory(
                principal(UUID.randomUUID(), UserRole.MANUFACTURER_ADMIN, UUID.randomUUID()), itemId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("permission");
    }

    @Test
    void superAdminCanReadPlatformInventory() {
        ProductCatalogService service = new ProductCatalogService(stockItemRepository);
        UUID itemId = UUID.randomUUID();
        StockItem item = new StockItem();
        item.setId(itemId);
        item.setOrganization(organization(UUID.randomUUID()));
        item.setSku("SKU-1");
        item.setName("Product");
        item.setUnitPrice(java.math.BigDecimal.ONE);
        item.setActive(true);
        when(stockItemRepository.findById(itemId)).thenReturn(java.util.Optional.of(item));

        assertThat(service.getInventory(principal(UUID.randomUUID(), UserRole.SUPER_ADMIN, null), itemId).id())
                .isEqualTo(itemId);
    }

    private static Organization organization(UUID id) {
        Organization organization = new Organization();
        organization.setId(id);
        organization.setType(Organizationtype.MANUFACTURER);
        return organization;
    }

    private static AuthenticatedUser principal(UUID userId, UserRole role, UUID organizationId) {
        return new AuthenticatedUser(userId, role, organizationId,
                role.organizationtype(), Instant.now().plusSeconds(300));
    }
}
