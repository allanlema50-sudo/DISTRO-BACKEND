package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.DistributorProductAuthorization;
import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.ProductAuthorizationStatus;
import com.example.distrobackend.Domain.enums.ProductPublicationStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.dto.CreateProductAuthorizationRequest;
import com.example.distrobackend.dto.ProductAuthorizationDecisionRequest;
import com.example.distrobackend.repository.DistributorProductAuthorizationRepository;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.UserRepository;
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
class ProductAuthorizationServiceTest {

    @Mock
    private DistributorProductAuthorizationRepository authorizationRepository;

    @Mock
    private StockItemRepository stockItemRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private UserRepository userRepository;

    @Test
    void distributorCannotRequestAuthorizationForUnpublishedProduct() {
        UUID distributorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Organization distributor = organization(distributorId, Organizationtype.DISTRIBUTOR);
        Organization manufacturer = organization(UUID.randomUUID(), Organizationtype.MANUFACTURER);
        StockItem product = new StockItem();
        product.setId(productId);
        product.setOrganization(manufacturer);
        product.setActive(false);
        product.setPublicationStatus(ProductPublicationStatus.DRAFT);

        when(organizationRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(stockItemRepository.findById(productId)).thenReturn(Optional.of(product));

        AuthenticatedUser actor = actor(distributorId, Organizationtype.DISTRIBUTOR);
        ProductAuthorizationService service = new ProductAuthorizationService(
                authorizationRepository, stockItemRepository, organizationRepository, userRepository);

        assertThatThrownBy(() -> service.request(actor, new CreateProductAuthorizationRequest(productId)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Authorization requests must target a published manufacturer product");
    }

    @Test
    void manufacturerCannotRevokePendingAuthorization() {
        UUID manufacturerId = UUID.randomUUID();
        UUID authorizationId = UUID.randomUUID();
        DistributorProductAuthorization authorization = new DistributorProductAuthorization();
        authorization.setStatus(ProductAuthorizationStatus.PENDING);
        when(authorizationRepository.findByIdAndManufacturerOrganizationForUpdate(
                authorizationId, manufacturerId)).thenReturn(Optional.of(authorization));

        ProductAuthorizationService service = new ProductAuthorizationService(
                authorizationRepository, stockItemRepository, organizationRepository, userRepository);

        assertThatThrownBy(() -> service.decide(
                actor(manufacturerId, Organizationtype.MANUFACTURER), authorizationId,
                new ProductAuthorizationDecisionRequest(ProductAuthorizationStatus.REVOKED, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("The authorization status transition is not allowed");
    }

    private static Organization organization(UUID id, Organizationtype type) {
        Organization organization = new Organization();
        organization.setId(id);
        organization.setType(type);
        return organization;
    }

    private static AuthenticatedUser actor(UUID organizationId, Organizationtype type) {
        return new AuthenticatedUser(UUID.randomUUID(),
                type == Organizationtype.DISTRIBUTOR
                        ? com.example.distrobackend.Domain.enums.UserRole.DISTRIBUTOR_ADMIN
                        : com.example.distrobackend.Domain.enums.UserRole.MANUFACTURER_ADMIN,
                organizationId, type, Instant.now().plusSeconds(60));
    }
}
