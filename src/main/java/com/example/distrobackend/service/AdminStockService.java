package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.entity.StockMovement;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.StockMovementType;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.AdminCreateStockItemRequest;
import com.example.distrobackend.dto.StockAdjustmentRequest;
import com.example.distrobackend.dto.StockItemResponse;
import com.example.distrobackend.dto.StockMovementResponse;
import com.example.distrobackend.dto.UpdateStockItemRequest;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.StockMovementRepository;
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
public class AdminStockService {

    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<StockItemResponse> list(AuthenticatedUser actor, UUID organizationId,
                                        Boolean active, Pageable pageable) {
        requirePlatformAdmin(actor);
        return stockItemRepository.findForPlatform(organizationId, active, pageable)
                .map(StockItemResponse::from);
    }

    @Transactional(readOnly = true)
    public StockItemResponse get(AuthenticatedUser actor, UUID inventoryId) {
        requirePlatformAdmin(actor);
        return stockItemRepository.findById(inventoryId)
                .map(StockItemResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_ITEM_NOT_FOUND));
    }

    @Transactional
    public StockItemResponse create(AuthenticatedUser actor, AdminCreateStockItemRequest request) {
        requirePlatformAdmin(actor);
        Organization organization = organizationRepository.findById(request.organizationId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Organization not found"));
        if (organization.getType() != Organizationtype.MANUFACTURER) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Platform stock creation supports manufacturer source inventory only");
        }
        String sku = normalizeSku(request.sku());
        if (stockItemRepository.existsByOrganization_IdAndSkuIgnoreCase(organization.getId(), sku)) {
            throw new ApiException(ErrorCode.DUPLICATE_SKU);
        }
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
            recordMovement(actor, item, StockMovementType.RESTOCK_IN, request.initialQuantity(),
                    "OPENING_BALANCE", null, "Opening stock");
        }
        return StockItemResponse.from(item);
    }

    @Transactional
    public StockItemResponse update(AuthenticatedUser actor, UUID inventoryId,
                                    UpdateStockItemRequest request) {
        requirePlatformAdmin(actor);
        if (request.name() == null && request.category() == null && request.unitPrice() == null
                && request.reorderThreshold() == null && request.active() == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "At least one stock field must be supplied");
        }
        StockItem item = stockItemRepository.findByIdForUpdate(inventoryId)
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_ITEM_NOT_FOUND));
        if (request.name() != null) item.setName(request.name().trim());
        if (request.category() != null) item.setCategory(trimToNull(request.category()));
        if (request.unitPrice() != null) item.setUnitPrice(request.unitPrice());
        if (request.reorderThreshold() != null) item.setReorderThreshold(request.reorderThreshold());
        if (request.active() != null) item.setActive(request.active());
        return StockItemResponse.from(stockItemRepository.save(item));
    }

    @Transactional(readOnly = true)
    public Page<StockItemResponse> alerts(AuthenticatedUser actor, UUID organizationId,
                                          Pageable pageable) {
        requirePlatformAdmin(actor);
        return stockItemRepository.findLowStockForPlatform(organizationId, pageable)
                .map(StockItemResponse::from);
    }

    @Transactional
    public StockMovementResponse adjust(AuthenticatedUser actor, UUID inventoryId,
                                        StockAdjustmentRequest request) {
        requirePlatformAdmin(actor);
        validateMovement(request.movementType(), request.quantityDelta());
        StockItem item = stockItemRepository.findByIdForUpdate(inventoryId)
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_ITEM_NOT_FOUND));
        int updatedQuantity;
        try {
            updatedQuantity = Math.addExact(item.getQuantityOnHand(), request.quantityDelta());
        } catch (ArithmeticException ex) {
            throw new ApiException(ErrorCode.INVALID_STOCK_ADJUSTMENT,
                    "The stock adjustment exceeds the supported quantity range");
        }
        if (updatedQuantity < item.getReservedQuantity()) {
            throw new ApiException(ErrorCode.INSUFFICIENT_STOCK,
                    "The adjustment would reduce stock below the reserved quantity");
        }
        item.setQuantityOnHand(updatedQuantity);
        stockItemRepository.save(item);
        return StockMovementResponse.from(recordMovement(actor, item, request.movementType(),
                request.quantityDelta(), request.referenceType(), request.referenceId(), request.note()));
    }

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> movements(AuthenticatedUser actor, UUID inventoryId,
                                                 Pageable pageable) {
        requirePlatformAdmin(actor);
        if (!stockItemRepository.existsById(inventoryId)) {
            throw new ApiException(ErrorCode.STOCK_ITEM_NOT_FOUND);
        }
        return stockMovementRepository.findByStockItem_IdOrderByCreatedAtDesc(inventoryId, pageable)
                .map(StockMovementResponse::from);
    }

    private StockMovement recordMovement(AuthenticatedUser actor, StockItem item,
                                         StockMovementType type, int delta, String referenceType,
                                         UUID referenceId, String note) {
        StockMovement movement = new StockMovement();
        movement.setStockItem(item);
        movement.setMovementType(type);
        movement.setQuantityDelta(delta);
        movement.setReferenceType(referenceType);
        movement.setReferenceId(referenceId);
        movement.setPerformedBy(userRepository.getReferenceById(actor.userId()));
        movement.setNote(note);
        return stockMovementRepository.save(movement);
    }

    private static void requirePlatformAdmin(AuthenticatedUser actor) {
        if (actor == null || actor.role() != UserRole.SUPER_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
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

    private static String normalizeSku(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
