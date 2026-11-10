package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.PurchaseOrder;
import com.example.distrobackend.Domain.entity.PurchaseOrderItem;
import com.example.distrobackend.Domain.entity.PurchaseOrderShipment;
import com.example.distrobackend.Domain.entity.PurchaseOrderShipmentReceiptLine;
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
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.StockMovementRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.repository.WarehouseRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final WarehouseRepository warehouseRepository;
    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

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
        PurchaseOrderSettlement settlement = settlementRepository
                .findByPurchaseOrderIdOrderByCreatedAtDesc(purchaseOrder.getId()).stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.CONFLICT,
                        "The purchase order has no settlement record"));
        if (settlement.getStatus() != SettlementStatus.SETTLED) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "The purchase order must be settled before dispatch");
        }
        if (settlement.getAmount() == null
                || settlement.getAmount().compareTo(purchaseOrder.getTotalAmount()) != 0
                || !purchaseOrder.getCurrency().equalsIgnoreCase(settlement.getCurrency())) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "The settled amount does not match the purchase order");
        }
        PurchaseOrderShipment shipment = shipmentRepository.findByPurchaseOrderId(purchaseOrder.getId())
                .orElse(null);
        if (shipment != null && shipment.getStatus() != PurchaseOrderShipmentStatus.CANCELLED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "A shipment already exists for this purchase order");
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
        notifyOrganizationAdministrators(purchaseOrder.getSupplierOrganization(),
                "Shipment received", "The distributor received shipment for purchase order "
                        + purchaseOrder.getPurchaseOrderNumber() + ".",
                NotificationPriority.MEDIUM, shipment.getId());
        return PurchaseOrderShipmentResponse.from(shipment);
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
        return (current == PurchaseOrderShipmentStatus.READY_FOR_DISPATCH
                && (requested == PurchaseOrderShipmentStatus.IN_TRANSIT
                || requested == PurchaseOrderShipmentStatus.CANCELLED))
                || (current == PurchaseOrderShipmentStatus.IN_TRANSIT
                && (requested == PurchaseOrderShipmentStatus.DELIVERED
                || requested == PurchaseOrderShipmentStatus.CANCELLED));
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
