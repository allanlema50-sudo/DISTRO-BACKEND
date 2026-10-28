package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.ProductImage;
import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.entity.StockMovement;
import com.example.distrobackend.Domain.enums.StockMovementType;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.ProductPublicationStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.Exception.ResourceNotFoundException;
import com.example.distrobackend.dto.CreateProductMultipartRequest;
import com.example.distrobackend.dto.ProductPublicationRequest;
import com.example.distrobackend.dto.ProductResponse;
import com.example.distrobackend.dto.UpdateProductMultipartRequest;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.ProductImageRepository;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.StockMovementRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductLifecycleService {

    private static final long DEFAULT_MAX_IMAGE_SIZE = 5L * 1024L * 1024L;
    private final StockItemRepository stockItemRepository;
    private final ProductImageRepository imageRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final StockMovementRepository stockMovementRepository;

    @Value("${app.product.image-max-size-bytes:5242880}")
    private long maxImageSize;

    @PostConstruct
    void validateImageSizeConfiguration() {
        if (maxImageSize <= 0 || maxImageSize > DEFAULT_MAX_IMAGE_SIZE) {
            throw new IllegalStateException(
                    "app.product.image-max-size-bytes must be between 1 and 5242880");
        }
    }

    @Transactional
    public ProductResponse create(AuthenticatedUser actor, CreateProductMultipartRequest request) {
        Organization manufacturer = manufacturer(actor);
        if (request.getPublicationStatus() != ProductPublicationStatus.DRAFT
                && actor.role() != com.example.distrobackend.Domain.enums.UserRole.MANUFACTURER_ADMIN) {
            throw new ApiException(ErrorCode.ROLE_NOT_ALLOWED,
                    "Only a manufacturer administrator may publish or discontinue products");
        }
        String sku = normalize(request.getSku());
        if (stockItemRepository.existsByOrganization_IdAndSkuIgnoreCase(manufacturer.getId(), sku)) {
            throw new ApiException(ErrorCode.DUPLICATE_SKU);
        }
        validateImage(request.getImage());
        StockItem item = new StockItem();
        item.setOrganization(manufacturer);
        item.setSku(sku);
        item.setName(request.getName().trim());
        item.setCategory(trimToNull(request.getCategory()));
        item.setBrand(trimToNull(request.getBrand()));
        item.setUnit(trimToNull(request.getUnit()));
        item.setDescription(trimToNull(request.getDescription()));
        item.setUnitPrice(request.getUnitPrice());
        item.setQuantityOnHand(request.getInitialQuantity());
        item.setReorderThreshold(request.getReorderThreshold());
        item.setPublicationStatus(request.getPublicationStatus());
        item.setActive(request.getPublicationStatus() == ProductPublicationStatus.PUBLISHED);
        stockItemRepository.saveAndFlush(item);
        saveImage(item, request.getImage());
        if (request.getInitialQuantity() > 0) {
            StockMovement movement = new StockMovement();
            movement.setStockItem(item);
            movement.setMovementType(StockMovementType.RESTOCK_IN);
            movement.setQuantityDelta(request.getInitialQuantity());
            movement.setReferenceType("OPENING_BALANCE");
            movement.setPerformedBy(userRepository.getReferenceById(actor.userId()));
            movement.setNote("Opening stock");
            stockMovementRepository.save(movement);
        }
        return ProductResponse.from(item);
    }

    @Transactional
    public ProductResponse update(AuthenticatedUser actor, UUID productId,
                                  UpdateProductMultipartRequest request) {
        Organization manufacturer = manufacturer(actor);
        if (request.isEmpty()) throw new ApiException(ErrorCode.BAD_REQUEST, "At least one product field is required");
        StockItem item = stockItemRepository.findByIdAndOrganizationIdForUpdate(productId, manufacturer.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_ITEM_NOT_FOUND));
        if (request.getName() != null) item.setName(requireText(request.getName(), "Product name"));
        if (request.getCategory() != null) item.setCategory(trimToNull(request.getCategory()));
        if (request.getBrand() != null) item.setBrand(trimToNull(request.getBrand()));
        if (request.getUnit() != null) item.setUnit(trimToNull(request.getUnit()));
        if (request.getDescription() != null) item.setDescription(trimToNull(request.getDescription()));
        if (request.getUnitPrice() != null) item.setUnitPrice(request.getUnitPrice());
        if (request.getSku() != null) {
            String sku = normalize(requireText(request.getSku(), "SKU"));
            if (!sku.equalsIgnoreCase(item.getSku())
                    && stockItemRepository.existsByOrganization_IdAndSkuIgnoreCase(manufacturer.getId(), sku)) {
                throw new ApiException(ErrorCode.DUPLICATE_SKU);
            }
            item.setSku(sku);
        }
        if (request.getImage() != null && !request.getImage().isEmpty()) {
            validateImage(request.getImage());
            saveImage(item, request.getImage());
        }
        return ProductResponse.from(stockItemRepository.save(item));
    }

    @Transactional
    public ProductResponse updatePublication(AuthenticatedUser actor, UUID productId,
                                              ProductPublicationRequest request) {
        Organization manufacturer = manufacturer(actor);
        StockItem item = stockItemRepository.findByIdAndOrganizationIdForUpdate(productId, manufacturer.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_ITEM_NOT_FOUND));
        ProductPublicationStatus status = switch (request.status()) {
            case DRAFT -> ProductPublicationStatus.DRAFT;
            case PUBLISHED -> ProductPublicationStatus.PUBLISHED;
            case DISCONTINUED -> ProductPublicationStatus.DISCONTINUED;
        };
        if (status != ProductPublicationStatus.DRAFT
                && actor.role() != com.example.distrobackend.Domain.enums.UserRole.MANUFACTURER_ADMIN) {
            throw new ApiException(ErrorCode.ROLE_NOT_ALLOWED,
                    "Only a manufacturer administrator may publish or discontinue products");
        }
        if (status == ProductPublicationStatus.PUBLISHED
                && !imageRepository.existsById(productId)) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "A product image is required before publishing");
        }
        item.setPublicationStatus(status);
        item.setActive(status == ProductPublicationStatus.PUBLISHED);
        return ProductResponse.from(stockItemRepository.save(item));
    }

    @Transactional(readOnly = true)
    public ImagePayload image(AuthenticatedUser actor, UUID productId) {
        StockItem item = stockItemRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_ITEM_NOT_FOUND));
        if (!visibleTo(actor, item)) throw new ApiException(ErrorCode.ACCESS_DENIED);
        StockItem imageOwner = item.getSourceStockItem() == null ? item : item.getSourceStockItem();
        ProductImage image = imageRepository.findById(imageOwner.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Product image not found"));
        return new ImagePayload(image.getContent(), image.getContentType());
    }

    private void saveImage(StockItem product, MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            ProductImage image = imageRepository.findById(product.getId()).orElseGet(ProductImage::new);
            image.setProductId(product.getId());
            image.setProduct(product);
            image.setContentType(detectType(bytes));
            image.setFileName(file.getOriginalFilename() == null ? null : file.getOriginalFilename().trim());
            image.setContent(bytes);
            image.setSizeBytes(bytes.length);
            imageRepository.save(image);
            product.setImageAvailable(true);
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Product image could not be read");
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Product image must be present and no larger than 5 MB");
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length == 0 || bytes.length > effectiveMaxImageSize()) {
                throw new ApiException(ErrorCode.BAD_REQUEST,
                        "Product image must be present and no larger than 5 MB");
            }
            detectType(bytes);
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Product image could not be read");
        }
    }

    private static String detectType(byte[] bytes) {
        if (bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50
                && bytes[2] == 0x4E && bytes[3] == 0x47 && bytes[4] == 0x0D
                && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A) return "image/png";
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF && hasJpegEndMarker(bytes)) return "image/jpeg";
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E'
                && bytes[10] == 'B' && bytes[11] == 'P') return "image/webp";
        throw new ApiException(ErrorCode.BAD_REQUEST, "Product image must be a PNG, JPEG, or WebP file");
    }

    private Organization manufacturer(AuthenticatedUser actor) {
        if (actor == null || actor.organizationId() == null
                || actor.organizationType() != Organizationtype.MANUFACTURER) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return organizationRepository.findById(actor.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Organization not found"));
    }

    private boolean visibleTo(AuthenticatedUser actor, StockItem item) {
        if (actor == null) return false;
        if (actor.role() == com.example.distrobackend.Domain.enums.UserRole.SUPER_ADMIN) return true;
        if (actor.role() != null && actor.role().name().startsWith("MANUFACTURER_")) {
            return actor.organizationId() != null && item.getOrganization() != null
                    && item.getOrganization().getType() == Organizationtype.MANUFACTURER
                    && actor.organizationId().equals(item.getOrganization().getId());
        }
        if (!isPublishedAndActive(item)) return false;
        if (actor.role() != null && actor.organizationId() != null
                && actor.role().name().startsWith("DISTRIBUTOR_")) {
            return (item.getOrganization() != null
                    && item.getOrganization().getType() == Organizationtype.MANUFACTURER)
                    || (item.getOrganization() != null
                    && actor.organizationId().equals(item.getOrganization().getId())
                    && item.getSourceStockItem() != null
                    && isPublishedAndActive(item.getSourceStockItem())
                    && item.getWarehouse() != null && item.getWarehouse().isActive());
        }
        return actor.role() != null && actor.role().name().equals("CUSTOMER")
                && item.getSourceStockItem() != null
                && item.getOrganization() != null
                && item.getOrganization().getType() == Organizationtype.DISTRIBUTOR
                && isPublishedAndActive(item.getSourceStockItem())
                && item.getWarehouse() != null && item.getWarehouse().isActive();
    }

    private boolean isPublishedAndActive(StockItem item) {
        return item != null && item.isActive()
                && item.getPublicationStatus() == ProductPublicationStatus.PUBLISHED;
    }

    private long effectiveMaxImageSize() {
        return maxImageSize <= 0 ? DEFAULT_MAX_IMAGE_SIZE : maxImageSize;
    }

    private static boolean hasJpegEndMarker(byte[] bytes) {
        for (int i = 2; i < bytes.length - 1; i++) {
            if ((bytes[i] & 0xFF) == 0xFF && (bytes[i + 1] & 0xFF) == 0xD9) return true;
        }
        return false;
    }

    private static String normalize(String value) { return value.trim().toUpperCase(Locale.ROOT); }
    private static String requireText(String value, String field) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) throw new ApiException(ErrorCode.BAD_REQUEST, field + " must not be blank");
        return trimmed;
    }
    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record ImagePayload(byte[] content, String contentType) { }
}
