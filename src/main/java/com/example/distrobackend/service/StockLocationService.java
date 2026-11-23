package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.entity.StockLocation;
import com.example.distrobackend.Domain.entity.StockMovement;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.StockMovementType;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.PutAwayStockRequest;
import com.example.distrobackend.dto.StockLocationResponse;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.StockLocationRepository;
import com.example.distrobackend.repository.StockMovementRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockLocationService {
    private final StockItemRepository stockItemRepository;
    private final StockLocationRepository stockLocationRepository;
    private final StockMovementRepository stockMovementRepository;
    private final UserRepository userRepository;

    @Transactional
    public StockLocationResponse putAway(AuthenticatedUser actor, UUID stockItemId,
                                         PutAwayStockRequest request) {
        UUID organizationId = requireOrganization(actor);
        StockItem item = stockItemRepository.findByIdAndOrganizationIdForUpdateWithWarehouse(
                        stockItemId, organizationId)
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_ITEM_NOT_FOUND));
        if (item.getWarehouse() == null || !item.getWarehouse().isActive()) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Put-away is available only for stock assigned to an active organization warehouse");
        }
        String locationCode = request.locationCode().trim().toUpperCase(Locale.ROOT);
        String idempotencyKey = request.idempotencyKey() == null || request.idempotencyKey().isBlank()
                ? null : request.idempotencyKey().trim();
        if (idempotencyKey != null) {
            var prior = stockMovementRepository.findByStockItem_IdAndIdempotencyKey(stockItemId, idempotencyKey);
            if (prior.isPresent()) {
                StockMovement movement = prior.get();
                if (movement.getMovementType() != StockMovementType.PUT_AWAY
                        || !locationCode.equals(movement.getExternalReference())
                        || movement.getLocationQuantityDelta() == null
                        || movement.getLocationQuantityDelta() != request.quantity()) {
                    throw new ApiException(ErrorCode.CONFLICT,
                            "The idempotency key was already used for a different operation");
                }
                return stockLocationRepository.findByStockItem_IdAndStockItem_Organization_IdOrderByLocationCodeAsc(
                                stockItemId, organizationId).stream()
                        .filter(location -> locationCode.equals(location.getLocationCode()))
                        .findFirst().map(StockLocationResponseMapper::map)
                        .orElseThrow(() -> new ApiException(ErrorCode.CONFLICT,
                                "The prior put-away record has no matching storage location"));
            }
        }
        long assigned = stockLocationRepository.sumQuantityByStockItemId(stockItemId);
        if ((long) item.getQuantityOnHand() - assigned < request.quantity()) {
            throw new ApiException(ErrorCode.INVALID_STOCK_ADJUSTMENT,
                    "Put-away quantity exceeds unassigned on-hand stock");
        }
        StockLocation location = stockLocationRepository.findForUpdate(stockItemId, locationCode)
                .orElseGet(() -> {
                    StockLocation created = new StockLocation();
                    created.setStockItem(item);
                    created.setLocationCode(locationCode);
                    return created;
                });
        try {
            location.setQuantity(Math.addExact(location.getQuantity(), request.quantity()));
        } catch (ArithmeticException ex) {
            throw new ApiException(ErrorCode.INVALID_STOCK_ADJUSTMENT,
                    "Storage location quantity exceeds the supported range");
        }
        StockLocation saved = stockLocationRepository.save(location);
        StockMovement movement = new StockMovement();
        movement.setStockItem(item);
        movement.setMovementType(StockMovementType.PUT_AWAY);
        movement.setQuantityDelta(0);
        movement.setLocationQuantityDelta(request.quantity());
        movement.setExternalReference(locationCode);
        movement.setIdempotencyKey(idempotencyKey);
        movement.setReferenceType("WAREHOUSE_PUT_AWAY");
        movement.setPerformedBy(userRepository.getReferenceById(actor.userId()));
        movement.setNote("Put away into " + locationCode);
        stockMovementRepository.save(movement);
        return StockLocationResponseMapper.map(saved);
    }

    @Transactional(readOnly = true)
    public List<StockLocationResponse> list(AuthenticatedUser actor, UUID stockItemId) {
        UUID organizationId = requireOrganization(actor);
        if (stockItemRepository.findByIdAndOrganization_Id(stockItemId, organizationId).isEmpty()) {
            throw new ApiException(ErrorCode.STOCK_ITEM_NOT_FOUND);
        }
        return stockLocationRepository
                .findByStockItem_IdAndStockItem_Organization_IdOrderByLocationCodeAsc(
                        stockItemId, organizationId).stream()
                .map(StockLocationResponseMapper::map).toList();
    }

    @Transactional
    public void pickReservedQuantity(AuthenticatedUser actor, UUID stockItemId, int quantity,
                                     UUID purchaseOrderItemId, int targetPickedQuantity) {
        if (quantity == 0) return;
        UUID organizationId = requireOrganization(actor);
        StockItem item = stockItemRepository.findByIdAndOrganizationIdForUpdateWithWarehouse(
                        stockItemId, organizationId)
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_ITEM_NOT_FOUND));
        if (item.getOrganization().getType() != Organizationtype.MANUFACTURER
                || item.getSourceStockItem() != null || item.getWarehouse() == null
                || !item.getWarehouse().isActive()) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Picking requires manufacturer stock assigned to an active warehouse");
        }
        if (item.getReservedQuantity() < targetPickedQuantity) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Picked quantity exceeds stock reserved for this purchase order");
        }
        List<StockLocation> locations = stockLocationRepository.findAllForUpdate(stockItemId);
        int availableInLocations = locations.stream().mapToInt(StockLocation::getQuantity).sum();
        if (availableInLocations < quantity) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Warehouse locations do not contain enough assigned stock to pick this quantity");
        }
        int remaining = quantity;
        for (StockLocation location : locations) {
            if (remaining == 0) break;
            int moved = Math.min(remaining, location.getQuantity());
            location.setQuantity(location.getQuantity() - moved);
            remaining -= moved;
            StockMovement movement = new StockMovement();
            movement.setStockItem(item);
            movement.setMovementType(StockMovementType.PICK);
            movement.setQuantityDelta(0);
            movement.setLocationQuantityDelta(-moved);
            movement.setExternalReference(location.getLocationCode());
            movement.setIdempotencyKey("PO_PICK:" + purchaseOrderItemId + ":" + targetPickedQuantity
                    + ":" + location.getId());
            movement.setReferenceType("PURCHASE_ORDER_ITEM_PICK");
            movement.setReferenceId(purchaseOrderItemId);
            movement.setPerformedBy(userRepository.getReferenceById(actor.userId()));
            movement.setNote("Picked " + moved + " unit(s) from " + location.getLocationCode());
            stockMovementRepository.save(movement);
        }
    }

    private static UUID requireOrganization(AuthenticatedUser actor) {
        if (actor == null || actor.organizationId() == null
                || actor.organizationType() == null
                || !((actor.organizationType() == Organizationtype.DISTRIBUTOR
                    && (actor.role() == UserRole.DISTRIBUTOR_ADMIN || actor.role() == UserRole.DISTRIBUTOR_STAFF))
                    || (actor.organizationType() == Organizationtype.MANUFACTURER
                    && (actor.role() == UserRole.MANUFACTURER_ADMIN || actor.role() == UserRole.MANUFACTURER_STAFF)))) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return actor.organizationId();
    }

    private static final class StockLocationResponseMapper {
        private static StockLocationResponse map(StockLocation location) {
            return new StockLocationResponse(location.getId(), location.getStockItem().getId(),
                    location.getStockItem().getSku(), location.getLocationCode(), location.getQuantity());
        }
    }
}
