package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.*;
import com.example.distrobackend.Domain.enums.*;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.*;
import com.example.distrobackend.repository.*;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShipmentService {
    private final ShipmentRepository shipments;
    private final ShipmentStatusHistoryRepository history;
    private final OrderRepository orders;
    private final OrganizationRepository organizations;
    private final TripRepository trips;
    private final UserRepository users;
    private final OrderService orderService;

    @Transactional(readOnly = true)
    public Page<ShipmentResponse> list(AuthenticatedUser actor, ShipmentStatus status,
                                       UUID requestedOrg, Pageable page) {
        UUID org = organizationFilter(actor, requestedOrg);
        UUID customer = actor.role() == UserRole.CUSTOMER ? actor.userId() : null;
        return shipments.search(org, customer, status, page).map(ShipmentResponse::from);
    }

    @Transactional(readOnly = true)
    public ShipmentResponse get(AuthenticatedUser actor, UUID id) {
        Shipment shipment = shipments.findWithDetailsById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Shipment not found"));
        checkAccess(actor, shipment);
        return ShipmentResponse.from(shipment);
    }

    @Transactional
    public ShipmentResponse create(AuthenticatedUser actor, ShipmentRequest request) {
        requireStaff(actor);
        validateWindow(request);
        Organization organization = resolveOrganization(actor, request.organizationId());
        Order order = null;
        if (request.orderId() != null) {
            order = orders.findByIdWithOwnershipForUpdate(request.orderId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found"));
            if (!organization.getId().equals(order.getOrganization().getId())) {
                throw new ApiException(ErrorCode.BAD_REQUEST,
                        "The order must belong to the requesting organization");
            }
            if (order.getStatus() != OrderStatus.AWAITING_DISPATCH) {
                throw new ApiException(ErrorCode.CONFLICT,
                        "An order-linked shipment requires an order awaiting dispatch");
            }
            if (shipments.existsByOrder_Id(order.getId())) {
                throw new ApiException(ErrorCode.CONFLICT, "Order already has a shipment");
            }
        }

        Shipment shipment = new Shipment();
        shipment.setOrder(order);
        shipment.setOrganization(organization);
        shipment.setCreatedBy(users.getReferenceById(actor.userId()));
        shipment.setShipmentNumber("SHP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        applyRequest(shipment, request);
        if (request.tripId() != null) {
            shipment.setTrip(eligibleTrip(request.tripId(), organization, order));
        }
        Shipment saved = shipments.save(shipment);
        record(saved, null, ShipmentStatus.READY, actor, "Shipment registered");
        if (saved.getTrip() != null) {
            saved.setStatus(ShipmentStatus.ASSIGNED);
            record(saved, ShipmentStatus.READY, ShipmentStatus.ASSIGNED, actor, "Shipment assigned to trip");
        }
        return ShipmentResponse.from(shipments.save(saved));
    }

    @Transactional
    public ShipmentResponse update(AuthenticatedUser actor, UUID id, ShipmentRequest request) {
        requireStaff(actor);
        validateWindow(request);
        Shipment shipment = shipments.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Shipment not found"));
        checkAccess(actor, shipment);
        if (!shipment.getOrganization().getId().equals(request.organizationId())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "A shipment cannot be moved to another organization");
        }
        if (!sameOrder(shipment.getOrder(), request.orderId())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "A shipment cannot be moved to another order");
        }
        if (shipment.getStatus() == ShipmentStatus.IN_TRANSIT
                || shipment.getStatus() == ShipmentStatus.DELIVERED
                || shipment.getStatus() == ShipmentStatus.CANCELLED) {
            throw new ApiException(ErrorCode.CONFLICT, "A shipment in progress or closed cannot be edited");
        }
        applyRequest(shipment, request);
        Trip trip = request.tripId() == null ? null
                : eligibleTrip(request.tripId(), shipment.getOrganization(), shipment.getOrder());
        if (trip != shipment.getTrip() || shipment.getStatus() == ShipmentStatus.FAILED) {
            ShipmentStatus before = shipment.getStatus();
            shipment.setTrip(trip);
            shipment.setStatus(trip == null ? ShipmentStatus.READY : ShipmentStatus.ASSIGNED);
            record(shipment, before, shipment.getStatus(), actor, "Shipment trip assignment updated");
        }
        return ShipmentResponse.from(shipments.save(shipment));
    }

    @Transactional
    public ShipmentResponse updateStatus(AuthenticatedUser actor, UUID id,
                                        ShipmentStatus requested, String note) {
        requireStaff(actor);
        Shipment shipment = shipments.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Shipment not found"));
        checkAccess(actor, shipment);
        ShipmentStatus from = shipment.getStatus();
        boolean allowed = (from == ShipmentStatus.READY && requested == ShipmentStatus.CANCELLED)
                || (from == ShipmentStatus.ASSIGNED && (requested == ShipmentStatus.IN_TRANSIT
                || requested == ShipmentStatus.CANCELLED || requested == ShipmentStatus.FAILED))
                || (from == ShipmentStatus.IN_TRANSIT && (requested == ShipmentStatus.DELIVERED
                || requested == ShipmentStatus.FAILED));
        if (!allowed) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Shipment cannot transition from " + from + " to " + requested);
        }
        if (requested == ShipmentStatus.IN_TRANSIT) {
            if (shipment.getTrip() == null || (shipment.getTrip().getStatus() != TripStatus.ONGOING
                    && shipment.getTrip().getStatus() != TripStatus.DELAYED)) {
                throw new ApiException(ErrorCode.CONFLICT,
                        "Shipment trip must be in progress before dispatch");
            }
            if (shipment.getOrder() != null) {
                orderService.updateOrderStatus(shipment.getOrder().getId(), actor.userId(),
                        new OrderStatusUpdate(OrderStatus.IN_TRANSIT, note));
            }
        }
        if (requested == ShipmentStatus.DELIVERED) {
            if (shipment.getOrder() != null && shipment.getOrder().getStatus() != OrderStatus.DELIVERED) {
                throw new ApiException(ErrorCode.CONFLICT,
                        "Customer delivery OTP must complete the order before closing the shipment");
            }
            if (shipment.getOrder() == null && (shipment.getTrip() == null
                    || shipment.getTrip().getStatus() != TripStatus.COMPLETED)) {
                throw new ApiException(ErrorCode.CONFLICT,
                        "The delivery trip must be completed before closing the shipment");
            }
        }
        shipment.setStatus(requested);
        record(shipment, from, requested, actor, note);
        return ShipmentResponse.from(shipments.save(shipment));
    }

    private Organization resolveOrganization(AuthenticatedUser actor, UUID organizationId) {
        if (actor.role() != UserRole.SUPER_ADMIN && !organizationId.equals(actor.organizationId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return organizations.findById(organizationId)
                .filter(org -> org.getStatus() == OrganizationStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Active requesting organization not found"));
    }

    private Trip eligibleTrip(UUID tripId, Organization organization, Order order) {
        Trip trip = trips.findById(tripId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Trip not found"));
        boolean containsOrder = order == null
                || order.getId().equals(trip.getOrder() == null ? null : trip.getOrder().getId())
                || trip.getStops().stream().anyMatch(stop -> stop.getOrder() != null
                && order.getId().equals(stop.getOrder().getId()));
        if (!organization.getId().equals(trip.getOrganization().getId())
                || trip.getTripType() != TripType.DELIVERY || !containsOrder
                || trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Shipment requires an open delivery trip in the requesting organization"
                            + (order == null ? "" : " containing its order"));
        }
        return trip;
    }

    private void applyRequest(Shipment shipment, ShipmentRequest request) {
        shipment.setCargoDescription(request.cargoDescription().trim());
        shipment.setCargoCategory(request.cargoCategory().trim());
        shipment.setWeightKg(request.weightKg());
        shipment.setVolumeM3(request.volumeM3());
        shipment.setHandlingRequirements(request.handlingRequirements());
        shipment.setDeliveryLocation(request.deliveryLocation().trim());
        shipment.setDeliveryWindowStart(request.deliveryWindowStart());
        shipment.setDeliveryWindowEnd(request.deliveryWindowEnd());
        shipment.setAllowSharedLoads(Boolean.TRUE.equals(request.allowSharedLoads()));
        shipment.setSpecialInstructions(trimToNull(request.specialInstructions()));
        shipment.setTrackingReference(trimToNull(request.trackingReference()));
        shipment.setNotes(trimToNull(request.notes()));
    }

    private void validateWindow(ShipmentRequest request) {
        if (request.deliveryWindowStart() != null && request.deliveryWindowEnd() != null
                && !request.deliveryWindowEnd().isAfter(request.deliveryWindowStart())) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Delivery window end must be later than its start");
        }
    }

    private static boolean sameOrder(Order order, UUID requestedOrderId) {
        return order == null ? requestedOrderId == null : order.getId().equals(requestedOrderId);
    }

    private void record(Shipment shipment, ShipmentStatus from, ShipmentStatus to,
                        AuthenticatedUser actor, String note) {
        ShipmentStatusHistory item = new ShipmentStatusHistory();
        item.setShipment(shipment);
        item.setFromStatus(from);
        item.setToStatus(to);
        item.setChangedBy(users.getReferenceById(actor.userId()));
        item.setNote(note);
        history.save(item);
    }

    private UUID organizationFilter(AuthenticatedUser actor, UUID requested) {
        if (actor.role() == UserRole.SUPER_ADMIN) return requested;
        if (actor.role() == UserRole.CUSTOMER) return null;
        requireStaff(actor);
        if (requested != null && !requested.equals(actor.organizationId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return actor.organizationId();
    }

    private void checkAccess(AuthenticatedUser actor, Shipment shipment) {
        if (actor.role() == UserRole.SUPER_ADMIN) return;
        if (actor.role() == UserRole.CUSTOMER && shipment.getOrder() != null
                && actor.userId().equals(shipment.getOrder().getCustomer().getId())) return;
        if (actor.organizationId() != null
                && actor.organizationId().equals(shipment.getOrganization().getId())) return;
        throw new ApiException(ErrorCode.NOT_FOUND, "Shipment not found");
    }

    private void requireStaff(AuthenticatedUser actor) {
        if (actor == null || (actor.role() != UserRole.SUPER_ADMIN
                && (actor.organizationId() == null || (actor.role() != UserRole.MANUFACTURER_ADMIN
                && actor.role() != UserRole.MANUFACTURER_STAFF
                && actor.role() != UserRole.DISTRIBUTOR_ADMIN
                && actor.role() != UserRole.DISTRIBUTOR_STAFF)))) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
