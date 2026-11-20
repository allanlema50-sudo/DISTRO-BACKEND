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
    private final NotificationService notifications;

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
        if (requested == ShipmentStatus.CANCELLED && shipment.getTrip() != null) {
            TripStatus tripStatus = shipment.getTrip().getStatus();
            if (tripStatus == TripStatus.ONGOING || tripStatus == TripStatus.DELAYED
                    || tripStatus == TripStatus.COMPLETED) {
                throw new ApiException(ErrorCode.CONFLICT,
                        "This shipment is on a trip that has already started");
            }
            shipment.setTrip(null);
        }
        shipment.setStatus(requested);
        record(shipment, from, requested, actor, note);
        return ShipmentResponse.from(shipments.save(shipment));
    }

    @Transactional
    public void delete(AuthenticatedUser actor, UUID id) {
        requireStaff(actor);
        Shipment shipment = shipments.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Shipment not found"));
        checkAccess(actor, shipment);
        if (shipment.getStatus() == ShipmentStatus.IN_TRANSIT
                || shipment.getStatus() == ShipmentStatus.DELIVERED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "A shipment that is moving or already delivered cannot be deleted");
        }
        if (shipment.getTrip() != null) {
            TripStatus tripStatus = shipment.getTrip().getStatus();
            if (tripStatus != TripStatus.SCHEDULED && tripStatus != TripStatus.CANCELLED) {
                throw new ApiException(ErrorCode.CONFLICT,
                        "Delete this shipment only before its trip starts");
            }
            shipment.setTrip(null);
            shipments.saveAndFlush(shipment);
        }
        shipments.delete(shipment);
    }

    @Transactional
    public ShipmentResponse correctLocation(AuthenticatedUser actor, UUID id, ShipmentLocationUpdate request) {
        Shipment shipment = shipments.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Shipment not found"));
        requireLocationEditor(actor, shipment);
        if (shipment.getStatus() == ShipmentStatus.DELIVERED
                || shipment.getStatus() == ShipmentStatus.CANCELLED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "A delivered or cancelled shipment location cannot be changed");
        }
        requirePin(request.pickupLat(), request.pickupLng(), "Pickup");
        requirePin(request.deliveryLat(), request.deliveryLng(), "Drop-off");
        String pickupLocation = request.pickupLocation().trim();
        String deliveryLocation = request.deliveryLocation().trim();
        boolean pickupChanged = !samePin(shipment.getPickupLat(), shipment.getPickupLng(),
                request.pickupLat(), request.pickupLng())
                || !pickupLocation.equals(shipment.getPickupLocation());
        boolean dropoffChanged = !samePin(shipment.getDeliveryLat(), shipment.getDeliveryLng(),
                request.deliveryLat(), request.deliveryLng())
                || !deliveryLocation.equals(shipment.getDeliveryLocation());
        if (!pickupChanged && !dropoffChanged) {
            return ShipmentResponse.from(shipment);
        }
        Double previousPickupLat = shipment.getPickupLat();
        Double previousPickupLng = shipment.getPickupLng();
        String previousPickup = shipment.getPickupLocation();
        Double previousDropLat = shipment.getDeliveryLat();
        Double previousDropLng = shipment.getDeliveryLng();
        String previousDrop = shipment.getDeliveryLocation();
        shipment.setPickupLocation(pickupLocation);
        shipment.setPickupLat(request.pickupLat());
        shipment.setPickupLng(request.pickupLng());
        shipment.setDeliveryLocation(deliveryLocation);
        shipment.setDeliveryLat(request.deliveryLat());
        shipment.setDeliveryLng(request.deliveryLng());
        syncTripLocation(shipment, previousPickupLat, previousPickupLng, previousPickup,
                previousDropLat, previousDropLng, previousDrop, pickupChanged, dropoffChanged);
        Shipment saved = shipments.save(shipment);
        notifyDriver(saved, pickupChanged, dropoffChanged);
        return ShipmentResponse.from(saved);
    }

    private void syncTripLocation(Shipment shipment, Double previousPickupLat, Double previousPickupLng,
                                  String previousPickup, Double previousDropLat, Double previousDropLng,
                                  String previousDrop, boolean pickupChanged, boolean dropoffChanged) {
        Trip trip = shipment.getTrip();
        if (trip == null) {
            return;
        }
        if (pickupChanged && (samePin(trip.getOriginLat(), trip.getOriginLng(), previousPickupLat, previousPickupLng)
                || previousPickup != null && previousPickup.equals(trip.getOriginName()))) {
            trip.setOriginName(shipment.getPickupLocation());
            trip.setOriginAddress(shipment.getPickupLocation());
            trip.setOriginLat(shipment.getPickupLat());
            trip.setOriginLng(shipment.getPickupLng());
        }
        if (dropoffChanged && trip.getStops() != null) {
            for (TripStop stop : trip.getStops()) {
                if (samePin(stop.getLocationLat(), stop.getLocationLng(), previousDropLat, previousDropLng)
                        || previousDrop != null && previousDrop.equals(stop.getLocationAddress())) {
                    stop.setLocationName(shipment.getDeliveryLocation());
                    stop.setLocationAddress(shipment.getDeliveryLocation());
                    stop.setLocationLat(shipment.getDeliveryLat());
                    stop.setLocationLng(shipment.getDeliveryLng());
                }
            }
        }
        trips.save(trip);
    }

    private void notifyDriver(Shipment shipment, boolean pickupChanged, boolean dropoffChanged) {
        Trip trip = shipment.getTrip();
        if (trip == null || trip.getRider() == null) {
            return;
        }
        String number = shipment.getShipmentNumber() == null ? "This shipment" : shipment.getShipmentNumber();
        String detail = pickupChanged && dropoffChanged
                ? "Pickup is now " + shipment.getPickupLocation()
                    + ". Drop-off is now " + shipment.getDeliveryLocation() + "."
                : pickupChanged
                    ? "Pickup is now " + shipment.getPickupLocation() + "."
                    : "Drop-off is now " + shipment.getDeliveryLocation() + ".";
        notifications.create(trip.getRider(), "Shipment location corrected",
                number + ". " + detail, NotificationType.DELIVERY, NotificationPriority.HIGH,
                shipment.getId(), "SHIPMENT");
    }

    private void requireLocationEditor(AuthenticatedUser actor, Shipment shipment) {
        if (actor != null && actor.role() == UserRole.SUPER_ADMIN) {
            return;
        }
        boolean organizationAdmin = actor != null
                && (actor.role() == UserRole.MANUFACTURER_ADMIN || actor.role() == UserRole.DISTRIBUTOR_ADMIN)
                && actor.organizationId() != null
                && actor.organizationId().equals(shipment.getOrganization().getId());
        if (!organizationAdmin) {
            throw new ApiException(ErrorCode.ACCESS_DENIED,
                    "Only a super admin or that organization's admin can correct a location");
        }
    }

    private static boolean samePin(Double firstLat, Double firstLng, Double secondLat, Double secondLng) {
        if (firstLat == null || firstLng == null || secondLat == null || secondLng == null) {
            return false;
        }
        return Math.abs(firstLat - secondLat) < 0.00001 && Math.abs(firstLng - secondLng) < 0.00001;
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
        requirePin(request.pickupLat(), request.pickupLng(), "Pickup");
        requirePin(request.deliveryLat(), request.deliveryLng(), "Drop-off");
        shipment.setPickupLocation(request.pickupLocation().trim());
        shipment.setPickupWindowStart(request.pickupWindowStart());
        shipment.setPickupWindowEnd(request.pickupWindowEnd());
        shipment.setPickupLat(request.pickupLat());
        shipment.setPickupLng(request.pickupLng());
        shipment.setDeliveryLocation(request.deliveryLocation().trim());
        shipment.setDeliveryWindowStart(request.deliveryWindowStart());
        shipment.setDeliveryWindowEnd(request.deliveryWindowEnd());
        shipment.setDeliveryLat(request.deliveryLat());
        shipment.setDeliveryLng(request.deliveryLng());
        shipment.setAllowSharedLoads(Boolean.TRUE.equals(request.allowSharedLoads()));
        shipment.setSpecialInstructions(trimToNull(request.specialInstructions()));
        shipment.setTrackingReference(trimToNull(request.trackingReference()));
        shipment.setNotes(trimToNull(request.notes()));
    }

    private void validateWindow(ShipmentRequest request) {
        if (request.pickupWindowStart() != null && request.pickupWindowEnd() != null
                && !request.pickupWindowEnd().isAfter(request.pickupWindowStart())) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Pickup window end must be later than its start");
        }
        if (request.deliveryWindowStart() != null && request.deliveryWindowEnd() != null
                && !request.deliveryWindowEnd().isAfter(request.deliveryWindowStart())) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Delivery window end must be later than its start");
        }
    }

    private static void requirePin(Double latitude, Double longitude, String label) {
        if (latitude == null || longitude == null
                || latitude < -90 || latitude > 90
                || longitude < -180 || longitude > 180
                || (latitude == 0.0 && longitude == 0.0)) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    label + " needs a map pin. 0, 0 is not a pickup or drop-off.");
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
