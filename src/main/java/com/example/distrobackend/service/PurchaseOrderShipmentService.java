package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.CheckoutItem;
import com.example.distrobackend.Domain.entity.PurchaseOrder;
import com.example.distrobackend.Domain.entity.CheckoutPayment;
import com.example.distrobackend.Domain.entity.PurchaseOrderItem;
import com.example.distrobackend.Domain.entity.PurchaseOrderShipment;
import com.example.distrobackend.Domain.entity.PurchaseOrderShipmentReceiptLine;
import com.example.distrobackend.Domain.entity.PurchaseOrderShipmentGroup;
import com.example.distrobackend.Domain.entity.Trip;
import com.example.distrobackend.Domain.entity.TripStop;
import com.example.distrobackend.Domain.entity.TripStatusHistory;
import com.example.distrobackend.Domain.entity.PurchaseOrderSettlement;
import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.entity.StockMovement;
import com.example.distrobackend.Domain.entity.Warehouse;
import com.example.distrobackend.Domain.enums.NotificationPriority;
import com.example.distrobackend.Domain.enums.NotificationType;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.PurchaseOrderStatus;
import com.example.distrobackend.Domain.enums.SettlementStatus;
import com.example.distrobackend.Domain.enums.PurchaseOrderShipmentStatus;
import com.example.distrobackend.Domain.enums.ManufacturerShipmentStatus;
import com.example.distrobackend.Domain.enums.TripStatus;
import com.example.distrobackend.Domain.enums.StopStatus;
import com.example.distrobackend.Domain.enums.StockMovementType;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.Exception.ResourceNotFoundException;
import com.example.distrobackend.dto.CreateShipmentRequest;
import com.example.distrobackend.dto.PurchaseOrderShipmentResponse;
import com.example.distrobackend.dto.ReceiveShipmentRequest;
import com.example.distrobackend.dto.ShipmentStatusRequest;
import com.example.distrobackend.repository.PurchaseOrderRepository;
import com.example.distrobackend.repository.PurchaseOrderSettlementRepository;
import com.example.distrobackend.repository.PurchaseOrderShipmentRepository;
import com.example.distrobackend.repository.PurchaseOrderShipmentGroupRepository;
import com.example.distrobackend.repository.TripRepository;
import com.example.distrobackend.repository.TripStatusHistoryRepository;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.StockMovementRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.repository.WarehouseRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

import static org.springframework.data.domain.Pageable.unpaged;

@Service
@RequiredArgsConstructor
public class PurchaseOrderShipmentService {

    private final PurchaseOrderShipmentRepository shipmentRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderSettlementRepository settlementRepository;
    private final com.example.distrobackend.repository.CheckoutPaymentRepository checkoutPaymentRepository;
    private final com.example.distrobackend.repository.PaymentAllocationRepository paymentAllocationRepository;
    private final com.example.distrobackend.repository.CheckoutItemRepository checkoutItemRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final PurchaseOrderShipmentGroupRepository shipmentGroupRepository;
    private final TripRepository tripRepository;
    private final TripStatusHistoryRepository tripStatusHistoryRepository;

