package com.example.distrobackend.controller;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.enums.ProductApprovalStatus;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','SUPER_ADMIN','MANUFACTURER_ADMIN','MANUFACTURER_STAFF','DISTRIBUTOR_ADMIN','DISTRIBUTOR_STAFF')")
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
    public ResponseEntity<List<StockItem>> getProducts(
            @AuthenticationPrincipal AuthenticatedUser me
    ) {
        return ResponseEntity.ok(isPlatformAdmin(me)
                ? stockItemRepository.findAll()
                : stockItemRepository.findAllByOrganizationId(requireOrganizationId(me)));
    }

    // GET /api/v1/products/{id}
    @GetMapping("/{id}")
    public ResponseEntity<StockItem> getProduct(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser me
    ) {
        return findProduct(id, me)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // POST /api/v1/products
    @PostMapping
    public ResponseEntity<StockItem> createProduct(
            @RequestBody StockItem product,
            @AuthenticationPrincipal AuthenticatedUser me
    ) {
        applyOrganization(product, me);
        // Only the platform-admin approval endpoint can approve a new product.
        product.setApprovalStatus(ProductApprovalStatus.PENDING);
        product.setActive(false);

        StockItem savedProduct = stockItemRepository.save(product);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(savedProduct);
    }

    // PUT /api/v1/products/{id}
    @PutMapping("/{id}")
    public ResponseEntity<StockItem> updateProduct(
            @PathVariable UUID id,
            @RequestBody StockItem product,
            @AuthenticationPrincipal AuthenticatedUser me
    ) {
        return findProduct(id, me)
                .map(existingProduct -> {

                    existingProduct.setSku(product.getSku());
                    existingProduct.setName(product.getName());
                    existingProduct.setCategory(product.getCategory());
                    existingProduct.setDescription(product.getDescription());
                    existingProduct.setUnitPrice(product.getUnitPrice());
                    existingProduct.setQuantityOnHand(product.getQuantityOnHand());
                    existingProduct.setReorderThreshold(product.getReorderThreshold());

                    if (isPlatformAdmin(me)
                            && (product.getOrganizationId() != null
                            || (product.getOrganizationName() != null
                            && !product.getOrganizationName().isBlank()))) {
                        applyOrganization(product, me);
                        existingProduct.setOrganizationId(product.getOrganizationId());
                        existingProduct.setOrganizationName(product.getOrganizationName());
                    }

                    if (isPlatformAdmin(me) && product.getApprovalStatus() != null) {
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
            @RequestBody StatusRequest request,
            @AuthenticationPrincipal AuthenticatedUser me
    ) {
        return findProduct(id, me)
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
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','SUPER_ADMIN')")
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
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser me
    ) {
        return findProduct(id, me)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // GET /api/v1/products/categories
    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories(
            @AuthenticationPrincipal AuthenticatedUser me
    ) {

        List<String> categories = scopedProducts(me)
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
    public ResponseEntity<ProductStatsResponse> getProductStats(
            @AuthenticationPrincipal AuthenticatedUser me
    ) {

        List<StockItem> products = scopedProducts(me);
        long totalProducts = products.size();
        long activeProducts = products.stream().filter(StockItem::isActive).count();
        long inactiveProducts = totalProducts - activeProducts;
        long pendingProducts = products.stream()
                .filter(product -> product.getApprovalStatus() == ProductApprovalStatus.PENDING).count();
        long categories = products.stream()
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
    private void applyOrganization(StockItem product, AuthenticatedUser me) {

        if (!isPlatformAdmin(me)) {
            Organization organization = organizationRepository.findById(requireOrganizationId(me))
                    .orElseThrow(() -> new IllegalStateException("Your organization no longer exists"));
            product.setOrganizationId(organization.getId());
            product.setOrganizationName(organization.getName());
            return;
        }

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

    private java.util.Optional<StockItem> findProduct(UUID id, AuthenticatedUser me) {
        if (isPlatformAdmin(me)) {
            return stockItemRepository.findById(id);
        }
        return stockItemRepository.findByIdAndOrganizationId(id, requireOrganizationId(me));
    }

    private List<StockItem> scopedProducts(AuthenticatedUser me) {
        return isPlatformAdmin(me)
                ? stockItemRepository.findAll()
                : stockItemRepository.findAllByOrganizationId(requireOrganizationId(me));
    }

    private UUID requireOrganizationId(AuthenticatedUser me) {
        if (me.organizationId() == null) {
            throw new AccessDeniedException("This role must be associated with an organization");
        }
        return me.organizationId();
    }

    private boolean isPlatformAdmin(AuthenticatedUser me) {
        return me.role() == UserRole.PLATFORM_ADMIN || me.role() == UserRole.SUPER_ADMIN;
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
