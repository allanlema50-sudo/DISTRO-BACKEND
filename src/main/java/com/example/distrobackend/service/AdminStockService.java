package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.dto.AdminStockResponse;
import com.example.distrobackend.repository.StockItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminStockService {

    private final StockItemRepository stockItemRepository;

    public List<AdminStockResponse> getAllStock() {
        return stockItemRepository.findAll()
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
