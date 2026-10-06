package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.dto.AdminStockResponse;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import org.springframework.security.access.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminStockService {

    private final StockItemRepository stockItemRepository;

    public List<AdminStockResponse> getAllStock(AuthenticatedUser user) {
        List<StockItem> stockItems;
        if (user.role() == UserRole.PLATFORM_ADMIN || user.role() == UserRole.SUPER_ADMIN) {
            stockItems = stockItemRepository.findAll();
        } else {
            if (user.organizationId() == null) {
                throw new AccessDeniedException("User is not associated with an organization");
            }
            stockItems = stockItemRepository.findAllByOrganizationId(user.organizationId());
        }
        return stockItems
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AdminStockResponse toResponse(StockItem stockItem) {
        return new AdminStockResponse(
                stockItem.getId(),
                stockItem.getSku(),
                stockItem.getName(),
                stockItem.getCategory(),
                stockItem.getUnitPrice(),
                stockItem.getQuantityOnHand(),
                stockItem.getReorderThreshold(),
                stockItem.isActive(),
                stockItem.getCreatedAt(),
                stockItem.getUpdatedAt()
        );
    }
}
