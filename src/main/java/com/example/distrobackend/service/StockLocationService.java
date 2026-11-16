package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.entity.StockLocation;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.PutAwayStockRequest;
import com.example.distrobackend.dto.StockLocationResponse;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.StockLocationRepository;
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

    @Transactional
    public StockLocationResponse putAway(AuthenticatedUser actor, UUID stockItemId,
                                         PutAwayStockRequest request) {
        UUID organizationId = requireDistributor(actor);
        StockItem item = stockItemRepository.findByIdAndOrganizationIdForUpdateWithWarehouse(
                        stockItemId, organizationId)
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_ITEM_NOT_FOUND));
        if (item.getSourceStockItem() == null || item.getWarehouse() == null
                || !item.getWarehouse().isActive()) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Put-away is available only for active distributor stock in an active warehouse");
        }
        String locationCode = request.locationCode().trim().toUpperCase(Locale.ROOT);
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
        return StockLocationResponseMapper.map(stockLocationRepository.save(location));
    }

    @Transactional(readOnly = true)
    public List<StockLocationResponse> list(AuthenticatedUser actor, UUID stockItemId) {
        UUID organizationId = requireDistributor(actor);
        if (stockItemRepository.findByIdAndOrganization_Id(stockItemId, organizationId).isEmpty()) {
            throw new ApiException(ErrorCode.STOCK_ITEM_NOT_FOUND);
        }
        return stockLocationRepository
                .findByStockItem_IdAndStockItem_Organization_IdOrderByLocationCodeAsc(
                        stockItemId, organizationId).stream()
                .map(StockLocationResponseMapper::map).toList();
    }

    private static UUID requireDistributor(AuthenticatedUser actor) {
        if (actor == null || actor.organizationId() == null
                || actor.organizationType() != Organizationtype.DISTRIBUTOR
                || (actor.role() != UserRole.DISTRIBUTOR_ADMIN
                && actor.role() != UserRole.DISTRIBUTOR_STAFF)) {
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
