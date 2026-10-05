package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.RestockRequest;
import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.entity.StockMovement;
import com.example.distrobackend.Domain.enums.StockMovementType;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.RestockRequestStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.Exception.InsufficientStockException;
import com.example.distrobackend.Exception.ResourceNotFoundException;
import com.example.distrobackend.dto.CreateStockItemRequest;
import com.example.distrobackend.dto.ProductResponse;
import com.example.distrobackend.dto.StockAdjustmentRequest;
import com.example.distrobackend.dto.StockItemResponse;
import com.example.distrobackend.dto.StockMovementResponse;
import com.example.distrobackend.dto.CreateRestockRequest;
import com.example.distrobackend.dto.RestockRequestResponse;
import com.example.distrobackend.dto.UpdateRestockRequestStatus;
import com.example.distrobackend.dto.UpdateStockItemRequest;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.StockMovementRepository;
import com.example.distrobackend.repository.RestockRequestRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final RestockRequestRepository restockRequestRepository;

    @Transactional(readOnly = true)
    public Page<StockItemResponse> list(AuthenticatedUser user, Pageable pageable) {
        UUID organizationId = organizationId(user);
        return stockItemRepository.findByOrganization_Id(organizationId, pageable)
                .map(StockItemResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<StockItemResponse> listLowStock(AuthenticatedUser user, Pageable pageable) {
        UUID organizationId = organizationId(user);
        return stockItemRepository.findLowStockByOrganizationId(organizationId, pageable)
                .map(StockItemResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> catalog(String category, Pageable pageable) {
        Page<StockItem> items = category == null || category.isBlank()
                ? stockItemRepository.findByActiveTrue(pageable)
                : stockItemRepository.findByActiveTrueAndCategoryIgnoreCase(category.trim(), pageable);
        return items.map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public java.util.List<String> categories() {
        return stockItemRepository.findActiveCategories();
    }

    @Transactional(readOnly = true)
    public Page<StockItemResponse> availability(UUID organizationId, Pageable pageable) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND));
        if (organization.getType() != com.example.distrobackend.Domain.enums.Organizationtype.DISTRIBUTOR) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Availability is only supported for distributors");
        }
        return stockItemRepository.findByOrganization_IdAndActiveTrue(organizationId, pageable)
                .map(StockItemResponse::from);
    }

    @Transactional(readOnly = true)
    public StockItemResponse get(AuthenticatedUser user, UUID id) {
        return StockItemResponse.from(findItem(user, id));
    }

    @Transactional
    public StockItemResponse create(AuthenticatedUser user, CreateStockItemRequest request) {
        UUID organizationId = organizationId(user);
        String sku = normalizeSku(request.sku());
        if (stockItemRepository.existsByOrganization_IdAndSkuIgnoreCase(organizationId, sku)) {
            throw new ApiException(ErrorCode.DUPLICATE_SKU);
        }

        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCESS_DENIED));
        StockItem item = new StockItem();
        item.setOrganization(organization);
        item.setSku(sku);
        item.setName(request.name().trim());
        item.setCategory(trimToNull(request.category()));
        item.setUnitPrice(request.unitPrice());
        item.setQuantityOnHand(request.initialQuantity());
        item.setReorderThreshold(request.reorderThreshold());
        item.setActive(true);
        stockItemRepository.saveAndFlush(item);

        if (request.initialQuantity() > 0) {
            StockMovement openingMovement = new StockMovement();
            openingMovement.setStockItem(item);
            openingMovement.setMovementType(StockMovementType.RESTOCK_IN);
            openingMovement.setQuantityDelta(request.initialQuantity());
            openingMovement.setReferenceType("OPENING_BALANCE");
            openingMovement.setPerformedBy(userRepository.getReferenceById(user.userId()));
            openingMovement.setNote("Opening stock");
            stockMovementRepository.save(openingMovement);
        }
        return StockItemResponse.from(item);
    }

    @Transactional
    public StockItemResponse update(AuthenticatedUser user, UUID id, UpdateStockItemRequest request) {
        StockItem item = findItem(user, id);
        if (request.name() != null) {
            item.setName(request.name().trim());
        }
        if (request.category() != null) {
            item.setCategory(trimToNull(request.category()));
        }
        if (request.unitPrice() != null) {
            item.setUnitPrice(request.unitPrice());
        }
        if (request.reorderThreshold() != null) {
            item.setReorderThreshold(request.reorderThreshold());
        }
        if (request.active() != null) {
            item.setActive(request.active());
        }
        return StockItemResponse.from(stockItemRepository.save(item));
    }

    @Transactional
    public StockMovementResponse adjust(
            AuthenticatedUser user, UUID id, StockAdjustmentRequest request) {
        UUID organizationId = organizationId(user);
        StockItem item = stockItemRepository.findByIdAndOrganizationIdForUpdate(id, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_ITEM_NOT_FOUND));
        validateMovement(request.movementType(), request.quantityDelta());

        int newQuantity;
        try {
            newQuantity = Math.addExact(item.getQuantityOnHand(), request.quantityDelta());
        } catch (ArithmeticException ex) {
            throw new ApiException(ErrorCode.INVALID_STOCK_ADJUSTMENT);
        }
        if (newQuantity < 0) {
            throw new InsufficientStockException();
        }

        item.setQuantityOnHand(newQuantity);
        stockItemRepository.save(item);

        StockMovement movement = new StockMovement();
        movement.setStockItem(item);
        movement.setMovementType(request.movementType());
        movement.setQuantityDelta(request.quantityDelta());
        movement.setReferenceType(trimToNull(request.referenceType()));
        movement.setReferenceId(request.referenceId());
        movement.setPerformedBy(userRepository.getReferenceById(user.userId()));
        movement.setNote(trimToNull(request.note()));
        return StockMovementResponse.from(stockMovementRepository.saveAndFlush(movement));
    }

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> movements(
            AuthenticatedUser user, UUID id, Pageable pageable) {
        UUID organizationId = organizationId(user);
        findItem(user, id);
        return stockMovementRepository
                .findByStockItem_IdAndStockItem_Organization_IdOrderByCreatedAtDesc(id, organizationId, pageable)
                .map(StockMovementResponse::from);
    }

    @Transactional
    public RestockRequestResponse createRestockRequest(
            AuthenticatedUser user, CreateRestockRequest request) {
        Organization distributor = organization(user);
        if (distributor.getType() != Organizationtype.DISTRIBUTOR) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }

        StockItem stockItem = stockItemRepository.findByIdAndOrganization_Id(
                        request.stockItemId(), distributor.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_ITEM_NOT_FOUND));
        Organization manufacturer = organizationRepository.findById(request.manufacturerOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND));
        if (manufacturer.getType() != Organizationtype.MANUFACTURER) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Restock requests must target a manufacturer");
        }

        RestockRequest restockRequest = new RestockRequest();
        restockRequest.setDistributorOrganization(distributor);
        restockRequest.setManufacturerOrganization(manufacturer);
        restockRequest.setStockItem(stockItem);
        restockRequest.setRequestedQuantity(request.requestedQuantity());
        restockRequest.setRequestedBy(userRepository.getReferenceById(user.userId()));
        restockRequest.setNote(trimToNull(request.note()));
        return RestockRequestResponse.from(restockRequestRepository.saveAndFlush(restockRequest));
    }

    @Transactional(readOnly = true)
    public Page<RestockRequestResponse> restockRequests(
            AuthenticatedUser user, Pageable pageable) {
        UUID organizationId = organizationId(user);
        Page<RestockRequestResponse> requests;
        if (user.role().organizationtype() == Organizationtype.MANUFACTURER) {
            requests = restockRequestRepository
                    .findByManufacturerOrganization_IdOrderByCreatedAtDesc(organizationId, pageable)
                    .map(RestockRequestResponse::from);
        } else if (user.role().organizationtype() == Organizationtype.DISTRIBUTOR) {
            requests = restockRequestRepository
                    .findByDistributorOrganization_IdOrderByCreatedAtDesc(organizationId, pageable)
                    .map(RestockRequestResponse::from);
        } else {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return requests;
    }

    @Transactional
    public RestockRequestResponse updateRestockRequestStatus(
            AuthenticatedUser user, UUID id, UpdateRestockRequestStatus request) {
        UUID organizationId = organizationId(user);
        RestockRequest restockRequest;
        if (user.role().organizationtype() == Organizationtype.MANUFACTURER) {
            restockRequest = restockRequestRepository.findByIdAndManufacturerOrganization_Id(id, organizationId)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND));
            if (request.status() != RestockRequestStatus.APPROVED
                    && request.status() != RestockRequestStatus.REJECTED) {
                throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION);
            }
        } else if (user.role().organizationtype() == Organizationtype.DISTRIBUTOR) {
            restockRequest = restockRequestRepository.findByIdAndDistributorOrganization_Id(id, organizationId)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND));
            if (request.status() != RestockRequestStatus.CANCELLED) {
                throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION);
            }
        } else {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }

        if (restockRequest.getStatus() != RestockRequestStatus.PENDING) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION);
        }
        restockRequest.setStatus(request.status());
        if (request.note() != null) {
            restockRequest.setNote(trimToNull(request.note()));
        }
        return RestockRequestResponse.from(restockRequestRepository.save(restockRequest));
    }

    private Organization organization(AuthenticatedUser user) {
        UUID id = organizationId(user);
        return organizationRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCESS_DENIED));
    }

    private StockItem findItem(AuthenticatedUser user, UUID id) {
        return stockItemRepository.findByIdAndOrganization_Id(id, organizationId(user))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_ITEM_NOT_FOUND));
    }

    private static UUID organizationId(AuthenticatedUser user) {
        if (user == null || user.organizationId() == null) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return user.organizationId();
    }

    private static void validateMovement(StockMovementType type, Integer delta) {
        if (type == null || delta == null || delta == 0
                || (type == StockMovementType.RESTOCK_IN && delta < 0)
                || (type == StockMovementType.RETURN && delta < 0)
                || (type == StockMovementType.SALE_OUT && delta > 0)
                || (type == StockMovementType.DAMAGE && delta > 0)) {
            throw new ApiException(ErrorCode.INVALID_STOCK_ADJUSTMENT);
        }
    }

    private static String normalizeSku(String sku) {
        return sku.trim().toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
