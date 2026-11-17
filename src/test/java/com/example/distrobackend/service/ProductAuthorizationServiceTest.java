package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.DistributorProductAuthorization;
import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.ProductAuthorizationStatus;
import com.example.distrobackend.Domain.enums.ProductPublicationStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.dto.CreateProductAuthorizationRequest;
import com.example.distrobackend.dto.ProductAuthorizationDecisionRequest;
import com.example.distrobackend.dto.ProductAuthorizationResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @Mock
    private NotificationService notificationService;

    @Test
    void firstRequestSucceedsWhenNoAuthorizationExists() {
        UUID distributorId = UUID.randomUUID();
        UUID manufacturerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Organization distributor = organization(distributorId, Organizationtype.DISTRIBUTOR);
        StockItem product = publishedProduct(productId, organization(manufacturerId, Organizationtype.MANUFACTURER));
        when(organizationRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(stockItemRepository.findById(productId)).thenReturn(Optional.of(product));
        when(authorizationRepository.findByProduct_IdAndDistributorOrganization_Id(productId, distributorId))
                .thenReturn(Optional.empty());
        when(userRepository.getReferenceById(any())).thenReturn(new User());
        when(authorizationRepository.saveAndFlush(any(DistributorProductAuthorization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProductAuthorizationResponse response = service().request(
                actor(distributorId, Organizationtype.DISTRIBUTOR), new CreateProductAuthorizationRequest(productId));

        assertThat(response.status()).isEqualTo(ProductAuthorizationStatus.PENDING);
        assertThat(response.manufacturerOrganizationId()).isEqualTo(manufacturerId);
        assertThat(response.distributorOrganizationId()).isEqualTo(distributorId);
        verify(authorizationRepository).saveAndFlush(any(DistributorProductAuthorization.class));
    }

    @Test
    void duplicatePendingRequestIsRejected() {
        assertExistingStatusIsRejected(ProductAuthorizationStatus.PENDING);
    }

    @Test
    void alreadyApprovedDistributorCannotSubmitRedundantRequest() {
        assertExistingStatusIsRejected(ProductAuthorizationStatus.APPROVED);
    }

    @Test
    void anotherDistributorCanRequestTheSameProduct() {
        UUID distributorId = UUID.randomUUID();
        UUID otherDistributorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Organization distributor = organization(distributorId, Organizationtype.DISTRIBUTOR);
        Organization otherDistributor = organization(otherDistributorId, Organizationtype.DISTRIBUTOR);
        StockItem product = publishedProduct(productId,
                organization(UUID.randomUUID(), Organizationtype.MANUFACTURER));
        when(organizationRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(organizationRepository.findById(otherDistributorId)).thenReturn(Optional.of(otherDistributor));
        when(stockItemRepository.findById(productId)).thenReturn(Optional.of(product));
        when(authorizationRepository.findByProduct_IdAndDistributorOrganization_Id(productId, distributorId))
                .thenReturn(Optional.empty());
        when(authorizationRepository.findByProduct_IdAndDistributorOrganization_Id(productId, otherDistributorId))
                .thenReturn(Optional.empty());
        when(userRepository.getReferenceById(any())).thenReturn(new User());
        when(authorizationRepository.saveAndFlush(any(DistributorProductAuthorization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProductAuthorizationService service = service();
        ProductAuthorizationResponse response = service.request(
                actor(distributorId, Organizationtype.DISTRIBUTOR), new CreateProductAuthorizationRequest(productId));
        ProductAuthorizationResponse otherResponse = service.request(
                actor(otherDistributorId, Organizationtype.DISTRIBUTOR),
                new CreateProductAuthorizationRequest(productId));

        assertThat(response.status()).isEqualTo(ProductAuthorizationStatus.PENDING);
        assertThat(otherResponse.status()).isEqualTo(ProductAuthorizationStatus.PENDING);
        assertThat(otherResponse.distributorOrganizationId()).isEqualTo(otherDistributorId);
        verify(authorizationRepository).findByProduct_IdAndDistributorOrganization_Id(productId, distributorId);
        verify(authorizationRepository).findByProduct_IdAndDistributorOrganization_Id(productId, otherDistributorId);
    }

    @Test
    void previouslyRejectedRequestCanBeResubmitted() {
        UUID distributorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Organization distributor = organization(distributorId, Organizationtype.DISTRIBUTOR);
        StockItem product = publishedProduct(productId,
                organization(UUID.randomUUID(), Organizationtype.MANUFACTURER));
        DistributorProductAuthorization rejected = new DistributorProductAuthorization();
        rejected.setStatus(ProductAuthorizationStatus.REJECTED);
        when(organizationRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(stockItemRepository.findById(productId)).thenReturn(Optional.of(product));
        when(authorizationRepository.findByProduct_IdAndDistributorOrganization_Id(productId, distributorId))
                .thenReturn(Optional.of(rejected));
        when(userRepository.getReferenceById(any())).thenReturn(new User());
        when(authorizationRepository.saveAndFlush(any(DistributorProductAuthorization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProductAuthorizationResponse response = service().request(
                actor(distributorId, Organizationtype.DISTRIBUTOR), new CreateProductAuthorizationRequest(productId));

        assertThat(response.status()).isEqualTo(ProductAuthorizationStatus.PENDING);
        verify(authorizationRepository).saveAndFlush(rejected);
    }

    @Test
    void requestRejectsProductNotOwnedByManufacturer() {
        UUID distributorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Organization distributor = organization(distributorId, Organizationtype.DISTRIBUTOR);
        StockItem product = publishedProduct(productId, distributor);
        when(organizationRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(stockItemRepository.findById(productId)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service().request(actor(distributorId, Organizationtype.DISTRIBUTOR),
                new CreateProductAuthorizationRequest(productId)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Authorization requests must target a published manufacturer product");
        verify(authorizationRepository, never())
                .findByProduct_IdAndDistributorOrganization_Id(any(), any());
    }

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
        ProductAuthorizationService service = service();

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

        ProductAuthorizationService service = service();

        assertThatThrownBy(() -> service.decide(
                actor(manufacturerId, Organizationtype.MANUFACTURER), authorizationId,
                new ProductAuthorizationDecisionRequest(ProductAuthorizationStatus.REVOKED, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("The authorization status transition is not allowed");
    }

    @Test
    void decisionNoteCannotExceedStorageLimit() {
        UUID manufacturerId = UUID.randomUUID();
        ProductAuthorizationService service = service();

        assertThatThrownBy(() -> service.decide(
                actor(manufacturerId, Organizationtype.MANUFACTURER), UUID.randomUUID(),
                new ProductAuthorizationDecisionRequest(
                        ProductAuthorizationStatus.APPROVED, "x".repeat(1001))))
                .isInstanceOf(ApiException.class)
                .hasMessage("Authorization decision note must not exceed 1000 characters");
    }

    @Test
    void approvalRequiresWholesalePriceAndMinimumQuantityTogether() {
        UUID manufacturerId = UUID.randomUUID();
        ProductAuthorizationService service = service();

        assertThatThrownBy(() -> service.decide(
                actor(manufacturerId, Organizationtype.MANUFACTURER), UUID.randomUUID(),
                new ProductAuthorizationDecisionRequest(ProductAuthorizationStatus.APPROVED,
                        "Approved", java.math.BigDecimal.valueOf(25), null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Wholesale price and minimum order quantity must be supplied together");
    }

    private static Organization organization(UUID id, Organizationtype type) {
        Organization organization = new Organization();
        organization.setId(id);
        organization.setType(type);
        return organization;
    }

    private void assertExistingStatusIsRejected(ProductAuthorizationStatus status) {
        UUID distributorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        DistributorProductAuthorization existing = new DistributorProductAuthorization();
        existing.setStatus(status);
        when(organizationRepository.findById(distributorId)).thenReturn(Optional.of(
                organization(distributorId, Organizationtype.DISTRIBUTOR)));
        when(stockItemRepository.findById(productId)).thenReturn(Optional.of(publishedProduct(productId,
                organization(UUID.randomUUID(), Organizationtype.MANUFACTURER))));
        when(authorizationRepository.findByProduct_IdAndDistributorOrganization_Id(productId, distributorId))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service().request(actor(distributorId, Organizationtype.DISTRIBUTOR),
                new CreateProductAuthorizationRequest(productId)))
                .isInstanceOf(ApiException.class)
                .hasMessage("An active authorization request already exists for this product");
        verify(authorizationRepository, never()).saveAndFlush(any());
    }

    private ProductAuthorizationService service() {
        return new ProductAuthorizationService(authorizationRepository, stockItemRepository,
                organizationRepository, userRepository, notificationService);
    }

    private static StockItem publishedProduct(UUID productId, Organization manufacturer) {
        StockItem product = new StockItem();
        product.setId(productId);
        product.setOrganization(manufacturer);
        product.setActive(true);
        product.setPublicationStatus(ProductPublicationStatus.PUBLISHED);
        return product;
    }

    private static AuthenticatedUser actor(UUID organizationId, Organizationtype type) {
        return new AuthenticatedUser(UUID.randomUUID(),
                type == Organizationtype.DISTRIBUTOR
                        ? com.example.distrobackend.Domain.enums.UserRole.DISTRIBUTOR_ADMIN
                        : com.example.distrobackend.Domain.enums.UserRole.MANUFACTURER_ADMIN,
                organizationId, type, Instant.now().plusSeconds(60));
    }
}
