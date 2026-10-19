package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.ProductStatsResponse;
import com.example.distrobackend.dto.StockItemResponse;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductCatalogService {

    private final StockItemRepository stockItemRepository;

    @Transactional(readOnly = true)
    public StockItemResponse getInventory(AuthenticatedUser actor, UUID productId) {
        if (actor == null || (actor.role() != UserRole.SUPER_ADMIN
                && actor.role() != UserRole.MANUFACTURER_ADMIN
                && actor.role() != UserRole.MANUFACTURER_STAFF)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        StockItem item = stockItemRepository.findById(productId)
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_ITEM_NOT_FOUND));
        if (actor.role() != UserRole.SUPER_ADMIN
                && (item.getOrganization() == null || actor.organizationId() == null
                || !actor.organizationId().equals(item.getOrganization().getId()))) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return StockItemResponse.from(item);
    }

    @Transactional(readOnly = true)
    public ProductStatsResponse stats(AuthenticatedUser actor) {
        if (actor == null || actor.role() == null) throw new ApiException(ErrorCode.UNAUTHENTICATED);
        if (actor.role() == UserRole.SUPER_ADMIN) {
            return new ProductStatsResponse(stockItemRepository.count(),
                    stockItemRepository.countByActiveTrue(), stockItemRepository.countByActiveFalse(),
                    stockItemRepository.countLowStock());
        }
        if (actor.organizationId() == null || (actor.role() != UserRole.MANUFACTURER_ADMIN
                && actor.role() != UserRole.MANUFACTURER_STAFF
                && actor.role() != UserRole.DISTRIBUTOR_ADMIN
                && actor.role() != UserRole.DISTRIBUTOR_STAFF)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        UUID organizationId = actor.organizationId();
        return new ProductStatsResponse(stockItemRepository.countByOrganization_Id(organizationId),
                stockItemRepository.countByOrganization_IdAndActiveTrue(organizationId),
                stockItemRepository.countByOrganization_IdAndActiveFalse(organizationId),
                stockItemRepository.countLowStockByOrganizationId(organizationId));
    }
}
