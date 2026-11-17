package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.ProductImage;
import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.ProductPublicationStatus;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.dto.CreateProductMultipartRequest;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.ProductImageRepository;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.StockMovementRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductLifecycleServiceTest {

    @Mock
    private StockItemRepository stockItemRepository;

    @Mock
    private ProductImageRepository imageRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @InjectMocks
    private ProductLifecycleService productLifecycleService;

    @Test
    void persistsNewImageThroughItsManagedProductWithoutManuallyAssigningMapsId() {
        UUID organizationId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Organization manufacturer = new Organization();
        manufacturer.setId(organizationId);
        manufacturer.setType(Organizationtype.MANUFACTURER);

        when(organizationRepository.findById(organizationId)).thenReturn(Optional.of(manufacturer));
        when(stockItemRepository.existsByOrganization_IdAndSkuIgnoreCase(organizationId, "SIMBA-332"))
                .thenReturn(false);
        when(stockItemRepository.saveAndFlush(any(StockItem.class))).thenAnswer(invocation -> {
            StockItem saved = invocation.getArgument(0);
            saved.setId(productId);
            saved.setOrganization(manufacturer);
            return saved;
        });
        when(imageRepository.findById(productId)).thenReturn(Optional.empty());
        when(imageRepository.saveAndFlush(any(ProductImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateProductMultipartRequest request = new CreateProductMultipartRequest();
        request.setName("Simba cement");
        request.setSku("simba-332");
        request.setUnitPrice(new BigDecimal("500.00"));
        request.setPublicationStatus(ProductPublicationStatus.DRAFT);
        request.setImage(new MockMultipartFile("image", "cement.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, (byte) 0xFF, (byte) 0xD9}));

        AuthenticatedUser actor = new AuthenticatedUser(UUID.randomUUID(), UserRole.MANUFACTURER_ADMIN,
                organizationId, Organizationtype.MANUFACTURER, Instant.now().plusSeconds(60));

        productLifecycleService.create(actor, request);

        ArgumentCaptor<StockItem> productCaptor = ArgumentCaptor.forClass(StockItem.class);
        verify(stockItemRepository).saveAndFlush(productCaptor.capture());
        ArgumentCaptor<ProductImage> imageCaptor = ArgumentCaptor.forClass(ProductImage.class);
        verify(imageRepository).saveAndFlush(imageCaptor.capture());
        assertThat(imageCaptor.getValue().getProduct()).isSameAs(productCaptor.getValue());
        assertThat(productCaptor.getValue().getId()).isEqualTo(productId);
        assertThat(imageCaptor.getValue().getProductId()).isNull();
    }
}
