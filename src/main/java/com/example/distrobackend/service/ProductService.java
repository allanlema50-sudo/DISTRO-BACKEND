package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Product;
import com.example.distrobackend.Domain.enums.ProductStatus;
import com.example.distrobackend.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Page<Product> getProducts(
            int page,
            int size,
            String search
    ) {

        Pageable pageable = PageRequest.of(page, size);

        if (search != null && !search.trim().isEmpty()) {
            return productRepository
                    .findByNameContainingIgnoreCaseOrSkuContainingIgnoreCase(
                            search.trim(),
                            search.trim(),
                            pageable
                    );
        }

        return productRepository.findAll(pageable);
    }

    public Product getProduct(UUID id) {

        return productRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Product not found")
                );
    }

    public Product createProduct(Product product) {

        if (product.getStatus() == null) {
            product.setStatus(ProductStatus.ACTIVE);
        }

        return productRepository.save(product);
    }

    public Product updateProduct(
            UUID id,
            Product updatedProduct
    ) {

        Product existing = getProduct(id);

        existing.setName(updatedProduct.getName());
        existing.setSku(updatedProduct.getSku());
        existing.setCategory(updatedProduct.getCategory());
        existing.setOrganizationId(updatedProduct.getOrganizationId());
        existing.setOrganizationName(updatedProduct.getOrganizationName());
        existing.setDescription(updatedProduct.getDescription());
        existing.setUnitPrice(updatedProduct.getUnitPrice());
        existing.setStockQuantity(updatedProduct.getStockQuantity());

        if (updatedProduct.getStatus() != null) {
            existing.setStatus(updatedProduct.getStatus());
        }

        return productRepository.save(existing);
    }

    public Product updateProductStatus(
            UUID id,
            ProductStatus status
    ) {

        Product product = getProduct(id);

        product.setStatus(status);

        return productRepository.save(product);
    }

    public List<String> getCategories() {

        return productRepository.findAll()
                .stream()
                .map(Product::getCategory)
                .filter(category -> category != null && !category.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    public void deleteProduct(UUID id) {

        Product product = getProduct(id);

        productRepository.delete(product);
    }
}