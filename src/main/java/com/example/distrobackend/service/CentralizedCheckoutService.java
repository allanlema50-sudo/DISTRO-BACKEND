package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.*;
import com.example.distrobackend.Domain.enums.*;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.*;
import com.example.distrobackend.repository.*;
import com.example.distrobackend.security.AuthenticatedUser;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CentralizedCheckoutService {
    private static final String DEMO_LABEL = "DEMO \u2014 NO MONEY TRANSFERRED";
    private final CheckoutRepository checkoutRepository;
    private final CheckoutPaymentRepository checkoutPaymentRepository;
    private final CheckoutItemRepository checkoutItemRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final SupplierSettlementRepository supplierSettlementRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final StockItemRepository stockItemRepository;
    private final WarehouseRepository warehouseRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final PurchaseOrderService purchaseOrderService;

    @Value("${logiflow.marketplace-checkout.enabled:false}") private boolean marketplaceCheckoutEnabled;
    @Value("${logiflow.demo-payments.enabled:false}") private boolean demoEnabled;
    @Value("${logiflow.demo-payments.allowed-organization-ids:}") private String demoOrganizationIds;
    @Value("${logiflow.demo-payments.outcome:SUCCESS}") private String demoOutcome;
    @Value("${logiflow.demo-payments.confirmation-delay-seconds:3}") private long confirmationDelaySeconds;
    @Value("${spring.profiles.active:local}") private String activeProfiles;

    @PostConstruct
    void validateDemoConfiguration() {
        if (demoEnabled && Arrays.stream(activeProfiles.split(",")).map(String::trim)
                .anyMatch(p -> !Set.of("local", "dev", "development", "staging", "test").contains(p.toLowerCase(Locale.ROOT)))) {
            throw new IllegalStateException("DEMO payments may only run in local, development, staging, or test profiles");
        }
        if (!Set.of("SUCCESS", "FAILED", "CANCELLED", "TIMED_OUT").contains(demoOutcome.toUpperCase(Locale.ROOT))) {
            throw new IllegalStateException("logiflow.demo-payments.outcome must be SUCCESS, FAILED, CANCELLED, or TIMED_OUT");
        }
        if (confirmationDelaySeconds < 0 || confirmationDelaySeconds > 300) {
            throw new IllegalStateException("DEMO payment confirmation delay must be between 0 and 300 seconds");
        }
    }

    @Transactional
    public CheckoutResponse create(AuthenticatedUser actor, CreateCheckoutRequest request, String idempotencyKey) {
        requireDistributor(actor);
        if (!marketplaceCheckoutEnabled) throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED, "Marketplace checkout is disabled");
        ensureDemoAuthorized(actor);
        String key = clean(idempotencyKey);
        if (key == null) throw new ApiException(ErrorCode.BAD_REQUEST, "Idempotency-Key header is required");
        if (key != null) {
            Checkout existing = checkoutRepository.findByBuyerOrganization_IdAndIdempotencyKey(actor.organizationId(), key).orElse(null);
            if (existing != null) return response(existing);
        }
        if (request.supplierOrders().size() < 2) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Centralized checkout requires products from at least two suppliers");
        }
        Set<UUID> suppliers = new HashSet<>();
        for (var supplierOrder : request.supplierOrders()) {
            if (!suppliers.add(supplierOrder.supplierOrganizationId())) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Use one supplier order group per supplier");
            }
            warehouseRepository.findByIdAndOrganization_IdAndActiveTrue(supplierOrder.destinationWarehouseId(), actor.organizationId())
                    .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST, "Destination warehouse must be active and owned by the distributor"));
        }

        Organization buyer = organizationRepository.findById(actor.organizationId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Buyer organization not found"));
        BigDecimal total = BigDecimal.ZERO;
        List<PurchaseOrder> orders = new ArrayList<>();
        for (var supplierOrder : request.supplierOrders()) {
            List<PurchaseOrderItemRequest> lines = supplierOrder.items().stream()
                    .map(item -> new PurchaseOrderItemRequest(item.stockItemId(), item.quantity(), item.distributorOfferId())).toList();
            PurchaseOrderResponse poResponse = purchaseOrderService.create(actor,
                    new CreatePurchaseOrderRequest(supplierOrder.supplierOrganizationId(), lines));
            PurchaseOrder order = purchaseOrderRepository.findByIdWithItems(poResponse.id())
                    .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "Created purchase order could not be loaded"));
            total = total.add(order.getTotalAmount());
            orders.add(order);
        }
        Checkout checkout = new Checkout();
        checkout.setCheckoutReference("CHK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT));
        checkout.setIdempotencyKey(key);
        checkout.setBuyerOrganization(buyer);
        checkout.setBuyerType("DISTRIBUTOR_ORGANIZATION");
        checkout.setCreatedBy(userRepository.getReferenceById(actor.userId()));
        checkout.setStatus(CheckoutStatus.PAYMENT_PENDING);
        checkout.setTotalAmount(total);
        checkout.setCurrency("KES");
        checkout = checkoutRepository.saveAndFlush(checkout);

        for (int i = 0; i < orders.size(); i++) {
            PurchaseOrder order = orders.get(i);
            var supplierRequest = request.supplierOrders().get(i);
            order.setCheckout(checkout);
            order.setDestinationWarehouse(warehouseRepository.findByIdAndOrganization_IdAndActiveTrue(
                    supplierRequest.destinationWarehouseId(), actor.organizationId()).orElseThrow());
            purchaseOrderRepository.save(order);
            Warehouse warehouse = warehouseRepository.findByIdAndOrganization_IdAndActiveTrue(
                    supplierRequest.destinationWarehouseId(), actor.organizationId()).orElseThrow();
            for (PurchaseOrderItem line : order.getItems()) {
                CheckoutItem item = new CheckoutItem();
                item.setCheckout(checkout);
                item.setPurchaseOrder(order);
                item.setSupplierOrganization(order.getSupplierOrganization());
                item.setProductId(line.getStockItem().getId());
                item.setSupplierNameSnapshot(safeName(order.getSupplierOrganization().getName()));
                item.setProductNameSnapshot(line.getStockItem().getName());
                item.setQuantity(line.getQuantity());
                item.setUnitPrice(line.getUnitPrice());
                item.setLineTotal(line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity())));
                item.setCurrency(order.getCurrency());
                item.setDestinationWarehouse(warehouse);
                checkoutItemRepository.save(item);
            }
        }
        return response(checkout);
    }

    @Transactional(readOnly = true)
    public Page<CheckoutResponse> history(AuthenticatedUser actor, Pageable pageable) {
        requireDistributor(actor);
        return checkoutRepository.findByBuyerOrganization_IdOrderByCreatedAtDesc(actor.organizationId(), pageable).map(this::response);
    }

    @Transactional(readOnly = true)
    public CheckoutResponse get(AuthenticatedUser actor, UUID checkoutId) {
        requireDistributor(actor);
        return response(checkoutRepository.findByIdAndBuyerOrganization_Id(checkoutId, actor.organizationId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Checkout not found")));
    }

    @Transactional
    public CheckoutResponse initiatePayment(AuthenticatedUser actor, UUID checkoutId, InitiateCheckoutPaymentRequest request,
                                            String idempotencyHeader) {
        requireDistributor(actor);
        ensureDemoAuthorized(actor);
        Checkout checkout = checkoutRepository.findByIdAndBuyerOrganization_Id(checkoutId, actor.organizationId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Checkout not found"));
        String method = request.method().trim().toUpperCase(Locale.ROOT);
        PaymentMethod paymentMethod = switch (method) {
            case "DEMO_MPESA" -> PaymentMethod.MPESA;
            case "DEMO_CARD" -> PaymentMethod.CARD;
            default -> throw new ApiException(ErrorCode.BAD_REQUEST, "method must be DEMO_MPESA or DEMO_CARD");
        };
        CheckoutPayment existing = checkoutPaymentRepository.findByCheckout_Id(checkoutId).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == CheckoutPaymentStatus.PENDING || existing.getStatus() == CheckoutPaymentStatus.SUCCESS) {
                return response(checkout);
            }
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT, "Checkout already has a terminal payment attempt");
        }
        if (checkout.getStatus() != CheckoutStatus.PAYMENT_PENDING) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT, "Checkout is not payable");
        }
        String idempotencyKey = clean(idempotencyHeader) != null ? clean(idempotencyHeader) : clean(request.idempotencyKey());
        CheckoutPayment payment = new CheckoutPayment();
        payment.setCheckout(checkout);
        payment.setMethod(paymentMethod);
        payment.setStatus(CheckoutPaymentStatus.PENDING);
        payment.setAmount(checkout.getTotalAmount());
        payment.setCurrency(checkout.getCurrency());
        payment.setDemo(true);
        payment.setDemoLabel(DEMO_LABEL);
        payment.setIdempotencyKey(idempotencyKey);
        payment.setProviderReference("DEMO-PENDING-" + UUID.randomUUID());
        payment.setInitiatedAt(OffsetDateTime.now());
        checkoutPaymentRepository.save(payment);
        checkout.setPayment(payment);
        return response(checkout);
    }

    
    @Transactional
    public CheckoutResponse cancel(AuthenticatedUser actor, UUID checkoutId) {
        requireDistributor(actor);
        Checkout checkout = checkoutRepository.findByIdAndBuyerOrganization_Id(checkoutId, actor.organizationId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Checkout not found"));
        if (checkout.getStatus() != CheckoutStatus.PAYMENT_PENDING)
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT, "Only an unpaid checkout can be cancelled");
        CheckoutPayment payment = checkoutPaymentRepository.findByCheckout_Id(checkoutId).orElse(null);
        if (payment != null) checkout.setPayment(payment);
        if (payment != null && payment.getStatus() != CheckoutPaymentStatus.PENDING)
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT, "Checkout payment is no longer pending");
        if (payment != null) {
            payment.setStatus(CheckoutPaymentStatus.CANCELLED);
            payment.setConfirmedAt(OffsetDateTime.now());
            payment.setFailureReason("Cancelled by buyer");
            checkoutPaymentRepository.save(payment);
        }
        cancelAndRelease(checkout);
        checkout.setStatus(CheckoutStatus.CANCELLED);
        checkoutRepository.save(checkout);
        return response(checkout);
    }
    public SupplierFinancialStatusResponse supplierFinancialStatus(AuthenticatedUser actor, UUID purchaseOrderId) {
        requireManufacturer(actor);
        PurchaseOrder order = purchaseOrderRepository.findByIdWithItems(purchaseOrderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Purchase order not found"));
        if (!order.getSupplierOrganization().getId().equals(actor.organizationId())) throw new ApiException(ErrorCode.ACCESS_DENIED);
        PaymentAllocation allocation = allocationRepository.findByPurchaseOrder_Id(purchaseOrderId).stream().findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Payment allocation not found"));
        SupplierSettlement settlement = supplierSettlementRepository.findByAllocation_Id(allocation.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Supplier settlement not found"));
        CheckoutPayment payment = allocation.getPayment();
        return new SupplierFinancialStatusResponse(order.getId(), payment.getCheckout().getCheckoutReference(), allocation.getId(),
                allocation.getGrossAmount(), allocation.getProviderFee(), allocation.getCommission(), allocation.getSupplierAmount(),
                allocation.getCurrency(), payment.getStatus().name(), settlement.getStatus().name(), payment.isDemo(),
                payment.getDemoLabel(), settlement.getNote());
    }

    @Transactional(readOnly = true)
    public CheckoutResponse.SupplierOrder manufacturerOrder(AuthenticatedUser actor, UUID purchaseOrderId) {
        requireManufacturer(actor);
        PurchaseOrder order = purchaseOrderRepository.findByIdWithItems(purchaseOrderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Purchase order not found"));
        if (!order.getSupplierOrganization().getId().equals(actor.organizationId())) throw new ApiException(ErrorCode.ACCESS_DENIED);
        return supplierOrder(order);
    }

    @Transactional(readOnly = true)
    public List<CheckoutResponse.Allocation> allocations(AuthenticatedUser actor, UUID checkoutId) {
        requireDistributor(actor);
        Checkout checkout = checkoutRepository.findByIdAndBuyerOrganization_Id(checkoutId, actor.organizationId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Checkout not found"));
        CheckoutPayment payment = checkout.getPayment();
        if (payment == null) return List.of();
        return allocationRepository.findByPayment_IdOrderByCreatedAtAsc(payment.getId()).stream().map(this::allocationResponse).toList();
    }

    @Scheduled(fixedDelayString = "${logiflow.demo-payments.poll-interval-ms:1000}")
    @Transactional
    public void simulatePendingDemoPayments() {
        if (!demoEnabled || Arrays.stream(activeProfiles.split(",")).map(String::trim)
                .anyMatch(p -> p.equalsIgnoreCase("prod") || p.equalsIgnoreCase("production"))) return;
        OffsetDateTime cutoff = OffsetDateTime.now().minusSeconds(confirmationDelaySeconds);
        for (CheckoutPayment payment : checkoutPaymentRepository.findByStatusAndInitiatedAtBefore(CheckoutPaymentStatus.PENDING, cutoff)) {
            Checkout checkout = payment.getCheckout();
            String outcome = demoOutcome.toUpperCase(Locale.ROOT);
            payment.setProviderReference("DEMO-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT));
            payment.setConfirmedAt(OffsetDateTime.now());
            CheckoutStatus checkoutStatus;
            switch (outcome) {
                case "SUCCESS" -> {
                    payment.setStatus(CheckoutPaymentStatus.SUCCESS);
                    checkout.setStatus(CheckoutStatus.PAID);
                    recordAllocations(payment);
                    checkoutStatus = CheckoutStatus.PAID;
                }
                case "FAILED" -> { payment.setStatus(CheckoutPaymentStatus.FAILED); checkoutStatus = CheckoutStatus.PAYMENT_FAILED; cancelAndRelease(checkout); }
                case "CANCELLED" -> { payment.setStatus(CheckoutPaymentStatus.CANCELLED); checkoutStatus = CheckoutStatus.CANCELLED; cancelAndRelease(checkout); }
                default -> { payment.setStatus(CheckoutPaymentStatus.TIMED_OUT); checkoutStatus = CheckoutStatus.UNKNOWN; cancelAndRelease(checkout); }
            }
            checkout.setStatus(checkoutStatus);
            checkoutPaymentRepository.save(payment);
            checkout.setPayment(payment);
            checkoutRepository.save(checkout);
        }
    }

    private void recordAllocations(CheckoutPayment payment) {
        for (PurchaseOrder order : purchaseOrderRepository.findByCheckout_Id(payment.getCheckout().getId())) {
            PaymentAllocation allocation = new PaymentAllocation();
            allocation.setPayment(payment);
            allocation.setPurchaseOrder(order);
            allocation.setSupplierOrganization(order.getSupplierOrganization());
            allocation.setGrossAmount(order.getTotalAmount());
            allocation.setProviderFee(BigDecimal.ZERO);
            allocation.setCommission(BigDecimal.ZERO);
            allocation.setSupplierAmount(order.getTotalAmount());
            allocation.setCurrency(order.getCurrency());
            allocation.setDemo(true);
            allocation = allocationRepository.save(allocation);
            SupplierSettlement settlement = new SupplierSettlement();
            settlement.setAllocation(allocation);
            settlement.setSupplierOrganization(order.getSupplierOrganization());
            settlement.setAmount(allocation.getSupplierAmount());
            settlement.setCurrency(allocation.getCurrency());
            settlement.setStatus(SupplierSettlementStatus.PENDING);
            settlement.setDemo(true);
            settlement.setNote("DEMO allocation only; no supplier payout was made");
            supplierSettlementRepository.save(settlement);
        }
    }

    private void cancelAndRelease(Checkout checkout) {
        for (PurchaseOrder order : purchaseOrderRepository.findByCheckout_Id(checkout.getId())) {
            if (order.isStockReserved()) {
                order.getItems().forEach(item -> {
                    StockItem stock = stockItemRepository.findByIdAndOrganizationIdForUpdate(
                            item.getStockItem().getId(), order.getSupplierOrganization().getId())
                            .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));
                    if (stock.getReservedQuantity() < item.getQuantity()) throw new ApiException(ErrorCode.CONFLICT, "Reserved stock is inconsistent during checkout cancellation");
                    stock.setReservedQuantity(stock.getReservedQuantity() - item.getQuantity());
                });
                order.setStockReserved(false);
            }
            order.setStatus(PurchaseOrderStatus.CANCELLED);
            purchaseOrderRepository.save(order);
        }
    }

    private CheckoutResponse response(Checkout checkout) {
        CheckoutPayment payment = checkout.getPayment();
        CheckoutResponse.Payment paymentResponse = payment == null ? null : new CheckoutResponse.Payment(
                payment.getId(), payment.isDemo() ? "DEMO_" + payment.getMethod().name() : payment.getMethod().name(),
                payment.getStatus().name(), payment.getProviderReference(), payment.isDemo(), payment.getDemoLabel(), payment.getInitiatedAt(), payment.getConfirmedAt());
        List<CheckoutResponse.SupplierOrder> orderResponses = purchaseOrderRepository.findByCheckout_Id(checkout.getId()).stream().map(this::supplierOrder).toList();
        List<CheckoutResponse.Allocation> allocations = payment == null ? List.of()
                : allocationRepository.findByPayment_IdOrderByCreatedAtAsc(payment.getId()).stream().map(this::allocationResponse).toList();
        return new CheckoutResponse(checkout.getId(), checkout.getCheckoutReference(), checkout.getStatus().name(),
                checkout.getTotalAmount(), checkout.getCurrency(), checkout.getBuyerOrganization().getId(),
                safeName(checkout.getBuyerOrganization().getName()), payment != null && payment.isDemo(),
                payment == null ? null : payment.getDemoLabel(), paymentResponse, orderResponses, allocations);
    }

    private CheckoutResponse.SupplierOrder supplierOrder(PurchaseOrder order) {
        List<CheckoutItem> lines = checkoutItemRepository.findByPurchaseOrder_Id(order.getId());
        UUID warehouseId = lines.stream().map(CheckoutItem::getDestinationWarehouse).filter(Objects::nonNull)
                .map(Warehouse::getId).findFirst().orElse(null);
        return new CheckoutResponse.SupplierOrder(order.getId(), order.getPurchaseOrderNumber(),
                order.getSupplierOrganization().getId(), safeName(order.getSupplierOrganization().getName()), warehouseId,
                order.getStatus().name(), order.getTotalAmount(), order.getCurrency(),
                lines.stream().map(line -> {
                    StockItem product = order.getItems().stream().map(PurchaseOrderItem::getStockItem)
                            .filter(candidate -> candidate.getId().equals(line.getProductId())).findFirst().orElse(null);
                    return new CheckoutResponse.Item(line.getProductId(), line.getProductNameSnapshot(),
                            product == null ? null : product.getSku(),
                            product == null ? null : com.example.distrobackend.dto.ProductResponse.from(product).imageUrl(),
                            line.getQuantity(), line.getUnitPrice(), line.getLineTotal());
                }).toList());
    }
    private CheckoutResponse.Allocation allocationResponse(PaymentAllocation allocation) {
        SupplierSettlement settlement = supplierSettlementRepository.findByAllocation_Id(allocation.getId()).orElse(null);
        return new CheckoutResponse.Allocation(allocation.getId(), allocation.getPurchaseOrder().getId(),
                allocation.getSupplierOrganization().getId(), safeName(allocation.getSupplierOrganization().getName()),
                allocation.getGrossAmount(), allocation.getProviderFee(), allocation.getCommission(), allocation.getSupplierAmount(),
                allocation.getCurrency(), allocation.isDemo(), settlement == null ? "PENDING" : settlement.getStatus().name(),
                settlement == null ? null : settlement.getNote());
    }

    private void ensureDemoAuthorized(AuthenticatedUser actor) {
        if (!demoEnabled) throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED, "DEMO checkout payment is disabled");
        Set<String> allowed = Arrays.stream(demoOrganizationIds.split(",")).map(String::trim).filter(v -> !v.isBlank()).collect(Collectors.toSet());
        if (!allowed.contains(actor.organizationId().toString())) throw new ApiException(ErrorCode.ACCESS_DENIED, "Organization is not enabled for DEMO payments");
        if (Arrays.stream(activeProfiles.split(",")).map(String::trim).anyMatch(p -> p.equalsIgnoreCase("prod") || p.equalsIgnoreCase("production"))) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED, "DEMO payments are forbidden in production");
        }
    }

    private void requireDistributor(AuthenticatedUser actor) {
        if (actor == null || actor.organizationId() == null || actor.role() == null || actor.role().organizationtype() != Organizationtype.DISTRIBUTOR)
            throw new ApiException(ErrorCode.ACCESS_DENIED, "A distributor organization identity is required");
    }
    private void requireManufacturer(AuthenticatedUser actor) {
        if (actor == null || actor.organizationId() == null || actor.role() == null || actor.role().organizationtype() != Organizationtype.MANUFACTURER)
            throw new ApiException(ErrorCode.ACCESS_DENIED, "A manufacturer organization identity is required");
    }
    private static String safeName(String name) { return name == null || name.isBlank() ? "Organization details unavailable" : name; }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
