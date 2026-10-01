package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.StockItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface StockItemRepository extends JpaRepository<StockItem, UUID> {
}
