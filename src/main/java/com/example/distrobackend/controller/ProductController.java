package com.example.distrobackend.controller;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.enums.ProductApprovalStatus;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.StockItemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@CrossOrigin(origins = "http://localhost:4200")
public class ProductController {

    private final StockItemRepository stockItemRepository;
    private final OrganizationRepository organizationRepository;

    public ProductController(
            StockItemRepository stockItemRepository,
            OrganizationRepository organizationRepository
    ) {
        this.stockItemRepository = stockItemRepository;
        this.organizationRepository = organizationRepository;
    }

    // GET /api/v1/products
    @GetMapping
    public ResponseEntity<List<StockItem>> getProducts() {
        return ResponseEntity.ok(stockItemRepository.findAll());
    }

    // GET /api/v1/products/{id}
    @GetMapping("/{id}")
    public ResponseEntity<StockItem> getProduct(@PathVariable UUID id) {
        return stockItemRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // POST /api/v1/products
    @PostMapping
    public ResponseEntity<StockItem> createProduct(
            @RequestBody StockItem product
    ) {
        applyOrganization(product);

        if (product.getApprovalStatus() == null) {
            product.setApprovalStatus(ProductApprovalStatus.PENDING);
        }

        // New products must be approved before becoming active.
        if (product.getApprovalStatus() != ProductApprovalStatus.APPROVED) {
            product.setActive(false);
        }

        StockItem savedProduct = stockItemRepository.save(product);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(savedProduct);
    }

    // PUT /api/v1/products/{id}
    @PutMapping("/{id}")
    public ResponseEntity<StockItem> updateProduct(
            @PathVariable UUID id,
            @RequestBody StockItem product
    ) {
        return stockItemRepository.findById(id)
                .map(existingProduct -> {

                    existingProduct.setSku(product.getSku());
                    existingProduct.setName(product.getName());
                    existingProduct.setCategory(product.getCategory());
                    existingProduct.setDescription(product.getDescription());
                    existingProduct.setUnitPrice(product.getUnitPrice());
                    existingProduct.setQuantityOnHand(product.getQuantityOnHand());
                    existingProduct.setReorderThreshold(product.getReorderThreshold());

                    if (product.getOrganizationId() != null
                            || (product.getOrganizationName() != null
                            && !product.getOrganizationName().isBlank())) {

                        applyOrganization(product);

                        existingProduct.setOrganizationId(
                                product.getOrganizationId()
                        );

                        existingProduct.setOrganizationName(
                                product.getOrganizationName()
                        );
                    }

                    if (product.getApprovalStatus() != null) {
                        existingProduct.setApprovalStatus(
                                product.getApprovalStatus()
                        );
                    }

                    existingProduct.setActive(product.isActive());

                    // Pending/rejected products cannot be active.
                    if (existingProduct.getApprovalStatus()
                            != ProductApprovalStatus.APPROVED) {

                        existingProduct.setActive(false);
                    }

                    StockItem updatedProduct =
                            stockItemRepository.save(existingProduct);

                    return ResponseEntity.ok(updatedProduct);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // PATCH /api/v1/products/{id}/status
    @PatchMapping("/{id}/status")
    public ResponseEntity<StockItem> updateProductStatus(
            @PathVariable UUID id,
            @RequestBody StatusRequest request
    ) {
        return stockItemRepository.findById(id)
                .map(product -> {

                    boolean active =
                            "ACTIVE".equalsIgnoreCase(request.status());

                    if (active
                            && product.getApprovalStatus()
                            != ProductApprovalStatus.APPROVED) {

                        throw new IllegalStateException(
                                "A product must be approved before it can be activated."
                        );
                    }

                    product.setActive(active);

                    StockItem updatedProduct =
                            stockItemRepository.save(product);

                    return ResponseEntity.ok(updatedProduct);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // PATCH /api/v1/products/{id}/approval
    @PatchMapping("/{id}/approval")
    public ResponseEntity<StockItem> updateProductApproval(
            @PathVariable UUID id,
            @RequestBody ApprovalRequest request
    ) {
        return stockItemRepository.findById(id)
                .map(product -> {

                    ProductApprovalStatus approvalStatus =
                            ProductApprovalStatus.valueOf(
                                    request.status().toUpperCase()
                            );

                    product.setApprovalStatus(approvalStatus);

                    // Pending/rejected products are not available.
                    if (approvalStatus != ProductApprovalStatus.APPROVED) {
                        product.setActive(false);
                    }

                    StockItem updatedProduct =
                            stockItemRepository.save(product);

                    return ResponseEntity.ok(updatedProduct);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // GET /api/v1/products/{id}/inventory
    @GetMapping("/{id}/inventory")
    public ResponseEntity<StockItem> getProductInventory(
            @PathVariable UUID id
    ) {
        return stockItemRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // GET /api/v1/products/categories
    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {

        List<String> categories = stockItemRepository.findAll()
                .stream()
                .map(StockItem::getCategory)
                .filter(category ->
                        category != null && !category.isBlank()
                )
                .distinct()
                .sorted()
                .toList();

        return ResponseEntity.ok(categories);
    }

    // GET /api/v1/products/stats
    @GetMapping("/stats")
    public ResponseEntity<ProductStatsResponse> getProductStats() {

        long totalProducts =
                stockItemRepository.count();

        long activeProducts =
                stockItemRepository.countByActiveTrue();

        long inactiveProducts =
                totalProducts - activeProducts;

        long pendingProducts =
                stockItemRepository.countByApprovalStatus(
                        ProductApprovalStatus.PENDING
                );

        long categories =
                stockItemRepository.findAll()
                        .stream()
                        .map(StockItem::getCategory)
                        .filter(category ->
                                category != null && !category.isBlank()
                        )
                        .distinct()
                        .count();

        return ResponseEntity.ok(
                new ProductStatsResponse(
                        totalProducts,
                        activeProducts,
                        inactiveProducts,
                        pendingProducts,
                        categories
                )
        );
    }

    /**
     * Connects a product to a real organization.
     *
     * The frontend sends organizationId.
     * We verify that the organization exists and then
     * store both its ID and name on the product.
     */
    private void applyOrganization(StockItem product) {

        if (product.getOrganizationId() != null) {

            Organization organization =
                    organizationRepository
                            .findById(product.getOrganizationId())
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Organization not found: "
                                                    + product.getOrganizationId()
                                    )
                            );

            product.setOrganizationName(
                    organization.getName()
            );

            return;
        }

        if (product.getOrganizationName() != null
                && !product.getOrganizationName().isBlank()) {

            Organization organization =
                    organizationRepository
                            .findByNameIgnoreCase(
                                    product.getOrganizationName().trim()
                            )
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Organization not found: "
                                                    + product.getOrganizationName()
                                    )
                            );

            product.setOrganizationId(
                    organization.getId()
            );

            product.setOrganizationName(
                    organization.getName()
            );
        }
    }

    public record StatusRequest(String status) {
    }

    public record ApprovalRequest(String status) {
    }

    public record ProductStatsResponse(
            long totalProducts,
            long activeProducts,
            long inactiveProducts,
            long pendingProducts,
            long categories
    ) {
    }
}