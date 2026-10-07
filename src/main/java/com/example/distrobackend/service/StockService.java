package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.entity.StockMovement;
import com.example.distrobackend.Domain.enums.StockMovementType;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.Exception.InsufficientStockException;
import com.example.distrobackend.dto.*;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.StockMovementRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<StockItemResponse> list(AuthenticatedUser actor, UUID organizationId, boolean activeOnly) {
        requireCatalogAccess(actor, organizationId);
        List<StockItem> items = activeOnly
                ? stockItemRepository.findByOrganizationIdAndActiveTrueOrderByNameAsc(organizationId)
                : stockItemRepository.findByOrganizationIdOrderByNameAsc(organizationId);
        return items.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public StockItemResponse get(AuthenticatedUser actor, UUID organizationId, UUID itemId) {
        requireCatalogAccess(actor, organizationId);
        return toResponse(stockItemRepository.findByIdAndOrganizationId(itemId, organizationId)
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND)));
    }

    @Transactional
    public StockItemResponse create(AuthenticatedUser actor, CreateStockItemRequest request) {
        UUID organizationId = requireOrganization(actor);
        String sku = request.sku().trim();
        if (stockItemRepository.existsByOrganizationIdAndSku(organizationId, sku)) {
            throw new ApiException(ErrorCode.CONFLICT, "SKU already exists in this organization");
        }

        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Organization not found"));
        StockItem item = new StockItem();
        item.setOrganization(organization);
        item.setSku(sku);
        item.setName(request.name().trim());
        item.setCategory(request.category());
        item.setUnitPrice(request.unitPrice());
        item.setQuantityOnHand(request.quantityOnHand());
        item.setReorderThreshold(request.reorderThreshold());
        item.setActive(true);
        StockItem saved = stockItemRepository.save(item);

        if (request.quantityOnHand() > 0) {
            saveMovement(saved, actor, request.quantityOnHand(), StockMovementType.RESTOCK_IN,
                    "INITIAL_STOCK", "Initial stock");
        }
        return toResponse(saved);
    }

    @Transactional
    public StockItemResponse update(AuthenticatedUser actor, UUID itemId, UpdateStockItemRequest request) {
        UUID organizationId = requireOrganization(actor);
        StockItem item = stockItemRepository.findByIdAndOrganizationIdForUpdate(itemId, organizationId)
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));
        item.setName(request.name().trim());
        item.setCategory(request.category());
        item.setUnitPrice(request.unitPrice());
        item.setReorderThreshold(request.reorderThreshold());
        item.setActive(request.active());
        return toResponse(stockItemRepository.save(item));
    }

    @Transactional
    public StockItemResponse adjust(AuthenticatedUser actor, UUID itemId, StockAdjustmentRequest request) {
        UUID organizationId = requireOrganization(actor);
        if (request.quantityDelta() == 0) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "quantityDelta must not be zero");
        }
        validateMovementDirection(request.quantityDelta(), request.movementType());
        StockItem item = stockItemRepository.findByIdAndOrganizationIdForUpdate(itemId, organizationId)
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));
        long resultingQuantity = (long) item.getQuantityOnHand() + request.quantityDelta();
        if (resultingQuantity < 0) {
            throw new InsufficientStockException("Stock quantity cannot become negative");
        }
        if (resultingQuantity > Integer.MAX_VALUE) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Stock quantity exceeds the supported maximum");
        }
        item.setQuantityOnHand((int) resultingQuantity);
        StockItem saved = stockItemRepository.save(item);
        saveMovement(saved, actor, request.quantityDelta(), request.movementType(),
                "MANUAL", request.note().trim());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<StockMovementResponse> movements(AuthenticatedUser actor, UUID itemId) {
        UUID organizationId = requireOrganization(actor);
        if (stockItemRepository.findByIdAndOrganizationId(itemId, organizationId).isEmpty()) {
            throw new ApiException(ErrorCode.STOCK_NOT_FOUND);
        }
        return stockMovementRepository
                .findByStockItemIdAndStockItemOrganizationIdOrderByCreatedAtDesc(itemId, organizationId)
                .stream().map(this::toMovementResponse).toList();
    }

    private UUID requireOrganization(AuthenticatedUser actor) {
        if (actor == null || actor.organizationId() == null
                || actor.role() == UserRole.CUSTOMER || actor.role() == UserRole.DRIVER) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "An organization staff identity is required");
        }
        return actor.organizationId();
    }

    private void requireCatalogAccess(AuthenticatedUser actor, UUID organizationId) {
        if (actor == null || organizationId == null) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        // Customers may browse a distributor catalog in order to place an
        // order. Every organization-bound identity remains tenant-scoped.
        if (actor.role() != UserRole.CUSTOMER
                && !organizationId.equals(actor.organizationId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED,
                    "You do not have access to this organization's catalog");
        }
    }

    private void saveMovement(StockItem item, AuthenticatedUser actor, int delta,
                              StockMovementType type, String referenceType, String note) {
        StockMovement movement = new StockMovement();
        movement.setStockItem(item);
        movement.setMovementType(type);
        movement.setQuantityDelta(delta);
        movement.setReferenceType(referenceType);
        movement.setPerformedBy(userRepository.getReferenceById(actor.userId()));
        movement.setNote(note);
        stockMovementRepository.save(movement);
    }

    private void validateMovementDirection(int delta, StockMovementType type) {
        if (type == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "movementType is required");
        }
        boolean invalid = switch (type) {
            case RESTOCK_IN, RETURN -> delta < 0;
            case SALE_OUT, DAMAGE -> delta > 0;
            case ADJUSTMENT -> false;
        };
        if (invalid) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "quantityDelta direction does not match movementType " + type);
        }
    }

    private StockItemResponse toResponse(StockItem item) {
        return new StockItemResponse(item.getId(), item.getOrganization().getId(), item.getSku(),
                item.getName(), item.getCategory(), item.getUnitPrice(), item.getQuantityOnHand(),
                item.getReorderThreshold(), item.isActive(), item.getCreatedAt(), item.getUpdatedAt());
    }

    private StockMovementResponse toMovementResponse(StockMovement movement) {
        return new StockMovementResponse(movement.getId(), movement.getStockItem().getId(),
                movement.getMovementType(), movement.getQuantityDelta(), movement.getReferenceType(),
                movement.getReferenceId(), movement.getPerformedBy() == null ? null : movement.getPerformedBy().getId(),
                movement.getNote(), movement.getCreatedAt());
    }
}
