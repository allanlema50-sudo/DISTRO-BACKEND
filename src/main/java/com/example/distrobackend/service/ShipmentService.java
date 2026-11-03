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

@Service @RequiredArgsConstructor
public class ShipmentService {
    private final ShipmentRepository shipments;
    private final ShipmentStatusHistoryRepository history;
    private final OrderRepository orders;
    private final TripRepository trips;
    private final UserRepository users;
    private final OrderService orderService;

    @Transactional(readOnly = true)
    public Page<ShipmentResponse> list(AuthenticatedUser actor, ShipmentStatus status, UUID requestedOrg, Pageable page) {
        UUID org = organizationFilter(actor, requestedOrg);
        UUID customer = actor.role() == UserRole.CUSTOMER ? actor.userId() : null;
        return shipments.search(org, customer, status, page).map(ShipmentResponse::from);
    }
    @Transactional(readOnly = true)
    public ShipmentResponse get(AuthenticatedUser actor, UUID id) {
        Shipment s = shipments.findWithDetailsById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Shipment not found"));
        checkAccess(actor, s);
        return ShipmentResponse.from(s);
    }
    @Transactional
    public ShipmentResponse create(AuthenticatedUser actor, ShipmentRequest request) {
        requireStaff(actor);
        Order order = orders.findByIdWithOwnershipForUpdate(request.orderId()).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found"));
        if (!actor.organizationId().equals(order.getOrganization().getId())) throw new ApiException(ErrorCode.NOT_FOUND, "Order not found");
        if (order.getStatus() != OrderStatus.AWAITING_DISPATCH) throw new ApiException(ErrorCode.CONFLICT, "Only orders awaiting dispatch can be shipped");
        if (shipments.existsByOrder_Id(order.getId())) throw new ApiException(ErrorCode.CONFLICT, "Order already has a shipment");
        Shipment s = new Shipment(); s.setOrder(order); s.setOrganization(order.getOrganization()); s.setCreatedBy(users.getReferenceById(actor.userId()));
        s.setShipmentNumber("SHP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        s.setTrackingReference(request.trackingReference()); s.setNotes(request.notes());
        if (request.tripId() != null) s.setTrip(eligibleTrip(actor, request.tripId(), order));
        Shipment saved = shipments.save(s); record(saved, null, ShipmentStatus.READY, actor, "Shipment created");
        if (saved.getTrip() != null) { saved.setStatus(ShipmentStatus.ASSIGNED); record(saved, ShipmentStatus.READY, ShipmentStatus.ASSIGNED, actor, "Shipment assigned to trip"); }
        return ShipmentResponse.from(shipments.save(saved));
    }
    @Transactional
    public ShipmentResponse update(AuthenticatedUser actor, UUID id, ShipmentRequest request) {
        requireStaff(actor); Shipment s = shipments.findByIdForUpdate(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Shipment not found")); checkAccess(actor, s);
        if (!s.getOrder().getId().equals(request.orderId())) throw new ApiException(ErrorCode.BAD_REQUEST, "A shipment cannot be moved to another order");
        if (s.getStatus() == ShipmentStatus.IN_TRANSIT || s.getStatus() == ShipmentStatus.DELIVERED || s.getStatus() == ShipmentStatus.CANCELLED) throw new ApiException(ErrorCode.CONFLICT, "A shipment in progress or closed cannot be edited");
        s.setTrackingReference(request.trackingReference()); s.setNotes(request.notes());
        Trip trip = request.tripId() == null ? null : eligibleTrip(actor, request.tripId(), s.getOrder());
        if (trip != s.getTrip() || s.getStatus() == ShipmentStatus.FAILED) { ShipmentStatus before = s.getStatus(); s.setTrip(trip); s.setStatus(trip == null ? ShipmentStatus.READY : ShipmentStatus.ASSIGNED); record(s, before, s.getStatus(), actor, "Shipment trip assignment updated"); }
        return ShipmentResponse.from(shipments.save(s));
    }
    @Transactional
    public ShipmentResponse updateStatus(AuthenticatedUser actor, UUID id, ShipmentStatus requested, String note) {
        requireStaff(actor); Shipment s = shipments.findByIdForUpdate(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Shipment not found")); checkAccess(actor, s);
        ShipmentStatus from = s.getStatus();
        boolean allowed = (from == ShipmentStatus.READY && requested == ShipmentStatus.CANCELLED)
                || (from == ShipmentStatus.ASSIGNED && (requested == ShipmentStatus.IN_TRANSIT || requested == ShipmentStatus.CANCELLED || requested == ShipmentStatus.FAILED))
                || (from == ShipmentStatus.IN_TRANSIT && (requested == ShipmentStatus.DELIVERED || requested == ShipmentStatus.FAILED));
        if (!allowed) throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "Shipment cannot transition from " + from + " to " + requested);
        if (requested == ShipmentStatus.IN_TRANSIT) {
            if (s.getTrip() == null || (s.getTrip().getStatus() != TripStatus.ONGOING && s.getTrip().getStatus() != TripStatus.DELAYED)) throw new ApiException(ErrorCode.CONFLICT, "Shipment trip must be in progress before dispatch");
            orderService.updateOrderStatus(s.getOrder().getId(), actor.userId(), new OrderStatusUpdate(OrderStatus.IN_TRANSIT, note));
        }
        if (requested == ShipmentStatus.DELIVERED && s.getOrder().getStatus() != OrderStatus.DELIVERED) throw new ApiException(ErrorCode.CONFLICT, "Customer delivery OTP must complete the order before closing the shipment");
        s.setStatus(requested); record(s, from, requested, actor, note);
        return ShipmentResponse.from(shipments.save(s));
    }
    private Trip eligibleTrip(AuthenticatedUser actor, UUID id, Order order) {
        Trip trip = trips.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Trip not found"));
        boolean containsOrder = order.getId().equals(trip.getOrder() == null ? null : trip.getOrder().getId())
                || trip.getStops().stream().anyMatch(stop -> stop.getOrder() != null && order.getId().equals(stop.getOrder().getId()));
        if (!order.getOrganization().getId().equals(trip.getOrganization().getId())
                || trip.getTripType() != TripType.DELIVERY || !containsOrder
                || trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED)
            throw new ApiException(ErrorCode.BAD_REQUEST, "Shipment requires an open delivery trip containing its order in the same organization");
        return trip;
    }
    private void record(Shipment s, ShipmentStatus from, ShipmentStatus to, AuthenticatedUser actor, String note) { ShipmentStatusHistory h = new ShipmentStatusHistory(); h.setShipment(s); h.setFromStatus(from); h.setToStatus(to); h.setChangedBy(users.getReferenceById(actor.userId())); h.setNote(note); history.save(h); }
    private UUID organizationFilter(AuthenticatedUser actor, UUID requested) {
        if (actor.role() == UserRole.SUPER_ADMIN) return requested;
        if (actor.role() == UserRole.CUSTOMER) return null;
        requireStaff(actor); if (requested != null && !requested.equals(actor.organizationId())) throw new ApiException(ErrorCode.ACCESS_DENIED); return actor.organizationId();
    }
    private void checkAccess(AuthenticatedUser actor, Shipment s) {
        if (actor.role() == UserRole.SUPER_ADMIN) return;
        if (actor.role() == UserRole.CUSTOMER && actor.userId().equals(s.getOrder().getCustomer().getId())) return;
        if (actor.organizationId() != null && actor.organizationId().equals(s.getOrganization().getId())) return;
        throw new ApiException(ErrorCode.NOT_FOUND, "Shipment not found");
    }
    private void requireStaff(AuthenticatedUser actor) { if (actor == null || actor.organizationId() == null || (actor.role() != UserRole.MANUFACTURER_ADMIN && actor.role() != UserRole.MANUFACTURER_STAFF && actor.role() != UserRole.DISTRIBUTOR_ADMIN && actor.role() != UserRole.DISTRIBUTOR_STAFF)) throw new ApiException(ErrorCode.ACCESS_DENIED); }
}
