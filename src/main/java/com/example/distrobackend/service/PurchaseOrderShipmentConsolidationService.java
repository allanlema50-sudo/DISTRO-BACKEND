package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.PurchaseOrder;
import com.example.distrobackend.Domain.entity.PurchaseOrderShipment;
import com.example.distrobackend.Domain.entity.PurchaseOrderShipmentGroup;
import com.example.distrobackend.Domain.enums.ManufacturerShipmentStatus;
import com.example.distrobackend.Domain.enums.CheckoutPaymentStatus;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.PurchaseOrderShipmentStatus;
import com.example.distrobackend.Domain.enums.PurchaseOrderStatus;
import com.example.distrobackend.Domain.enums.SettlementStatus;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.Exception.ResourceNotFoundException;
import com.example.distrobackend.dto.CreateShipmentConsolidationRequest;
import com.example.distrobackend.dto.ShipmentConsolidationResponse;
import com.example.distrobackend.dto.ShipmentConsolidationSuggestion;
import com.example.distrobackend.repository.CheckoutPaymentRepository;
import com.example.distrobackend.repository.PaymentAllocationRepository;
import com.example.distrobackend.repository.PurchaseOrderSettlementRepository;
import com.example.distrobackend.repository.PurchaseOrderShipmentGroupRepository;
import com.example.distrobackend.repository.PurchaseOrderShipmentRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PurchaseOrderShipmentConsolidationService {
    private final PurchaseOrderShipmentRepository shipmentRepository;
    private final PurchaseOrderShipmentGroupRepository groupRepository;
    private final PurchaseOrderSettlementRepository settlementRepository;
    private final CheckoutPaymentRepository checkoutPaymentRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ShipmentConsolidationSuggestion> suggestions(AuthenticatedUser actor) {
        UUID organizationId = requireManufacturer(actor);
        List<PurchaseOrderShipment> eligible = shipmentRepository
                .findByPurchaseOrder_SupplierOrganization_IdAndStatusOrderByCreatedAtDesc(
                        organizationId, PurchaseOrderShipmentStatus.READY_FOR_DISPATCH, PageRequest.of(0, 500))
                .getContent().stream()
                .filter(shipment -> shipment.getOriginWarehouse() != null
                        && !groupRepository.existsByShipments_Id(shipment.getId()))
                .toList();
        Map<String, List<PurchaseOrderShipment>> groups = new LinkedHashMap<>();
        for (PurchaseOrderShipment shipment : eligible) {
            String key = shipment.getOriginWarehouse().getId() + ":" + shipment.getDestinationWarehouse().getId();
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(shipment);
        }
        return groups.values().stream().filter(items -> items.size() > 1).map(items -> {
            var first = items.get(0);
            var origin = first.getOriginWarehouse();
            var destination = first.getDestinationWarehouse();
            return new ShipmentConsolidationSuggestion(origin.getId(), destination.getId(),
                    origin.getAddress(), destination.getAddress(), destination.getLatitude(), destination.getLongitude(),
                    origin.getLatitude() != null && origin.getLongitude() != null
                            && destination.getLatitude() != null && destination.getLongitude() != null
                            ? "READY" : "LOCATION_DATA_REQUIRED",
                    items.stream().map(PurchaseOrderShipment::getId).toList());
        }).toList();
    }

    @Transactional
    public ShipmentConsolidationResponse create(AuthenticatedUser actor,
                                                CreateShipmentConsolidationRequest request) {
        UUID manufacturerId = requireManufacturer(actor);
        List<UUID> requested = request.purchaseOrderShipmentIds();
        if (requested.size() < 2 || requested.size() > 50) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "A consolidation must contain between 2 and 50 shipments");
        }
        if (requested.stream().anyMatch(java.util.Objects::isNull)
                || requested.stream().distinct().count() != requested.size()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Shipment IDs must be present and unique");
        }
        List<UUID> orderedIds = requested.stream().sorted(Comparator.comparing(UUID::toString)).toList();
        List<PurchaseOrderShipment> shipments = new ArrayList<>();
        for (UUID id : orderedIds) {
            PurchaseOrderShipment shipment = shipmentRepository.findByIdWithDetailsForUpdate(id)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Purchase-order shipment not found"));
            if (!manufacturerId.equals(shipment.getPurchaseOrder().getSupplierOrganization().getId())) {
                throw new ApiException(ErrorCode.ACCESS_DENIED, "All shipments must belong to the authenticated manufacturer");
            }
            if (groupRepository.existsByShipments_Id(id)) {
                throw new ApiException(ErrorCode.CONFLICT, "A purchase-order shipment is already in a consolidation");
            }
            validateEligible(shipment);
            shipments.add(shipment);
        }
        PurchaseOrderShipment first = shipments.get(0);
        UUID originId = first.getOriginWarehouse().getId();
        UUID destinationId = first.getDestinationWarehouse().getId();
        for (PurchaseOrderShipment shipment : shipments) {
            if (!originId.equals(shipment.getOriginWarehouse().getId())
                    || !destinationId.equals(shipment.getDestinationWarehouse().getId())) {
                throw new ApiException(ErrorCode.BAD_REQUEST,
                        "Consolidated purchase orders must share the same origin and destination warehouse IDs");
            }
        }
        PurchaseOrderShipmentGroup group = new PurchaseOrderShipmentGroup();
        group.setShipmentReference("MFS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        group.setManufacturerOrganization(first.getPurchaseOrder().getSupplierOrganization());
        group.setOriginWarehouse(first.getOriginWarehouse());
        group.setDestinationWarehouse(first.getDestinationWarehouse());
        group.setStatus(ManufacturerShipmentStatus.CREATED);
        group.setCreatedBy(userRepository.getReferenceById(actor.userId()));
        group.setShipments(shipments);
        try {
            return ShipmentConsolidationResponse.from(groupRepository.saveAndFlush(group));
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "One or more purchase-order shipments were consolidated concurrently");
        }
    }

    @Transactional(readOnly = true)
    public ShipmentConsolidationResponse get(AuthenticatedUser actor, UUID groupId) {
        UUID manufacturerId = requireManufacturer(actor);
        PurchaseOrderShipmentGroup group = groupRepository.findWithDetails(groupId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Shipment consolidation not found"));
        if (!manufacturerId.equals(group.getManufacturerOrganization().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return ShipmentConsolidationResponse.from(group);
    }

    private void validateEligible(PurchaseOrderShipment shipment) {
        PurchaseOrder order = shipment.getPurchaseOrder();
        if (shipment.getStatus() != PurchaseOrderShipmentStatus.READY_FOR_DISPATCH
                || order.getStatus() != PurchaseOrderStatus.APPROVED || !order.isStockReserved()
                || order.getPackageCount() <= 0 || order.getItems().isEmpty()
                || order.getItems().stream().anyMatch(item -> item.getPickedQuantity() != item.getQuantity()
                || item.getPackedQuantity() != item.getQuantity())) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Every shipment must be paid, reserved, fully picked, packed and ready for dispatch");
        }
        if (shipment.getOriginWarehouse() == null || !shipment.getOriginWarehouse().isActive()
                || !order.getSupplierOrganization().getId().equals(shipment.getOriginWarehouse().getOrganization().getId())
                || shipment.getDestinationWarehouse() == null || !shipment.getDestinationWarehouse().isActive()
                || !order.getBuyerOrganization().getId().equals(shipment.getDestinationWarehouse().getOrganization().getId())) {
            throw new ApiException(ErrorCode.CONFLICT, "Shipment origin or destination warehouse is unavailable");
        }
        if (!isPaid(order)) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "A verified payment for every manufacturer purchase order is required");
        }
    }

    private boolean isPaid(PurchaseOrder order) {
        if (order.getCheckout() != null) {
            var payment = checkoutPaymentRepository
                    .findFirstByCheckout_IdOrderByInitiatedAtDesc(order.getCheckout().getId()).orElse(null);
            if (payment == null || payment.getStatus() != CheckoutPaymentStatus.SUCCESS
                    || payment.getAmount().compareTo(order.getCheckout().getTotalAmount()) != 0
                    || !payment.getCurrency().equalsIgnoreCase(order.getCheckout().getCurrency())) return false;
            return paymentAllocationRepository.findByPurchaseOrder_Id(order.getId()).stream()
                    .anyMatch(allocation -> allocation.getPayment().getId().equals(payment.getId())
                            && allocation.getGrossAmount().compareTo(order.getTotalAmount()) == 0
                            && allocation.getCurrency().equalsIgnoreCase(order.getCurrency()));
        }
        return settlementRepository.findByPurchaseOrderIdOrderByCreatedAtDesc(order.getId()).stream()
                .anyMatch(settlement -> settlement.getStatus() == SettlementStatus.SETTLED
                        && settlement.getAmount() != null
                        && settlement.getAmount().compareTo(order.getTotalAmount()) == 0
                        && order.getCurrency().equalsIgnoreCase(settlement.getCurrency()));
    }

    private UUID requireManufacturer(AuthenticatedUser actor) {
        if (actor == null || actor.organizationId() == null
                || actor.organizationType() != Organizationtype.MANUFACTURER
                || (actor.role() != UserRole.MANUFACTURER_ADMIN && actor.role() != UserRole.MANUFACTURER_STAFF)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return actor.organizationId();
    }
}
