package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.Product;
import com.example.distrobackend.Domain.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Page<Product> findByNameContainingIgnoreCaseOrSkuContainingIgnoreCase(
            String name,
            String sku,
            Pageable pageable
    );

    List<Product> findByCategoryIgnoreCase(String category);

    List<Product> findByStatus(ProductStatus status);
}