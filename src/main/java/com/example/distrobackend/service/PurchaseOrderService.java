package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.PurchaseOrder;
import com.example.distrobackend.Domain.entity.PurchaseOrderItem;
import com.example.distrobackend.Domain.entity.PurchaseOrderSettlement;
import com.example.distrobackend.Domain.enums.PurchaseOrderShipmentStatus;
import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.entity.StockMovement;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.PurchaseOrderStatus;
import com.example.distrobackend.Domain.enums.ProductPublicationStatus;
import com.example.distrobackend.Domain.enums.SettlementStatus;
import com.example.distrobackend.Domain.enums.StockMovementType;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Domain.enums.NotificationPriority;
import com.example.distrobackend.Domain.enums.NotificationType;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.CreatePurchaseOrderRequest;
import com.example.distrobackend.dto.PurchaseOrderDecisionRequest;
import com.example.distrobackend.dto.PurchaseOrderItemResponse;
import com.example.distrobackend.dto.PurchaseOrderResponse;
import com.example.distrobackend.dto.PurchaseOrderSettlementResponse;
import com.example.distrobackend.dto.PickPurchaseOrderItemRequest;
import com.example.distrobackend.dto.PackPurchaseOrderRequest;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.PurchaseOrderRepository;
import com.example.distrobackend.repository.PurchaseOrderSettlementRepository;
import com.example.distrobackend.repository.PurchaseOrderShipmentRepository;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.StockMovementRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.repository.DistributorProductAuthorizationRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderSettlementRepository settlementRepository;
    private final PurchaseOrderShipmentRepository purchaseOrderShipmentRepository;
    private final StockLocationService stockLocationService;
    private final OrganizationRepository organizationRepository;
    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ProductAuthorizationService productAuthorizationService;
    private final DistributorProductAuthorizationRepository authorizationRepository;
    private final com.example.distrobackend.repository.CheckoutPaymentRepository checkoutPaymentRepository;

    @Transactional
    public PurchaseOrderResponse create(AuthenticatedUser actor, CreatePurchaseOrderRequest request) {
        requireDistributor(actor);
        Organization buyer = organizationRepository.findById(actor.organizationId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Buyer organization not found"));
        Organization supplier = organizationRepository.findById(request.supplierOrganizationId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Supplier organization not found"));
        if (supplier.getType() != Organizationtype.MANUFACTURER) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Purchase orders must target a manufacturer");
        }
        if (buyer.getId().equals(supplier.getId())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Buyer and supplier organizations must differ");
        }

        Set<UUID> seenItems = new HashSet<>();
        PurchaseOrder purchaseOrder = new PurchaseOrder();
        purchaseOrder.setPurchaseOrderNumber(String.format("PO-%d", purchaseOrderRepository.getNextPurchaseOrderSequence()));
        purchaseOrder.setBuyerOrganization(buyer);
        purchaseOrder.setSupplierOrganization(supplier);
        purchaseOrder.setCreatedBy(userRepository.getReferenceById(actor.userId()));
        // Distribution authorization is the manufacturer's commercial approval.
        // A purchase created against that authorization is payable immediately.
        purchaseOrder.setStatus(PurchaseOrderStatus.APPROVED);
        purchaseOrder.setSubmittedAt(OffsetDateTime.now());

        BigDecimal total = BigDecimal.ZERO;
        for (var requestItem : request.items()) {
            if (!seenItems.add(requestItem.stockItemId())) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Each stock item may appear only once");
            }
            StockItem stockItem = stockItemRepository
                    .findByIdAndOrganizationIdForUpdate(requestItem.stockItemId(), supplier.getId())
                    .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND,
                            "Supplier stock item not found: " + requestItem.stockItemId()));
            if (!stockItem.isActive()
                    || stockItem.getPublicationStatus() != ProductPublicationStatus.PUBLISHED) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Supplier stock item is inactive: " + stockItem.getSku());
            }
            if (!productAuthorizationService.isApproved(stockItem.getId(), buyer.getId())) {
                throw new ApiException(ErrorCode.ACCESS_DENIED,
                        "The distributor is not authorized to purchase this manufacturer product");
            }

            var authorization = authorizationRepository
                    .findByProduct_IdAndDistributorOrganization_Id(stockItem.getId(), buyer.getId())
                    .orElseThrow(() -> new ApiException(ErrorCode.ACCESS_DENIED));
            if (authorization.getMinimumOrderQuantity() != null
                    && requestItem.quantity() < authorization.getMinimumOrderQuantity()) {
                throw new ApiException(ErrorCode.BAD_REQUEST,
                        "Quantity is below the agreed minimum order quantity for " + stockItem.getSku());
            }

            if (requestItem.quantity() > stockItem.getAvailableQuantity()) {
                throw new ApiException(ErrorCode.CONFLICT, "Insufficient available manufacturer stock for " + stockItem.getSku());
            }
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setStockItem(stockItem);
            item.setProductSkuSnapshot(stockItem.getSku());
            item.setProductNameSnapshot(stockItem.getName());
            if (requestItem.distributorOfferId() != null) {
                StockItem offer = stockItemRepository.findByIdAndOrganizationIdForUpdate(
                                requestItem.distributorOfferId(), buyer.getId())
                        .orElseThrow(() -> new ApiException(ErrorCode.STOCK_ITEM_NOT_FOUND,
                                "Distributor offer not found: " + requestItem.distributorOfferId()));
                validateOfferForSource(offer, stockItem, buyer);
                item.setDistributorOffer(offer);
            }
            item.setQuantity(requestItem.quantity());
            BigDecimal unitPrice = authorization.getAgreedWholesalePrice() != null
                    ? authorization.getAgreedWholesalePrice() : stockItem.getUnitPrice();
            item.setUnitPrice(unitPrice);
            purchaseOrder.addItem(item);
            total = total.add(unitPrice.multiply(BigDecimal.valueOf(requestItem.quantity())));
        }

        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Purchase order total must be positive");
        }
        purchaseOrder.setTotalAmount(total);
        purchaseOrder.setStockReserved(false);
        PurchaseOrder saved = purchaseOrderRepository.save(purchaseOrder);

        PurchaseOrderSettlement settlement = new PurchaseOrderSettlement();
        settlement.setPurchaseOrder(saved);
        settlement.setStatus(SettlementStatus.PENDING);
        settlement.setAmount(total);
        settlementRepository.saveAndFlush(settlement);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrderResponse> listForDistributor(AuthenticatedUser actor, Pageable pageable) {
        requireDistributor(actor);
        return purchaseOrderRepository.findByBuyerOrganizationId(actor.organizationId(), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrderResponse> listForManufacturer(AuthenticatedUser actor, Pageable pageable) {
        requireManufacturer(actor);
        return purchaseOrderRepository.findBySupplierOrganizationId(actor.organizationId(), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse getForDistributor(AuthenticatedUser actor, UUID id) {
        requireDistributor(actor);
        return toResponse(getAuthorized(id, actor.organizationId(), true));
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse getForManufacturer(AuthenticatedUser actor, UUID id) {
        requireManufacturer(actor);
        return toResponse(getAuthorized(id, actor.organizationId(), false));
    }

    @Transactional
    public PurchaseOrderResponse pickManufacturerItem(AuthenticatedUser actor, UUID purchaseOrderId,
                                                       PickPurchaseOrderItemRequest request) {
        requireManufacturer(actor);
        purchaseOrderRepository.findByIdForUpdate(purchaseOrderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Purchase order not found"));
        PurchaseOrder order = getAuthorized(purchaseOrderId, actor.organizationId(), false);
        requireFulfilmentEligible(order);
        PurchaseOrderItem line = order.getItems().stream()
                .filter(candidate -> candidate.getId().equals(request.purchaseOrderItemId()))
                .findFirst().orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Purchase-order item not found"));
        if (request.pickedQuantity() < line.getPickedQuantity()
                || request.pickedQuantity() > line.getQuantity()
                || request.pickedQuantity() < line.getPackedQuantity()) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Picked quantity must be between the packed quantity and ordered quantity");
        }
        int newlyPicked = request.pickedQuantity() - line.getPickedQuantity();
        stockLocationService.pickReservedQuantity(actor, line.getStockItem().getId(), newlyPicked,
                line.getId(), request.pickedQuantity());
        line.setPickedQuantity(request.pickedQuantity());
        order.setFulfilmentStatus(order.getItems().stream()
                .allMatch(item -> item.getPickedQuantity() == item.getQuantity())
                ? "READY_FOR_PACKING" : "PICKING");
        return toResponse(purchaseOrderRepository.save(order));
    }

    @Transactional
    public PurchaseOrderResponse packManufacturerOrder(AuthenticatedUser actor, UUID purchaseOrderId,
                                                        PackPurchaseOrderRequest request) {
        requireManufacturer(actor);
        purchaseOrderRepository.findByIdForUpdate(purchaseOrderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Purchase order not found"));
        PurchaseOrder order = getAuthorized(purchaseOrderId, actor.organizationId(), false);
        requireFulfilmentEligible(order);
        java.util.Map<UUID, PackPurchaseOrderRequest.Line> lines = new java.util.HashMap<>();
        for (PackPurchaseOrderRequest.Line line : request.lines()) {
            if (lines.putIfAbsent(line.purchaseOrderItemId(), line) != null) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Each purchase-order item must appear once");
            }
        }
        if (lines.size() != order.getItems().size()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Packing quantities are required for every order item");
        }
        for (PurchaseOrderItem item : order.getItems()) {
            PackPurchaseOrderRequest.Line line = lines.get(item.getId());
            if (line == null || item.getPickedQuantity() != item.getQuantity()
                    || line.packedQuantity() < item.getPackedQuantity()
                    || line.packedQuantity() > item.getPickedQuantity()) {
                throw new ApiException(ErrorCode.BAD_REQUEST,
                        "Every item must be fully picked and packed quantities cannot exceed picked quantities");
            }
            item.setPackedQuantity(line.packedQuantity());
        }
        order.setPackageCount(request.packageCount());
        PurchaseOrder saved = purchaseOrderRepository.save(order);
        if (dispatchReady(saved)) {
            saved.setFulfilmentStatus("READY_FOR_DISPATCH");
            purchaseOrderShipmentRepository.findByPurchaseOrderId(saved.getId()).ifPresent(shipment -> {
                if (shipment.getStatus() == PurchaseOrderShipmentStatus.AWAITING_PICKING) {
                    shipment.setStatus(PurchaseOrderShipmentStatus.READY_FOR_DISPATCH);
                }
            });
        } else {
            saved.setFulfilmentStatus("PACKING");
        }
        return toResponse(saved);
    }

    private void requireFulfilmentEligible(PurchaseOrder order) {
        if (order.getStatus() != PurchaseOrderStatus.APPROVED || !order.isStockReserved()) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only approved purchase orders with reserved stock can be picked or packed");
        }
        boolean paid;
        if (order.getCheckout() != null) {
            var payment = order.getCheckout().getPayment();
            paid = payment != null
                    && payment.getStatus() == com.example.distrobackend.Domain.enums.CheckoutPaymentStatus.SUCCESS;
        } else {
            paid = settlementRepository.findByPurchaseOrderIdOrderByCreatedAtDesc(order.getId()).stream()
                    .anyMatch(settlement -> settlement.getStatus() == SettlementStatus.SETTLED
                            && settlement.getAmount() != null
                            && settlement.getAmount().compareTo(order.getTotalAmount()) == 0
                            && order.getCurrency().equalsIgnoreCase(settlement.getCurrency()));
        }
        if (!paid) throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                "Payment must be verified before warehouse fulfilment");
    }

    private boolean dispatchReady(PurchaseOrder order) {
        return order.getPackageCount() > 0 && !order.getItems().isEmpty()
                && order.getItems().stream().allMatch(item -> item.getPickedQuantity() == item.getQuantity()
                && item.getPackedQuantity() == item.getQuantity());
    }

    @Transactional
    public PurchaseOrderResponse decide(AuthenticatedUser actor, UUID id,
                                        PurchaseOrderDecisionRequest request) {
        requireManufacturerAdmin(actor);
        PurchaseOrder purchaseOrder = getAuthorized(id, actor.organizationId(), false);
        if (purchaseOrder.getStatus() != PurchaseOrderStatus.SUBMITTED) {
            throw new ApiException(ErrorCode.CONFLICT, "Only submitted purchase orders can be reviewed");
        }
        if (request.status() != PurchaseOrderStatus.APPROVED
                && request.status() != PurchaseOrderStatus.REJECTED) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Supplier decision must be APPROVED or REJECTED");
        }
        String note = request.note() == null ? null : request.note().trim();
        if (request.status() == PurchaseOrderStatus.REJECTED && (note == null || note.isBlank())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "A rejection reason is required");
        }
        purchaseOrder.setStatus(request.status());
        if (request.status() == PurchaseOrderStatus.REJECTED) {
            releaseStockReservation(purchaseOrder);
        }
        purchaseOrder.setRejectionReason(request.status() == PurchaseOrderStatus.REJECTED ? note : null);
        purchaseOrder.setReviewedBy(userRepository.getReferenceById(actor.userId()));
        purchaseOrder.setReviewedAt(OffsetDateTime.now());
        PurchaseOrder saved = purchaseOrderRepository.save(purchaseOrder);
        notifyDistributorAdministrators(saved, request.status());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public java.util.List<PurchaseOrderSettlementResponse> settlements(AuthenticatedUser actor, UUID id) {
        PurchaseOrder purchaseOrder = getForEitherParty(actor, id);
        return settlementRepository.findByPurchaseOrderIdOrderByCreatedAtDesc(purchaseOrder.getId())
                .stream().map(this::toSettlementResponse).toList();
    }

    private PurchaseOrder getForEitherParty(AuthenticatedUser actor, UUID id) {
        if (actor == null || actor.organizationId() == null) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Purchase order not found"));
        boolean buyer = actor.role() != null && actor.role().organizationtype() == Organizationtype.DISTRIBUTOR
                && actor.organizationId().equals(purchaseOrder.getBuyerOrganization().getId());
        boolean supplier = actor.role() != null && actor.role().organizationtype() == Organizationtype.MANUFACTURER
                && actor.organizationId().equals(purchaseOrder.getSupplierOrganization().getId());
        if (!buyer && !supplier) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return purchaseOrder;
    }

    private PurchaseOrder getAuthorized(UUID id, UUID organizationId, boolean buyer) {
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Purchase order not found"));
        UUID ownerId = buyer ? purchaseOrder.getBuyerOrganization().getId()
                : purchaseOrder.getSupplierOrganization().getId();
        if (!organizationId.equals(ownerId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return purchaseOrder;
    }

    private void requireDistributor(AuthenticatedUser actor) {
        if (actor == null || actor.organizationId() == null
                || actor.role() == null || actor.role().organizationtype() != Organizationtype.DISTRIBUTOR) {
            throw new ApiException(ErrorCode.ACCESS_DENIED,
                    "A distributor organization identity is required");
        }
    }

    private void requireManufacturer(AuthenticatedUser actor) {
        if (actor == null || actor.organizationId() == null
                || actor.role() == null || actor.role().organizationtype() != Organizationtype.MANUFACTURER) {
            throw new ApiException(ErrorCode.ACCESS_DENIED,
                    "A manufacturer organization identity is required");
        }
    }

    private void requireManufacturerAdmin(AuthenticatedUser actor) {
        requireManufacturer(actor);
        if (actor.role() != UserRole.MANUFACTURER_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED,
                    "Only a manufacturer administrator may review purchase orders");
        }
    }

    private void releaseStockReservation(PurchaseOrder purchaseOrder) {
        if (!purchaseOrder.isStockReserved()) return;
        for (PurchaseOrderItem item : purchaseOrder.getItems()) {
            StockItem stockItem = stockItemRepository.findByIdAndOrganizationIdForUpdate(
                            item.getStockItem().getId(), purchaseOrder.getSupplierOrganization().getId())
                    .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));
            if (stockItem.getReservedQuantity() < item.getQuantity()) {
                throw new ApiException(ErrorCode.CONFLICT, "Purchase-order stock reservation is inconsistent");
            }
            stockItem.setReservedQuantity(stockItem.getReservedQuantity() - item.getQuantity());
            StockMovement movement = new StockMovement();
            movement.setStockItem(stockItem);
            movement.setMovementType(StockMovementType.RESERVATION_RELEASE);
            movement.setQuantityDelta(0);
            movement.setReservationQuantityDelta(-item.getQuantity());
            movement.setReferenceType("PURCHASE_ORDER_RESERVATION_RELEASE");
            movement.setReferenceId(purchaseOrder.getId());
            movement.setExternalReference(item.getId().toString());
            movement.setIdempotencyKey("PO_RELEASE:" + purchaseOrder.getId() + ":" + item.getId());
            movement.setNote("Released reservation for " + purchaseOrder.getPurchaseOrderNumber());
            stockMovementRepository.save(movement);
        }
        purchaseOrder.setStockReserved(false);
    }
    private PurchaseOrderResponse toResponse(PurchaseOrder purchaseOrder) {
        com.example.distrobackend.Domain.entity.CheckoutPayment checkoutPayment = purchaseOrder.getCheckout() == null ? null
                : purchaseOrder.getCheckout().getPayment();
        return new PurchaseOrderResponse(
                purchaseOrder.getId(), purchaseOrder.getPurchaseOrderNumber(),
                purchaseOrder.getBuyerOrganization().getId(), purchaseOrder.getSupplierOrganization().getId(),
                displayName(purchaseOrder.getBuyerOrganization()), displayName(purchaseOrder.getSupplierOrganization()),
                purchaseOrder.getStatus(), purchaseOrder.getTotalAmount(), purchaseOrder.getCurrency(),
                purchaseOrder.getRejectionReason(), purchaseOrder.getSubmittedAt(),
                purchaseOrder.getReviewedAt(), purchaseOrder.getFulfilledAt(),
                purchaseOrder.getItems().stream().map(item -> new PurchaseOrderItemResponse(
                        item.getId(), item.getStockItem().getId(),
                        item.getDistributorOffer() == null ? null : item.getDistributorOffer().getId(),
                        item.getProductSkuSnapshot() == null ? item.getStockItem().getSku() : item.getProductSkuSnapshot(),
                        item.getProductNameSnapshot() == null ? item.getStockItem().getName() : item.getProductNameSnapshot(), item.getQuantity(), item.getUnitPrice(),
                        item.getLineTotal() != null ? item.getLineTotal() : item.getUnitPrice()
                                .multiply(BigDecimal.valueOf(item.getQuantity())),
                        item.getPickedQuantity(), item.getPackedQuantity())).toList(),
                settlementRepository.findByPurchaseOrderIdOrderByCreatedAtDesc(purchaseOrder.getId())
                        .stream().map(this::toSettlementResponse).toList(),
                purchaseOrder.getCheckout() == null ? null : purchaseOrder.getCheckout().getId(),
                purchaseOrder.getCheckout() == null ? null : purchaseOrder.getCheckout().getCheckoutReference(),
                purchaseOrder.getDestinationWarehouse() == null ? null : purchaseOrder.getDestinationWarehouse().getId(),
                checkoutPayment == null ? (isPurchaseOrderPaid(purchaseOrder) ? "SUCCESS" : "PENDING")
                        : checkoutPayment.getStatus().name(),
                checkoutPayment != null && checkoutPayment.isDemo(),
                checkoutPayment == null ? null : checkoutPayment.getProviderReference(),
                checkoutPayment == null ? "PENDING" : checkoutPayment.getStatus().name(),
                checkoutPayment == null ? null : (checkoutPayment.isDemo() ? "DEMO" : checkoutPayment.getMethod().name()),
                checkoutPayment == null || checkoutPayment.getStatus() != com.example.distrobackend.Domain.enums.CheckoutPaymentStatus.SUCCESS
                        ? BigDecimal.ZERO : checkoutPayment.getAmount(),
                checkoutPayment == null ? null : checkoutPayment.getConfirmedAt(),
                fulfilmentStatus(purchaseOrder),
                purchaseOrder.getStatus() == PurchaseOrderStatus.APPROVED
                        && isPurchaseOrderPaid(purchaseOrder) && dispatchReady(purchaseOrder),
                purchaseOrder.getPackageCount());
    }

    private boolean isPurchaseOrderPaid(PurchaseOrder order) {
        if (order.getCheckout() != null) {
            var payment = order.getCheckout().getPayment();
            return payment != null
                    && payment.getStatus() == com.example.distrobackend.Domain.enums.CheckoutPaymentStatus.SUCCESS;
        }
        return settlementRepository.findByPurchaseOrderIdOrderByCreatedAtDesc(order.getId()).stream()
                .anyMatch(settlement -> settlement.getStatus() == SettlementStatus.SETTLED
                        && settlement.getAmount() != null
                        && settlement.getAmount().compareTo(order.getTotalAmount()) == 0);
    }

    private String fulfilmentStatus(PurchaseOrder order) {
        if (order.getFulfilmentStatus() != null && order.getFulfilmentStatus().endsWith("_EXCEPTION")) {
            return order.getFulfilmentStatus();
        }
        if (order.getStatus() == PurchaseOrderStatus.CANCELLED) return "CANCELLED";
        if (order.getStatus() == PurchaseOrderStatus.REJECTED) return "REJECTED";
        if (!isPurchaseOrderPaid(order)) return "PAYMENT_PENDING";
        if (!order.isStockReserved()) return "INVENTORY_EXCEPTION";
        if (dispatchReady(order)) return "READY_FOR_DISPATCH";
        if (order.getItems().stream().anyMatch(item -> item.getPickedQuantity() > 0)) return "PICKING";
        return "READY_FOR_PICKING";
    }

    private String displayName(Organization organization) {
        return organization == null || organization.getName() == null || organization.getName().isBlank()
                ? "Organization details unavailable" : organization.getName();
    }

    private void validateOfferForSource(StockItem offer, StockItem source, Organization buyer) {
        if (offer.getSourceStockItem() == null
                || !source.getId().equals(offer.getSourceStockItem().getId())
                || offer.getOrganization() == null
                || !buyer.getId().equals(offer.getOrganization().getId())
                || offer.getWarehouse() == null
                || offer.getWarehouse().getOrganization() == null
                || !buyer.getId().equals(offer.getWarehouse().getOrganization().getId())
                || !offer.isActive()
                || !offer.getWarehouse().isActive()) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "The selected distributor offer is not valid for this purchase order");
        }
    }

    private void notifyDistributorAdministrators(PurchaseOrder purchaseOrder,
                                                   PurchaseOrderStatus status) {
        String title = status == PurchaseOrderStatus.APPROVED
                ? "Purchase order approved" : "Purchase order rejected";
        String message = "Purchase order " + purchaseOrder.getPurchaseOrderNumber()
                + (status == PurchaseOrderStatus.APPROVED
                ? " was approved. Complete settlement before shipment can be dispatched."
                : " was rejected. Review the manufacturer's decision note.");
        NotificationPriority priority = status == PurchaseOrderStatus.APPROVED
                ? NotificationPriority.HIGH : NotificationPriority.MEDIUM;
        userRepository.findByOrganization_IdAndRole(
                        purchaseOrder.getBuyerOrganization().getId(), UserRole.DISTRIBUTOR_ADMIN,
                        org.springframework.data.domain.Pageable.unpaged())
                .forEach(user -> notificationService.create(user, title, message,
                        NotificationType.ORDER, priority, purchaseOrder.getId(), "PURCHASE_ORDER"));
    }

    private PurchaseOrderSettlementResponse toSettlementResponse(PurchaseOrderSettlement settlement) {
        return new PurchaseOrderSettlementResponse(settlement.getId(), settlement.getStatus(),
                settlement.getAmount(), settlement.getCurrency(), settlement.getProviderReference(),
                settlement.getNote(), settlement.getSettledAt(), settlement.getCreatedAt());
    }
}
