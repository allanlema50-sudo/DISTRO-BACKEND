package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.PurchaseOrder;
import com.example.distrobackend.Domain.entity.PurchaseOrderItem;
import com.example.distrobackend.Domain.entity.PurchaseOrderShipment;
import com.example.distrobackend.Domain.entity.PurchaseOrderSettlement;
import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.entity.StockMovement;
import com.example.distrobackend.Domain.entity.Warehouse;
import com.example.distrobackend.Domain.enums.NotificationPriority;
import com.example.distrobackend.Domain.enums.NotificationType;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.PurchaseOrderStatus;
import com.example.distrobackend.Domain.enums.SettlementStatus;
import com.example.distrobackend.Domain.enums.ShipmentStatus;
import com.example.distrobackend.Domain.enums.StockMovementType;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.Exception.ResourceNotFoundException;
import com.example.distrobackend.dto.CreateShipmentRequest;
import com.example.distrobackend.dto.ShipmentResponse;
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
import java.util.UUID;

import static org.springframework.data.domain.Pageable.unpaged;

@Service
@RequiredArgsConstructor
public class ShipmentService {

    private final PurchaseOrderShipmentRepository shipmentRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderSettlementRepository settlementRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public ShipmentResponse create(AuthenticatedUser actor, CreateShipmentRequest request) {
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
        if (shipmentRepository.existsByPurchaseOrder_Id(purchaseOrder.getId())) {
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

        PurchaseOrderShipment shipment = new PurchaseOrderShipment();
        shipment.setPurchaseOrder(purchaseOrder);
        shipment.setDestinationWarehouse(warehouse);
        shipment.setStatus(ShipmentStatus.READY_FOR_DISPATCH);
        shipment.setTrackingNumber(trimToNull(request.trackingNumber()));
        shipment.setCarrier(trimToNull(request.carrier()));
        shipment.setCreatedBy(userRepository.getReferenceById(actor.userId()));
        PurchaseOrderShipment saved = shipmentRepository.saveAndFlush(shipment);
        notifyOrganizationAdministrators(purchaseOrder.getBuyerOrganization(),
                "Shipment ready for dispatch", "Shipment for purchase order "
                        + purchaseOrder.getPurchaseOrderNumber()
                        + " is ready for dispatch after settlement.",
                NotificationPriority.HIGH, saved.getId());
        return ShipmentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<ShipmentResponse> listForManufacturer(AuthenticatedUser actor, Pageable pageable) {
        requireManufacturer(actor);
        return shipmentRepository
                .findByPurchaseOrder_SupplierOrganization_IdOrderByCreatedAtDesc(actor.organizationId(), pageable)
                .map(ShipmentResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<ShipmentResponse> listForDistributor(AuthenticatedUser actor, Pageable pageable) {
        requireDistributor(actor);
        return shipmentRepository
                .findByPurchaseOrder_BuyerOrganization_IdOrderByCreatedAtDesc(actor.organizationId(), pageable)
                .map(ShipmentResponse::from);
    }

    @Transactional(readOnly = true)
    public ShipmentResponse get(AuthenticatedUser actor, UUID shipmentId) {
        PurchaseOrderShipment shipment = findAuthorized(actor, shipmentId);
        return ShipmentResponse.from(shipment);
    }

    @Transactional
    public ShipmentResponse updateStatus(AuthenticatedUser actor, UUID shipmentId,
                                         ShipmentStatusRequest request) {
        requireManufacturer(actor);
        PurchaseOrderShipment shipment = shipmentRepository.findByIdWithDetailsForUpdate(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Shipment not found"));
        if (!actor.organizationId().equals(shipment.getPurchaseOrder().getSupplierOrganization().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        if (request.status() == shipment.getStatus()) {
            return ShipmentResponse.from(shipment);
        }
        if (request.status() == ShipmentStatus.RECEIVED) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "The distributor must confirm receipt of a delivered shipment");
        }
        if (request.status() == ShipmentStatus.CANCELLED && actor.role() != UserRole.MANUFACTURER_ADMIN) {
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
        if (request.status() == ShipmentStatus.IN_TRANSIT) {
            shipment.setDispatchedAt(now);
            shipment.setDispatchNote(trimToNull(request.note()));
        } else if (request.status() == ShipmentStatus.DELIVERED) {
            shipment.setDeliveredAt(now);
            shipment.setReceiptNote(trimToNull(request.note()));
        } else if (request.status() == ShipmentStatus.CANCELLED) {
            shipment.setReceiptNote(trimToNull(request.note()));
        }
        PurchaseOrderShipment saved = shipmentRepository.save(shipment);
        if (request.status() == ShipmentStatus.DELIVERED) {
            notifyOrganizationAdministrators(shipment.getPurchaseOrder().getBuyerOrganization(),
                    "Shipment delivered", "Shipment for purchase order "
                            + shipment.getPurchaseOrder().getPurchaseOrderNumber()
                            + " was marked delivered. Confirm receipt after checking the stock.",
                    NotificationPriority.HIGH, shipment.getId());
        }
        return ShipmentResponse.from(saved);
    }

    @Transactional
    public ShipmentResponse receive(AuthenticatedUser actor, UUID shipmentId, String note) {
        requireDistributor(actor);
        PurchaseOrderShipment shipment = shipmentRepository.findByIdWithDetailsForUpdate(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Shipment not found"));
        PurchaseOrder purchaseOrder = shipment.getPurchaseOrder();
        if (!actor.organizationId().equals(purchaseOrder.getBuyerOrganization().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        if (shipment.getStatus() == ShipmentStatus.RECEIVED) {
            return ShipmentResponse.from(shipment);
        }
        if (shipment.getStatus() != ShipmentStatus.DELIVERED) {
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
            int quantity;
            try {
                quantity = Math.addExact(offer.getQuantityOnHand(), item.getQuantity());
            } catch (ArithmeticException ex) {
                throw new ApiException(ErrorCode.INVALID_STOCK_ADJUSTMENT,
                        "Receiving this shipment exceeds the supported stock quantity");
            }
            offer.setQuantityOnHand(quantity);
            StockMovement movement = new StockMovement();
            movement.setStockItem(offer);
            movement.setMovementType(StockMovementType.RESTOCK_IN);
            movement.setQuantityDelta(item.getQuantity());
            movement.setReferenceType("PURCHASE_ORDER_SHIPMENT");
            movement.setReferenceId(shipment.getId());
            movement.setPerformedBy(userRepository.getReferenceById(actor.userId()));
            movement.setNote(trimToNull(note));
            stockMovementRepository.save(movement);
        }

        OffsetDateTime now = OffsetDateTime.now();
        shipment.setStatus(ShipmentStatus.RECEIVED);
        shipment.setReceivedAt(now);
        shipment.setReceiptNote(trimToNull(note));
        shipment.setLastUpdatedBy(userRepository.getReferenceById(actor.userId()));
        purchaseOrder.setStatus(PurchaseOrderStatus.FULFILLED);
        purchaseOrder.setFulfilledAt(now);
        shipmentRepository.save(shipment);
        purchaseOrderRepository.save(purchaseOrder);
        notifyOrganizationAdministrators(purchaseOrder.getSupplierOrganization(),
                "Shipment received", "The distributor received shipment for purchase order "
                        + purchaseOrder.getPurchaseOrderNumber() + ".",
                NotificationPriority.MEDIUM, shipment.getId());
        return ShipmentResponse.from(shipment);
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

    private static boolean isAllowedTransition(ShipmentStatus current, ShipmentStatus requested) {
        return (current == ShipmentStatus.READY_FOR_DISPATCH
                && (requested == ShipmentStatus.IN_TRANSIT || requested == ShipmentStatus.CANCELLED))
                || (current == ShipmentStatus.IN_TRANSIT
                && (requested == ShipmentStatus.DELIVERED || requested == ShipmentStatus.CANCELLED));
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