    /**
     * Creates the existing manufacturer shipment record once a centralized
     * checkout is authoritatively confirmed. The payment transition and all
     * manufacturer-specific ready shipments share the caller's transaction.
     */
    @Transactional
    public void markCheckoutOrdersReady(CheckoutPayment payment) {
        if (payment == null || payment.getStatus() != com.example.distrobackend.Domain.enums.CheckoutPaymentStatus.SUCCESS
                || payment.getCheckout() == null) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "Only a confirmed checkout payment can make supplier orders dispatchable");
        }
        List<PurchaseOrder> orders = purchaseOrderRepository.findByCheckout_Id(payment.getCheckout().getId());
        OffsetDateTime now = OffsetDateTime.now();
        for (PurchaseOrder listedOrder : orders) {
            purchaseOrderRepository.findByIdForUpdate(listedOrder.getId());
            PurchaseOrder order = purchaseOrderRepository.findByIdWithItems(listedOrder.getId()).orElse(listedOrder);
            if (order.getFulfilmentNextRecoveryAt() != null && order.getFulfilmentNextRecoveryAt().isAfter(now)) {
                continue;
            }
            if (order.getStatus() != PurchaseOrderStatus.APPROVED) {
                setFulfilmentException(order, "ORDER_NOT_APPROVED");
                continue;
            }
            PurchaseOrderShipment existing = shipmentRepository.findByPurchaseOrderId(order.getId()).orElse(null);
            if (existing != null && existing.getStatus() != PurchaseOrderShipmentStatus.CANCELLED) continue;
            var allocation = paymentAllocationRepository.findByPurchaseOrder_Id(order.getId()).stream()
                    .filter(candidate -> candidate.getPayment().getId().equals(payment.getId()))
                    .findFirst().orElse(null);
            if (allocation == null || allocation.getGrossAmount() == null
                    || allocation.getGrossAmount().compareTo(order.getTotalAmount()) != 0
                    || !allocation.getCurrency().equalsIgnoreCase(order.getCurrency())) {
                setFulfilmentException(order, "PAYMENT_ALLOCATION_EXCEPTION");
                continue;
            }
            Warehouse warehouse = order.getDestinationWarehouse();
            if (warehouse == null || !warehouse.isActive()
                    || !warehouse.getOrganization().getId().equals(order.getBuyerOrganization().getId())) {
                setFulfilmentException(order, "DESTINATION_WAREHOUSE_EXCEPTION");
                continue;
            }
            Warehouse origin = null;
            boolean originValid = true;
            for (PurchaseOrderItem item : order.getItems()) {
                StockItem stock = stockItemRepository.findByIdAndOrganizationIdForUpdate(
                                item.getStockItem().getId(), order.getSupplierOrganization().getId())
                        .orElse(null);
                if (stock == null || (order.isStockReserved()
                        ? stock.getReservedQuantity() < item.getQuantity()
                        : stock.getAvailableQuantity() < item.getQuantity())
                        || !stock.isActive() || stock.getWarehouse() == null || !stock.getWarehouse().isActive()
                        || !order.getSupplierOrganization().getId().equals(stock.getWarehouse().getOrganization().getId())) {
                    originValid = false;
                    break;
                }
                if (origin == null) origin = stock.getWarehouse();
                else if (!origin.getId().equals(stock.getWarehouse().getId())) {
                    originValid = false;
                    break;
                }
            }
            if (!originValid || origin == null) {
                setFulfilmentException(order, "ORIGIN_INVENTORY_EXCEPTION");
                continue;
            }
            try {
                for (PurchaseOrderItem item : order.getItems()) {
                    item.setDistributorOffer(resolveOffer(item, order.getBuyerOrganization(), warehouse));
                }
            } catch (ApiException ex) {
                setFulfilmentException(order, "DISTRIBUTOR_OFFER_EXCEPTION");
                continue;
            }
            if (!reserveStockAfterPayment(order)) {
                setFulfilmentException(order, "INVENTORY_EXCEPTION");
                continue;
            }
            PurchaseOrderShipment shipment = existing == null ? new PurchaseOrderShipment() : existing;
            shipment.setPurchaseOrder(order);
            shipment.setDestinationWarehouse(warehouse);
            shipment.setOriginWarehouse(origin);
            shipment.setStatus(PurchaseOrderShipmentStatus.AWAITING_PICKING);
            shipment.setCreatedBy(order.getCreatedBy());
            shipment.setLastUpdatedBy(null);
            shipment.setTrackingNumber(null);
            shipment.setCarrier(null);
            shipment.setDispatchNote(null);
            shipment.setManufacturerNote(null);
            shipment.setReceiptNote(null);
            shipment.setDispatchedAt(null);
            shipment.setDeliveredAt(null);
            shipment.setReceivedAt(null);
            PurchaseOrderShipment saved = shipmentRepository.save(shipment);
            order.setFulfilmentStatus("READY_FOR_PICKING");
            clearFulfilmentRecovery(order);
            purchaseOrderRepository.save(order);
            notifyOrganizationAdministrators(order.getSupplierOrganization(),
                    "Purchase order ready for picking", "Payment was confirmed for purchase order "
                            + order.getPurchaseOrderNumber() + ". The order is ready for warehouse picking.",
                    NotificationPriority.HIGH, saved.getId());
            notifyOrganizationAdministrators(order.getBuyerOrganization(),
                    "Purchase order ready for picking", "Purchase order "
                            + order.getPurchaseOrderNumber() + " is ready for warehouse picking.",
                    NotificationPriority.HIGH, saved.getId());
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPurchaseOrderPaymentVerified(PurchaseOrderPaymentVerifiedEvent event) {
        if (event.checkoutPaymentId() != null) {
            checkoutPaymentRepository.findById(event.checkoutPaymentId())
                    .filter(payment -> payment.getStatus()
                            == com.example.distrobackend.Domain.enums.CheckoutPaymentStatus.SUCCESS)
                    .ifPresent(this::markCheckoutOrdersReady);
        } else if (event.purchaseOrderId() != null) {
            handleSettledPurchaseOrder(event.purchaseOrderId());
        }
    }

    @Transactional
    public void handleSettledPurchaseOrder(UUID purchaseOrderId) {
        PurchaseOrder order = lockedPurchaseOrder(purchaseOrderId);
        if (order.getCheckout() != null) return;
        PurchaseOrderSettlement settlement = settlementRepository
                .findByPurchaseOrderIdOrderByCreatedAtDesc(order.getId()).stream()
                .findFirst().orElse(null);
        if (settlement == null || settlement.getStatus() != SettlementStatus.SETTLED
                || settlement.getAmount() == null
                || settlement.getAmount().compareTo(order.getTotalAmount()) != 0
                || !order.getCurrency().equalsIgnoreCase(settlement.getCurrency())) return;
        if (order.getStatus() != PurchaseOrderStatus.APPROVED) return;
        if (reserveStockAfterPayment(order)) {
            order.setFulfilmentStatus("READY_FOR_PICKING");
        } else {
            order.setFulfilmentStatus("INVENTORY_EXCEPTION");
        }
        purchaseOrderRepository.save(order);
    }

    private boolean reserveStockAfterPayment(PurchaseOrder order) {
        if (order.isStockReserved()) return true;
        if (order.getItems().isEmpty()) return false;
        List<StockItem> lockedItems = new ArrayList<>();
        UUID commonWarehouseId = null;
        for (PurchaseOrderItem line : order.getItems()) {
            StockItem stock = stockItemRepository.findByIdAndOrganizationIdForUpdate(
                            line.getStockItem().getId(), order.getSupplierOrganization().getId())
                    .orElse(null);
            if (stock == null || !stock.isActive() || stock.getAvailableQuantity() < line.getQuantity()
                    || stock.getWarehouse() == null || !stock.getWarehouse().isActive()
                    || !order.getSupplierOrganization().getId().equals(stock.getWarehouse().getOrganization().getId())
                    || (commonWarehouseId != null && !commonWarehouseId.equals(stock.getWarehouse().getId()))) {
                return false;
            }
            commonWarehouseId = stock.getWarehouse().getId();
            lockedItems.add(stock);
        }
        for (int index = 0; index < order.getItems().size(); index++) {
            PurchaseOrderItem line = order.getItems().get(index);
            StockItem stock = lockedItems.get(index);
            stock.setReservedQuantity(Math.addExact(stock.getReservedQuantity(), line.getQuantity()));
            StockMovement movement = new StockMovement();
            movement.setStockItem(stock);
            movement.setMovementType(StockMovementType.RESERVATION);
            movement.setQuantityDelta(0);
            movement.setReservationQuantityDelta(line.getQuantity());
            movement.setReferenceType("PURCHASE_ORDER_RESERVATION");
            movement.setReferenceId(order.getId());
            movement.setExternalReference(line.getId().toString());
            movement.setIdempotencyKey("PO_RESERVE:" + order.getId() + ":" + line.getId());
            movement.setPerformedBy(order.getCreatedBy());
            movement.setNote("Reserved stock for " + order.getPurchaseOrderNumber());
            stockMovementRepository.save(movement);
        }
        order.setStockReserved(true);
        return true;
    }

    @Transactional
    public void retryCheckoutFulfilment(AuthenticatedUser actor, UUID purchaseOrderId) {
        requireManufacturer(actor);
        PurchaseOrder order = lockedPurchaseOrder(purchaseOrderId);
        if (!actor.organizationId().equals(order.getSupplierOrganization().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        if (order.getCheckout() == null) {
            handleSettledPurchaseOrder(purchaseOrderId);
            return;
        }
        order.setFulfilmentNextRecoveryAt(null);
        if (order.getCheckout().getPayment() == null || order.getCheckout().getPayment().getStatus()
                != com.example.distrobackend.Domain.enums.CheckoutPaymentStatus.SUCCESS) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "Only a successfully paid purchase can retry fulfilment validation");
        }
        markCheckoutOrdersReady(order.getCheckout().getPayment());
    }

    private void setFulfilmentException(PurchaseOrder order, String status) {
        order.setFulfilmentStatus(status);
        scheduleFulfilmentRecovery(order, status);
        purchaseOrderRepository.save(order);
    }

    @Transactional
    public void retryPaidCheckoutFulfilment(UUID paymentId) {
        CheckoutPayment payment = checkoutPaymentRepository.findById(paymentId).orElse(null);
        if (payment == null || payment.getStatus() != com.example.distrobackend.Domain.enums.CheckoutPaymentStatus.SUCCESS
                || payment.getCheckout() == null) return;
        markCheckoutOrdersReady(payment);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCheckoutFulfilmentRecoveryFailure(UUID paymentId) {
        CheckoutPayment payment = checkoutPaymentRepository.findById(paymentId).orElse(null);
        if (payment == null || payment.getStatus() != com.example.distrobackend.Domain.enums.CheckoutPaymentStatus.SUCCESS
                || payment.getCheckout() == null) return;
        for (PurchaseOrder listedOrder : purchaseOrderRepository.findByCheckout_Id(payment.getCheckout().getId())) {
            PurchaseOrder order = purchaseOrderRepository.findByIdForUpdate(listedOrder.getId()).orElse(null);
            if (order == null || order.getStatus() != PurchaseOrderStatus.APPROVED
                    || shipmentRepository.findByPurchaseOrderId(order.getId())
                    .filter(shipment -> shipment.getStatus() != PurchaseOrderShipmentStatus.CANCELLED).isPresent()) {
                continue;
            }
            order.setFulfilmentStatus("FULFILMENT_RETRY_EXCEPTION");
            scheduleFulfilmentRecovery(order, "Automatic fulfilment preparation failed; see server logs");
            purchaseOrderRepository.save(order);
        }
    }

    private static void scheduleFulfilmentRecovery(PurchaseOrder order, String reason) {
        int attempts = order.getFulfilmentRecoveryAttempts() + 1;
        order.setFulfilmentRecoveryAttempts(attempts);
        long delaySeconds = Math.min(21_600L, 60L << Math.min(attempts - 1, 8));
        order.setFulfilmentNextRecoveryAt(OffsetDateTime.now().plusSeconds(delaySeconds));
        order.setFulfilmentLastRecoveryError(reason);
    }

    private static void clearFulfilmentRecovery(PurchaseOrder order) {
        order.setFulfilmentRecoveryAttempts(0);
        order.setFulfilmentNextRecoveryAt(null);
        order.setFulfilmentLastRecoveryError(null);
    }

    @Transactional
    public PurchaseOrderShipmentResponse create(AuthenticatedUser actor, CreateShipmentRequest request) {
        requireManufacturer(actor);
        PurchaseOrder purchaseOrder = lockedPurchaseOrder(request.purchaseOrderId());
        if (!actor.organizationId().equals(purchaseOrder.getSupplierOrganization().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        if (purchaseOrder.getStatus() != PurchaseOrderStatus.APPROVED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "A shipment can only be created for an approved purchase order");
        }
        PurchaseOrderShipment shipment = shipmentRepository.findByPurchaseOrderId(purchaseOrder.getId())
                .orElse(null);
        if (shipment != null && shipment.getStatus() != PurchaseOrderShipmentStatus.CANCELLED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "A shipment already exists for this purchase order");
        }
        if (!isPickedAndPacked(purchaseOrder)) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "The purchase order must be fully picked and packed before shipment creation");
        }
        Warehouse origin = resolveOriginWarehouse(purchaseOrder);
        if (!purchaseOrder.isStockReserved()) {
            throw new ApiException(ErrorCode.CONFLICT, "The purchase order has no active stock reservation");
        }
        if (purchaseOrder.getCheckout() == null) {
            PurchaseOrderSettlement settlement = settlementRepository
                    .findByPurchaseOrderIdOrderByCreatedAtDesc(purchaseOrder.getId()).stream()
                    .findFirst().orElseThrow(() -> new ApiException(ErrorCode.CONFLICT,
                            "The purchase order has no settlement record"));
            if (settlement.getStatus() != SettlementStatus.SETTLED || settlement.getAmount() == null
                    || settlement.getAmount().compareTo(purchaseOrder.getTotalAmount()) != 0
                    || !purchaseOrder.getCurrency().equalsIgnoreCase(settlement.getCurrency())) {
                throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                        "The purchase order must be settled for the exact amount before dispatch");
            }
        } else {
            var payment = checkoutPaymentRepository.findFirstByCheckout_IdOrderByInitiatedAtDesc(purchaseOrder.getCheckout().getId())
                    .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT, "Checkout payment is not confirmed"));
            if (payment.getStatus() != com.example.distrobackend.Domain.enums.CheckoutPaymentStatus.SUCCESS
                    || payment.getAmount().compareTo(purchaseOrder.getCheckout().getTotalAmount()) != 0
                    || !payment.getCurrency().equalsIgnoreCase(purchaseOrder.getCheckout().getCurrency())) {
                throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT, "Checkout payment is not confirmed for the full amount");
            }
            var allocation = paymentAllocationRepository.findByPurchaseOrder_Id(purchaseOrder.getId()).stream()
                    .filter(candidate -> candidate.getPayment().getId().equals(payment.getId())).findFirst()
                    .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT, "Supplier order has no checkout allocation"));
            if (allocation.getGrossAmount().compareTo(purchaseOrder.getTotalAmount()) != 0
                    || !allocation.getCurrency().equalsIgnoreCase(purchaseOrder.getCurrency())) {
                throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT, "Supplier allocation does not match this purchase order");
            }
            UUID capturedWarehouse = checkoutItemRepository.findByPurchaseOrder_Id(purchaseOrder.getId()).stream()
                    .map(CheckoutItem::getDestinationWarehouse).filter(java.util.Objects::nonNull)
                    .map(Warehouse::getId).findFirst().orElse(null);
            if (capturedWarehouse == null || !capturedWarehouse.equals(request.destinationWarehouseId())
                    || purchaseOrder.getDestinationWarehouse() == null
                    || !capturedWarehouse.equals(purchaseOrder.getDestinationWarehouse().getId())) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Shipment destination must match the warehouse selected at checkout");
            }
        }

        Warehouse warehouse = warehouseRepository.findByIdAndOrganization_IdForUpdate(
                        request.destinationWarehouseId(), purchaseOrder.getBuyerOrganization().getId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND,
                        "Destination warehouse not found for the distributor"));
        if (!warehouse.isActive()) {
            throw new ApiException(ErrorCode.CONFLICT, "The destination warehouse is inactive");
        }

        // Select and lock every destination offer before creating the shipment.
        // This makes receipt deterministic even when a distributor has several offers.
        for (PurchaseOrderItem item : purchaseOrder.getItems()) {
            StockItem offer = resolveOffer(item, purchaseOrder.getBuyerOrganization(), warehouse);
            item.setDistributorOffer(offer);
        }

        if (shipment == null) {
            shipment = new PurchaseOrderShipment();
            shipment.setPurchaseOrder(purchaseOrder);
            shipment.setCreatedBy(userRepository.getReferenceById(actor.userId()));
        }
        shipment.setDestinationWarehouse(warehouse);
        shipment.setOriginWarehouse(origin);
        shipment.setStatus(PurchaseOrderShipmentStatus.READY_FOR_DISPATCH);
        shipment.setTrackingNumber(trimToNull(request.trackingNumber()));
        shipment.setCarrier(trimToNull(request.carrier()));
        shipment.setDispatchNote(null);
        shipment.setManufacturerNote(null);
        shipment.setReceiptNote(null);
        shipment.setDispatchedAt(null);
        shipment.setDeliveredAt(null);
        shipment.setReceivedAt(null);
        shipment.setLastUpdatedBy(userRepository.getReferenceById(actor.userId()));
        PurchaseOrderShipment saved = shipmentRepository.saveAndFlush(shipment);
        purchaseOrder.setFulfilmentStatus("READY_FOR_DISPATCH");
        purchaseOrderRepository.save(purchaseOrder);
        notifyOrganizationAdministrators(purchaseOrder.getBuyerOrganization(),
                "Shipment ready for dispatch", "Shipment for purchase order "
                        + purchaseOrder.getPurchaseOrderNumber()
                        + " is ready for dispatch after settlement.",
                NotificationPriority.HIGH, saved.getId());
        return PurchaseOrderShipmentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrderShipmentResponse> listForManufacturer(AuthenticatedUser actor, Pageable pageable) {
        requireManufacturer(actor);
        return shipmentRepository
                .findByPurchaseOrder_SupplierOrganization_IdOrderByCreatedAtDesc(actor.organizationId(), pageable)
                .map(PurchaseOrderShipmentResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrderShipmentResponse> listForDistributor(AuthenticatedUser actor, Pageable pageable) {
        requireDistributor(actor);
        return shipmentRepository
                .findByPurchaseOrder_BuyerOrganization_IdOrderByCreatedAtDesc(actor.organizationId(), pageable)
                .map(PurchaseOrderShipmentResponse::from);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderShipmentResponse get(AuthenticatedUser actor, UUID shipmentId) {
        PurchaseOrderShipment shipment = findAuthorized(actor, shipmentId);
        return PurchaseOrderShipmentResponse.from(shipment);
    }

    @Transactional
    public PurchaseOrderShipmentResponse updateStatus(AuthenticatedUser actor, UUID shipmentId,
                                         ShipmentStatusRequest request) {
        requireManufacturer(actor);
        PurchaseOrderShipment shipment = shipmentRepository.findByIdWithDetailsForUpdate(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Shipment not found"));
        if (!actor.organizationId().equals(shipment.getPurchaseOrder().getSupplierOrganization().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        if (request.status() == shipment.getStatus()) {
            return PurchaseOrderShipmentResponse.from(shipment);
        }
        if (request.status() == PurchaseOrderShipmentStatus.RECEIVED) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "The distributor must confirm receipt of a delivered shipment");
        }
        if (request.status() == PurchaseOrderShipmentStatus.CANCELLED && actor.role() != UserRole.MANUFACTURER_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED,
                    "Only a manufacturer administrator may cancel a shipment");
        }
        if (!isAllowedTransition(shipment.getStatus(), request.status())) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "The shipment cannot move from " + shipment.getStatus() + " to " + request.status());
        }
        OffsetDateTime now = OffsetDateTime.now();
        shipment.setStatus(request.status());
        shipment.setLastUpdatedBy(userRepository.getReferenceById(actor.userId()));
        if (request.status() == PurchaseOrderShipmentStatus.IN_TRANSIT) {
            if (!isPickedAndPacked(shipment.getPurchaseOrder())) {
                throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                        "The purchase order must be fully picked and packed before dispatch");
            }
            dispatchPurchaseOrderStock(shipment, actor);
            shipment.setDispatchedAt(now);
            shipment.setDispatchNote(trimToNull(request.note()));
        } else if (request.status() == PurchaseOrderShipmentStatus.DELIVERED) {
            shipment.setDeliveredAt(now);
            shipment.setManufacturerNote(trimToNull(request.note()));
        } else if (request.status() == PurchaseOrderShipmentStatus.CANCELLED) {
            shipment.setManufacturerNote(trimToNull(request.note()));
        }
        PurchaseOrderShipment saved = shipmentRepository.save(shipment);
        if (request.status() == PurchaseOrderShipmentStatus.DELIVERED) {
            updateConsolidationStatus(saved, PurchaseOrderShipmentStatus.DELIVERED, actor);
        }
        if (request.status() == PurchaseOrderShipmentStatus.DELIVERED) {
            notifyOrganizationAdministrators(shipment.getPurchaseOrder().getBuyerOrganization(),
                    "Shipment delivered", "Shipment for purchase order "
                            + shipment.getPurchaseOrder().getPurchaseOrderNumber()
                            + " was marked delivered. Confirm receipt after checking the stock.",
                    NotificationPriority.HIGH, shipment.getId());
        }
        return PurchaseOrderShipmentResponse.from(saved);
    }

    @Transactional
    public PurchaseOrderShipmentResponse receive(AuthenticatedUser actor, UUID shipmentId, String note) {
        return receive(actor, shipmentId, new ReceiveShipmentRequest(note, null));
    }

    @Transactional
    public PurchaseOrderShipmentResponse receive(AuthenticatedUser actor, UUID shipmentId,
                                                  ReceiveShipmentRequest request) {
        requireDistributor(actor);
        PurchaseOrderShipment shipment = shipmentRepository.findByIdWithDetailsForUpdate(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Shipment not found"));
        PurchaseOrder purchaseOrder = shipment.getPurchaseOrder();
        if (!actor.organizationId().equals(purchaseOrder.getBuyerOrganization().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        if (shipment.getStatus() == PurchaseOrderShipmentStatus.RECEIVED) {
            return PurchaseOrderShipmentResponse.from(shipment);
        }
        if (shipment.getStatus() != PurchaseOrderShipmentStatus.DELIVERED) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only a delivered shipment can be received");
        }

        Warehouse warehouse = warehouseRepository.findByIdAndOrganization_IdForUpdate(
                        shipment.getDestinationWarehouse().getId(), actor.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND,
                        "Destination warehouse not found"));
        if (!warehouse.isActive()) {
            throw new ApiException(ErrorCode.CONFLICT, "The destination warehouse is inactive");
        }

        List<PurchaseOrderItem> items = new ArrayList<>(purchaseOrder.getItems());
        if (items.stream().anyMatch(item -> item.getDistributorOffer() == null)) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "The shipment has no destination offer for every purchase-order item");
        }
        Map<UUID, ReceiveShipmentRequest.Line> receiptLines = new HashMap<>();
        if (request == null || request.lines() == null) {
            for (PurchaseOrderItem item : items) {
                receiptLines.put(item.getId(), new ReceiveShipmentRequest.Line(
                        item.getId(), item.getQuantity(), 0, 0));
            }
        } else {
            for (ReceiveShipmentRequest.Line line : request.lines()) {
                if (line == null || line.purchaseOrderItemId() == null
                        || line.acceptedQuantity() < 0 || line.damagedQuantity() < 0
                        || line.missingQuantity() < 0) {
                    throw new ApiException(ErrorCode.BAD_REQUEST,
                            "Receipt quantities must be non-negative and linked to a purchase-order item");
                }
                if (receiptLines.putIfAbsent(line.purchaseOrderItemId(), line) != null) {
                    throw new ApiException(ErrorCode.BAD_REQUEST,
                            "Each purchase-order item must appear once in the receipt");
                }
            }
        }
        if (receiptLines.size() != items.size()) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "A receipt quantity breakdown is required for every purchase-order item");
        }
        for (PurchaseOrderItem item : items) {
            ReceiveShipmentRequest.Line line = receiptLines.get(item.getId());
            if (line == null || (long) line.acceptedQuantity() + line.damagedQuantity()
                    + line.missingQuantity() != item.getQuantity()) {
                throw new ApiException(ErrorCode.BAD_REQUEST,
                        "Accepted, damaged, and missing quantities must equal each ordered quantity");
            }
        }
        items.sort(Comparator.comparing(item -> item.getDistributorOffer().getId()));
        List<StockItem> lockedOffers = new ArrayList<>();
        for (PurchaseOrderItem item : items) {
            StockItem offer = stockItemRepository.findByIdAndOrganizationIdForUpdateWithWarehouse(
                            item.getDistributorOffer().getId(), actor.organizationId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_ITEM_NOT_FOUND));
            if (offer.getWarehouse() == null || !warehouse.getId().equals(offer.getWarehouse().getId())
                    || offer.getSourceStockItem() == null
                    || !item.getStockItem().getId().equals(offer.getSourceStockItem().getId())) {
                throw new ApiException(ErrorCode.CONFLICT,
                        "A shipment item is no longer linked to the destination warehouse");
            }
            lockedOffers.add(offer);
        }

        for (int i = 0; i < items.size(); i++) {
            PurchaseOrderItem item = items.get(i);
            StockItem offer = lockedOffers.get(i);
            ReceiveShipmentRequest.Line received = receiptLines.get(item.getId());
            PurchaseOrderShipmentReceiptLine receiptLine = new PurchaseOrderShipmentReceiptLine();
            receiptLine.setPurchaseOrderItem(item);
            receiptLine.setExpectedQuantity(item.getQuantity());
            receiptLine.setAcceptedQuantity(received.acceptedQuantity());
            receiptLine.setDamagedQuantity(received.damagedQuantity());
            receiptLine.setMissingQuantity(received.missingQuantity());
            shipment.addReceiptLine(receiptLine);
            if (received.acceptedQuantity() > 0) {
                int quantity;
                try {
                    quantity = Math.addExact(offer.getQuantityOnHand(), received.acceptedQuantity());
                } catch (ArithmeticException ex) {
                    throw new ApiException(ErrorCode.INVALID_STOCK_ADJUSTMENT,
                            "Receiving this shipment exceeds the supported stock quantity");
                }
                offer.setQuantityOnHand(quantity);
                StockMovement movement = new StockMovement();
                movement.setStockItem(offer);
                movement.setMovementType(StockMovementType.RESTOCK_IN);
                movement.setQuantityDelta(received.acceptedQuantity());
                movement.setReferenceType("PURCHASE_ORDER_SHIPMENT");
                movement.setReferenceId(shipment.getId());
                movement.setPerformedBy(userRepository.getReferenceById(actor.userId()));
                movement.setNote(trimToNull(request == null ? null : request.note()));
                stockMovementRepository.save(movement);
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        shipment.setStatus(PurchaseOrderShipmentStatus.RECEIVED);
        shipment.setReceivedAt(now);
        shipment.setReceiptNote(trimToNull(request == null ? null : request.note()));
        shipment.setLastUpdatedBy(userRepository.getReferenceById(actor.userId()));
        purchaseOrder.setStatus(PurchaseOrderStatus.FULFILLED);
        purchaseOrder.setFulfilledAt(now);
        shipmentRepository.save(shipment);
        purchaseOrderRepository.save(purchaseOrder);
        updateConsolidationStatus(shipment, PurchaseOrderShipmentStatus.RECEIVED, actor);
        notifyOrganizationAdministrators(purchaseOrder.getSupplierOrganization(),
                "Shipment received", "The distributor received shipment for purchase order "
                        + purchaseOrder.getPurchaseOrderNumber() + ".",
                NotificationPriority.MEDIUM, shipment.getId());
        return PurchaseOrderShipmentResponse.from(shipment);
    }

    private void updateConsolidationStatus(PurchaseOrderShipment shipment,
                                           PurchaseOrderShipmentStatus shipmentStatus,
                                           AuthenticatedUser actor) {
        UUID groupId = shipmentGroupRepository.findIdByShipmentId(shipment.getId()).orElse(null);
        if (groupId == null) return;
        PurchaseOrderShipmentGroup group = shipmentGroupRepository.findWithDetails(groupId).orElse(null);
        if (group == null || group.getShipments().stream()
                .anyMatch(member -> member.getStatus() != shipmentStatus)) return;
        group.setStatus(shipmentStatus == PurchaseOrderShipmentStatus.RECEIVED
                ? ManufacturerShipmentStatus.RECEIVED : ManufacturerShipmentStatus.DELIVERED);
        shipmentGroupRepository.save(group);
        Trip trip = group.getTrip();
        if (shipmentStatus != PurchaseOrderShipmentStatus.RECEIVED || trip == null
                || (trip.getStatus() != TripStatus.ONGOING && trip.getStatus() != TripStatus.DELAYED)) return;
        TripStop stop = trip.getStops().stream()
                .filter(candidate -> groupId.equals(candidate.getPurchaseOrderShipmentGroup().getId()))
                .findFirst().orElse(null);
        if (stop == null || stop.getStatus() != StopStatus.IN_PROGRESS) return;
        stop.setStatus(StopStatus.COMPLETED);
        trip.getStops().stream().filter(candidate -> candidate.getStatus() == StopStatus.PENDING
                && candidate.getSequence() == stop.getSequence() + 1).findFirst()
                .ifPresent(next -> next.setStatus(StopStatus.IN_PROGRESS));
        if (trip.getStops().stream().allMatch(candidate -> candidate.getStatus() == StopStatus.COMPLETED)) {
            TripStatus previous = trip.getStatus();
            trip.setStatus(TripStatus.COMPLETED);
            tripRepository.save(trip);
            TripStatusHistory history = new TripStatusHistory();
            history.setTrip(trip);
            history.setFromStatus(previous);
            history.setToStatus(TripStatus.COMPLETED);
            history.setChangedBy(userRepository.getReferenceById(actor.userId()));
            history.setNote("All shipment groups were received by their distributors");
            tripStatusHistoryRepository.save(history);
        }
    }

    private void dispatchPurchaseOrderStock(PurchaseOrderShipment shipment, AuthenticatedUser actor) {
        var purchaseOrder = shipment.getPurchaseOrder();
        for (var item : purchaseOrder.getItems()) {
            StockItem stock = stockItemRepository.findByIdAndOrganizationIdForUpdate(
                            item.getStockItem().getId(), purchaseOrder.getSupplierOrganization().getId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_ITEM_NOT_FOUND));
            if (stock.getQuantityOnHand() < item.getQuantity()
                    || (purchaseOrder.isStockReserved()
                    ? stock.getReservedQuantity() < item.getQuantity()
                    : stock.getAvailableQuantity() < item.getQuantity())) {
                throw new ApiException(ErrorCode.CONFLICT,
                        "Insufficient reserved manufacturer stock to dispatch the purchase order");
            }
            stock.setQuantityOnHand(stock.getQuantityOnHand() - item.getQuantity());
            if (purchaseOrder.isStockReserved()) {
                stock.setReservedQuantity(stock.getReservedQuantity() - item.getQuantity());
            }
            StockMovement movement = new StockMovement();
            movement.setStockItem(stock);
            movement.setMovementType(StockMovementType.SALE_OUT);
            movement.setQuantityDelta(-item.getQuantity());
            movement.setReferenceType("PURCHASE_ORDER_SHIPMENT");
            movement.setReferenceId(shipment.getId());
            movement.setPerformedBy(userRepository.getReferenceById(actor.userId()));
            movement.setNote("Dispatch for " + purchaseOrder.getPurchaseOrderNumber());
            stockMovementRepository.save(movement);
        }
        purchaseOrder.setStockReserved(false);
        purchaseOrderRepository.save(purchaseOrder);
    }

    private static boolean isPickedAndPacked(PurchaseOrder order) {
        return order.getPackageCount() > 0 && !order.getItems().isEmpty()
                && order.getItems().stream().allMatch(item -> item.getPickedQuantity() == item.getQuantity()
                && item.getPackedQuantity() == item.getQuantity());
    }

    private Warehouse resolveOriginWarehouse(PurchaseOrder order) {
        Warehouse origin = null;
        for (PurchaseOrderItem item : order.getItems()) {
            StockItem stock = stockItemRepository.findByIdAndOrganizationIdForUpdate(
                            item.getStockItem().getId(), order.getSupplierOrganization().getId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_ITEM_NOT_FOUND));
            if (!stock.isActive() || stock.getWarehouse() == null || !stock.getWarehouse().isActive()
                    || !order.getSupplierOrganization().getId().equals(stock.getWarehouse().getOrganization().getId())
                    || stock.getReservedQuantity() < item.getQuantity()) {
                throw new ApiException(ErrorCode.CONFLICT,
                        "Purchase-order stock must be reserved in an active manufacturer warehouse");
            }
            if (origin == null) origin = stock.getWarehouse();
            else if (!origin.getId().equals(stock.getWarehouse().getId())) {
                throw new ApiException(ErrorCode.CONFLICT,
                        "Purchase-order items must be picked from a single origin warehouse");
            }
        }
        if (origin == null) throw new ApiException(ErrorCode.CONFLICT, "The purchase order has no warehouse origin");
        return origin;
    }
    private StockItem resolveOffer(PurchaseOrderItem item, Organization buyer, Warehouse warehouse) {
        if (item.getDistributorOffer() != null) {
            StockItem offer = stockItemRepository.findByIdAndOrganizationIdForUpdateWithWarehouse(
                            item.getDistributorOffer().getId(), buyer.getId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_ITEM_NOT_FOUND,
                            "Distributor offer not found"));
            validateOffer(offer, item.getStockItem(), buyer, warehouse);
            return offer;
        }
        List<StockItem> offers = stockItemRepository.findOffersForUpdate(
                buyer.getId(), item.getStockItem().getId(), warehouse.getId()).stream()
                .filter(StockItem::isActive)
                .toList();
        if (offers.size() != 1) {
            throw new ApiException(ErrorCode.CONFLICT,
                    offers.isEmpty()
                            ? "No distributor offer exists for a shipment item"
                            : "Multiple distributor offers match a shipment item; select an offer when creating the purchase order");
        }
        return offers.get(0);
    }

    private PurchaseOrder lockedPurchaseOrder(UUID purchaseOrderId) {
        purchaseOrderRepository.findByIdForUpdate(purchaseOrderId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND,
                        "Purchase order not found"));
        return purchaseOrderRepository.findByIdWithItems(purchaseOrderId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND,
                        "Purchase order not found"));
    }

    private void validateOffer(StockItem offer, StockItem source, Organization buyer, Warehouse warehouse) {
        if (offer.getSourceStockItem() == null
                || !source.getId().equals(offer.getSourceStockItem().getId())
                || offer.getOrganization() == null
                || !buyer.getId().equals(offer.getOrganization().getId())
                || offer.getWarehouse() == null
                || !warehouse.getId().equals(offer.getWarehouse().getId())
                || !offer.isActive()) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "The distributor offer is not valid for the requested destination warehouse");
        }
    }

    private PurchaseOrderShipment findAuthorized(AuthenticatedUser actor, UUID shipmentId) {
        if (actor == null || actor.organizationId() == null) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        PurchaseOrderShipment shipment = shipmentRepository.findByIdWithDetails(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Shipment not found"));
        UUID organizationId = shipment.getPurchaseOrder().getBuyerOrganization().getId();
        if (!organizationId.equals(actor.organizationId())
                && !shipment.getPurchaseOrder().getSupplierOrganization().getId().equals(actor.organizationId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        if (actor.role() == null || actor.role().organizationtype() == null
                || (actor.role().organizationtype() == Organizationtype.DISTRIBUTOR
                && !organizationId.equals(actor.organizationId()))
                || (actor.role().organizationtype() == Organizationtype.MANUFACTURER
                && !shipment.getPurchaseOrder().getSupplierOrganization().getId().equals(actor.organizationId()))) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return shipment;
    }

    private static boolean isAllowedTransition(PurchaseOrderShipmentStatus current,
                                               PurchaseOrderShipmentStatus requested) {
        return ((current == PurchaseOrderShipmentStatus.AWAITING_PICKING
                || current == PurchaseOrderShipmentStatus.READY_FOR_DISPATCH)
                && requested == PurchaseOrderShipmentStatus.CANCELLED)
                || (current == PurchaseOrderShipmentStatus.READY_FOR_DISPATCH
                && (requested == PurchaseOrderShipmentStatus.IN_TRANSIT
                ))
                || (current == PurchaseOrderShipmentStatus.IN_TRANSIT
                && requested == PurchaseOrderShipmentStatus.DELIVERED);
    }

    private void notifyOrganizationAdministrators(Organization organization, String title,
                                                   String message, NotificationPriority priority,
                                                   UUID referenceId) {
        userRepository.findByOrganization_IdAndRole(organization.getId(),
                        organization.getType() == Organizationtype.DISTRIBUTOR
                                ? UserRole.DISTRIBUTOR_ADMIN : UserRole.MANUFACTURER_ADMIN,
                        unpaged())
                .forEach(user -> notificationService.create(user, title, message,
                        NotificationType.ORDER, priority, referenceId, "PURCHASE_ORDER_SHIPMENT"));
    }

    private void requireManufacturer(AuthenticatedUser actor) {
        if (actor == null || actor.organizationId() == null
                || actor.role() == null || actor.role().organizationtype() != Organizationtype.MANUFACTURER) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }

    private void requireDistributor(AuthenticatedUser actor) {
        if (actor == null || actor.organizationId() == null
                || actor.role() == null || actor.role().organizationtype() != Organizationtype.DISTRIBUTOR) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
