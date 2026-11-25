package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.*;
import com.example.distrobackend.Domain.enums.*;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.*;
import com.example.distrobackend.repository.*;
import com.example.distrobackend.Domain.enums.ManufacturerShipmentStatus;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.security.TripAccessService;
import com.example.distrobackend.integration.notification.EmailProviderClient;
import lombok.extern.slf4j.Slf4j;
import com.example.distrobackend.Domain.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripService {

    private final TripRepository tripRepository;
    private final OrderRepository orderRepository;
    private final StockItemRepository stockItemRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final TripLocationPingRepository locationPingRepository;
    private final TripStatusHistoryRepository tripStatusHistoryRepository;
    private final TripOperationalActionRepository tripOperationalActionRepository;
    private final RiderProfileRepository riderProfileRepository;
    private final VehicleRepository vehicleRepository;
    private final ShipmentRepository shipmentRepository;
    private final SharedLoadRepository sharedLoadRepository;
    private final TripAccessService tripAccessService;
    private final OtpService otpService;
    private final OrderService orderService;
    private final SimpMessagingTemplate messagingTemplate;
    private final EmailProviderClient emailProviderClient;
    private final ShipmentService shipmentService;
    private final PurchaseOrderShipmentGroupRepository purchaseOrderShipmentGroupRepository;
    private final PurchaseOrderShipmentService purchaseOrderShipmentService;

    @Transactional
    public TripResponse createTrip(AuthenticatedUser actor, TripRequest request) {
        UUID organizationId = requireOrganization(actor);
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Organization not found"));

        validateStopSequences(request);
        Set<Integer> sequences = new HashSet<>();
        Trip trip = new Trip();
        trip.setOrganization(organization);
        trip.setTripType(request.tripType());
        trip.setTripNumber("TRIP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        trip.setStatus(TripStatus.SCHEDULED);
        trip.setOriginName(request.origin().name());
        trip.setOriginAddress(request.origin().address());
        trip.setOriginLat(request.origin().latitude());
        trip.setOriginLng(request.origin().longitude());
        trip.setScheduledStartLabel(request.scheduledStartLabel());
        trip.setTotalDistanceKm(request.totalDistanceKm());

        for (TripRequest.TripStopRequest stopReq : request.stops()) {
            if (!sequences.add(stopReq.sequence())) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Trip stop sequence numbers must be unique");
            }
            TripStop stop = new TripStop();
            stop.setSequence(stopReq.sequence());
            stop.setStatus(stopReq.sequence() == 1 ? StopStatus.IN_PROGRESS : StopStatus.PENDING);
            stop.setLocationName(stopReq.location().name());
            stop.setLocationAddress(stopReq.location().address());
            stop.setLocationLat(stopReq.location().latitude());
            stop.setLocationLng(stopReq.location().longitude());
            stop.setEtaLabel(stopReq.etaLabel());
            stop.setDistanceFromPreviousKm(stopReq.distanceFromPreviousKm());
            stop.setContactName(stopReq.contactName());
            stop.setContactPhone(stopReq.contactPhone());

            if (request.tripType() == TripType.DELIVERY) {
                if (stopReq.orderId() == null || stopReq.stockItemId() != null) {
                    throw new ApiException(ErrorCode.BAD_REQUEST,
                            "Delivery stops must reference exactly one order");
                }
                Order order = orderRepository.findByIdWithOwnership(stopReq.orderId())
                        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found"));
                if (!organizationId.equals(order.getOrganization().getId())) {
                    throw new ApiException(ErrorCode.ACCESS_DENIED, "Order belongs to another organization");
                }
                stop.setOrder(order);
            } else {
                if (stopReq.stockItemId() == null || stopReq.orderId() != null
                        || stopReq.restockQuantity() == null || stopReq.restockQuantity() < 1) {
                    throw new ApiException(ErrorCode.BAD_REQUEST,
                            "Restock stops require one stock item and a positive quantity");
                }
                StockItem stockItem = stockItemRepository
                        .findByIdAndOrganizationId(stopReq.stockItemId(), organizationId)
                        .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));
                stop.setStockItem(stockItem);
                stop.setRestockQuantity(stopReq.restockQuantity());
            }
            trip.addStop(stop);
        }

        Trip saved = tripRepository.save(trip);
        recordStatus(saved, null, TripStatus.SCHEDULED, actor, "Trip created");
        return TripResponse.from(saved, aggregateItems(saved));
    }

    @Transactional
    public TripResponse createFreightTrip(AuthenticatedUser actor, FreightTripRequest request) {
        if (actor == null || actor.role() == UserRole.CUSTOMER || actor.role() == UserRole.DRIVER) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Role not authorized to create trips");
        }
        Vehicle vehicle = vehicleRepository.findByIdForUpdate(request.vehicleId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Vehicle not found"));
        if (actor.role() != UserRole.SUPER_ADMIN
                && !requireOrganization(actor).equals(vehicle.getOrganization().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        User driver = userRepository.findByIdForUpdate(request.driverId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Driver not found"));
        if (driver.getRole() != UserRole.DRIVER) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Driver not found");
        }
        if (driver.getStatus() != UserStatus.ACTIVE
                || driver.getDriverApprovalStatus() != DriverApprovalStatus.APPROVED) {
            throw new ApiException(ErrorCode.CONFLICT, "Only an approved active driver can be assigned");
        }
        takeDriverFromUnstartedTrips(actor, driver.getId(), null);
        takeVehicleFromUnstartedTrips(actor, vehicle.getId(), null);

        List<Shipment> shipments = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (UUID shipmentId : request.shipmentIds()) {
            if (!seen.add(shipmentId)) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Each shipment can only be added once");
            }
            Shipment shipment = shipmentRepository.findByIdForUpdate(shipmentId)
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Shipment not found"));
            if (shipment.getStatus() != ShipmentStatus.READY || shipment.getTrip() != null) {
                throw new ApiException(ErrorCode.CONFLICT, "Shipment is not available for a trip");
            }
            shipments.add(shipment);
        }

        Shipment first = shipments.get(0);
        Trip trip = new Trip();
        trip.setOrganization(vehicle.getOrganization());
        trip.setTripType(TripType.DELIVERY);
        trip.setTripNumber("TRIP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        trip.setStatus(TripStatus.SCHEDULED);
        trip.setRider(driver);
        trip.setVehicle(vehicle);
        requireTripPin(first.getPickupLat(), first.getPickupLng(), "Pickup");
        trip.setOriginName(first.getPickupLocation());
        trip.setOriginAddress(first.getPickupLocation());
        trip.setOriginLat(first.getPickupLat());
        trip.setOriginLng(first.getPickupLng());
        trip.setScheduledStartLabel(request.plannedStart());
        trip.setTotalDistanceKm(request.distanceKm());
        int sequence = 1;
        for (Shipment shipment : shipments) {
            requireTripPin(shipment.getDeliveryLat(), shipment.getDeliveryLng(), "Drop-off");
            TripStop stop = new TripStop();
            stop.setSequence(sequence++);
            stop.setStatus(StopStatus.PENDING);
            stop.setLocationName(shipment.getDeliveryLocation());
            stop.setLocationAddress(shipment.getDeliveryLocation());
            stop.setLocationLat(shipment.getDeliveryLat());
            stop.setLocationLng(shipment.getDeliveryLng());
            if (shipment.getDeliveryWindowStart() != null) {
                stop.setEtaLabel(shipment.getDeliveryWindowStart().toString());
            }
            trip.addStop(stop);
        }

        Trip saved = tripRepository.save(trip);
        for (Shipment shipment : shipments) {
            shipment.setTrip(saved);
            shipment.setStatus(ShipmentStatus.ASSIGNED);
            shipmentRepository.save(shipment);
        }
        String note = request.notes() == null || request.notes().isBlank()
                ? "Trip created" : request.notes().trim();
        recordStatus(saved, null, TripStatus.SCHEDULED, actor, note);
        return TripResponse.from(saved, aggregateItems(saved));
    }

    @Transactional
    public TripResponse createManufacturerShipmentTrip(AuthenticatedUser actor,
                                                        ManufacturerShipmentTripRequest request) {
        UUID organizationId = requireOrganization(actor);
        if (actor.role() != UserRole.MANUFACTURER_ADMIN && actor.role() != UserRole.MANUFACTURER_STAFF) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Only manufacturer staff can dispatch manufacturer shipments");
        }
        Vehicle vehicle = vehicleRepository.findByIdAndOrganizationForUpdate(request.vehicleId(), organizationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Vehicle not found"));
        if (vehicle.getStatus() != VehicleStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CONFLICT, "Only an active vehicle can be assigned");
        }
        User driver = userRepository.findByIdForUpdate(request.driverId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Driver not found"));
        if (driver.getRole() != UserRole.DRIVER || driver.getOrganization() == null
                || !organizationId.equals(driver.getOrganization().getId())
                || driver.getStatus() != UserStatus.ACTIVE
                || driver.getDriverApprovalStatus() != DriverApprovalStatus.APPROVED) {
            throw new ApiException(ErrorCode.CONFLICT, "Driver must be approved, active and belong to this manufacturer");
        }
        takeDriverFromUnstartedTrips(actor, driver.getId(), null);
        takeVehicleFromUnstartedTrips(actor, vehicle.getId(), null);
        Set<UUID> uniqueIds = new HashSet<>();
        List<PurchaseOrderShipmentGroup> groups = new ArrayList<>();
        for (UUID groupId : request.manufacturerShipmentGroupIds()) {
            if (!uniqueIds.add(groupId)) throw new ApiException(ErrorCode.BAD_REQUEST, "Duplicate shipment group");
            purchaseOrderShipmentGroupRepository.findByIdForUpdate(groupId)
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Shipment group not found"));
            PurchaseOrderShipmentGroup group = purchaseOrderShipmentGroupRepository.findWithDetails(groupId)
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Shipment group not found"));
            if (!organizationId.equals(group.getManufacturerOrganization().getId())) {
                throw new ApiException(ErrorCode.ACCESS_DENIED, "Shipment group belongs to another manufacturer");
            }
            if (group.getStatus() != ManufacturerShipmentStatus.CREATED || group.getTrip() != null
                    || group.getShipments().isEmpty()) {
                throw new ApiException(ErrorCode.CONFLICT, "Shipment group is not eligible for trip assignment");
            }
            groups.add(group);
        }
        Warehouse origin = groups.get(0).getOriginWarehouse();
        if (groups.stream().anyMatch(g -> !origin.getId().equals(g.getOriginWarehouse().getId()))) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Trip shipment groups must share one origin warehouse");
        }
        Organization organization = groups.get(0).getManufacturerOrganization();
        Trip trip = new Trip();
        trip.setOrganization(organization);
        trip.setTripType(TripType.DELIVERY);
        trip.setTripNumber("TRIP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        trip.setStatus(TripStatus.SCHEDULED);
        trip.setRider(driver);
        trip.setVehicle(vehicle);
        trip.setOriginName(origin.getName());
        trip.setOriginAddress(origin.getAddress());
        trip.setOriginLat(origin.getLatitude());
        trip.setOriginLng(origin.getLongitude());
        trip.setScheduledStartLabel(request.plannedStart());
        int sequence = 1;
        for (PurchaseOrderShipmentGroup group : groups) {
            Warehouse destination = group.getDestinationWarehouse();
            TripStop stop = new TripStop();
            stop.setSequence(sequence++);
            stop.setStatus(StopStatus.PENDING);
            stop.setPurchaseOrderShipmentGroup(group);
            stop.setLocationName(destination.getName());
            stop.setLocationAddress(destination.getAddress());
            stop.setLocationLat(destination.getLatitude());
            stop.setLocationLng(destination.getLongitude());
            trip.addStop(stop);
        }
        Trip saved = tripRepository.save(trip);
        for (PurchaseOrderShipmentGroup group : groups) {
            group.setTrip(saved);
            group.setStatus(ManufacturerShipmentStatus.TRIP_ASSIGNED);
            purchaseOrderShipmentGroupRepository.save(group);
        }
        recordStatus(saved, null, TripStatus.SCHEDULED, actor,
                request.notes() == null || request.notes().isBlank() ? "Manufacturer shipment trip created" : request.notes().trim());
        return TripResponse.from(saved, aggregateItems(saved));
    }

    @Transactional
    public TripResponse assignRider(AuthenticatedUser actor, UUID tripId, UUID riderId) {
        return assignRider(actor, tripId, riderId, null, false);
    }

    @Transactional
    public TripResponse assignRider(AuthenticatedUser actor, UUID tripId, UUID riderId, UUID vehicleId) {
        return assignRider(actor, tripId, riderId, vehicleId, false);
    }

    @Transactional
    public TripResponse assignRider(AuthenticatedUser actor, UUID tripId, UUID riderId,
                                    UUID vehicleId, boolean clearVehicle) {
        if (clearVehicle && vehicleId != null) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "vehicleId must be omitted when clearVehicle is true");
        }
        Trip trip = actor != null && actor.role() == UserRole.SUPER_ADMIN
                ? tripRepository.findByIdForUpdate(tripId)
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Trip not found"))
                : getOrganizationTripForUpdate(actor, tripId);
        User rider = userRepository.findByIdForUpdate(riderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Rider not found"));
        boolean platformDriver = rider.getOrganization() == null;
        boolean sameOrganization = !platformDriver
                && trip.getOrganization().getId().equals(rider.getOrganization().getId());
        boolean superAdmin = actor != null && actor.role() == UserRole.SUPER_ADMIN;
        if (rider.getRole() != UserRole.DRIVER || (!superAdmin && !platformDriver && !sameOrganization)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Rider is not valid for this organization");
        }
        if (platformDriver && (actor == null || actor.role() != UserRole.SUPER_ADMIN)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED,
                    "Only a Super Admin can dispatch a Mydistro pool driver to an organization trip");
        }
        if (rider.getStatus() != UserStatus.ACTIVE
                || rider.getDriverApprovalStatus() != DriverApprovalStatus.APPROVED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Only an approved active driver can be assigned");
        }
        if (trip.getStatus() != TripStatus.SCHEDULED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "A driver can only be changed before the trip starts");
        }
        takeDriverFromUnstartedTrips(actor, riderId, tripId);
        if (vehicleId != null) {
            Vehicle vehicle = vehicleRepository.findByIdAndOrganizationForUpdate(
                            vehicleId, trip.getOrganization().getId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Vehicle not found"));
            if (vehicle.getStatus() != VehicleStatus.ACTIVE) {
                throw new ApiException(ErrorCode.CONFLICT, "Only an active vehicle can be assigned");
            }
            takeVehicleFromUnstartedTrips(actor, vehicleId, tripId);
            trip.setVehicle(vehicle);
        } else if (clearVehicle) {
            trip.setVehicle(null);
        }
        trip.setRider(rider);
        Trip saved = tripRepository.save(trip);
        return TripResponse.from(saved, aggregateItems(saved));
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void deleteUnstarted(AuthenticatedUser actor, UUID tripId) {
        requireSuperAdmin(actor);
        Trip trip = tripRepository.findByIdForUpdate(tripId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Trip not found"));
        removeUnstartedTrip(trip);
    }

    @Transactional
    public int deleteAllUnstarted(AuthenticatedUser actor) {
        requireSuperAdmin(actor);
        List<Trip> scheduled = tripRepository.findByStatus(TripStatus.SCHEDULED);
        int deleted = 0;
        for (Trip trip : scheduled) {
            Trip locked = tripRepository.findByIdForUpdate(trip.getId()).orElse(null);
            if (locked != null && locked.getStatus() == TripStatus.SCHEDULED) {
                removeUnstartedTrip(locked);
                deleted++;
            }
        }
        return deleted;
    }

    private void removeUnstartedTrip(Trip trip) {
        if (trip.getStatus() != TripStatus.SCHEDULED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Only a trip that has not started can be deleted");
        }
        for (Shipment shipment : shipmentRepository.findByTrip_Id(trip.getId())) {
            if (shipment.getStatus() == ShipmentStatus.IN_TRANSIT
                    || shipment.getStatus() == ShipmentStatus.DELIVERED) {
                throw new ApiException(ErrorCode.CONFLICT,
                        "This trip has a shipment that is already moving or delivered");
            }
            if (shipment.getStatus() == ShipmentStatus.ASSIGNED) {
                shipment.setStatus(ShipmentStatus.READY);
            }
            shipment.setTrip(null);
            shipmentRepository.saveAndFlush(shipment);
        }
        releaseManufacturerShipmentGroups(trip, true);
        tripStatusHistoryRepository.deleteByTrip_Id(trip.getId());
        locationPingRepository.deleteByTrip_Id(trip.getId());
        sharedLoadRepository.deleteByTripId(trip.getId());
        tripRepository.delete(trip);
    }

    private void takeDriverFromUnstartedTrips(AuthenticatedUser actor, UUID driverId, UUID exceptTripId) {
        List<TripStatus> started = List.of(TripStatus.ONGOING, TripStatus.DELAYED);
        if (tripRepository.existsByRider_IdAndStatusIn(driverId, started)) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "This driver is on a trip that has already started");
        }
        List<Trip> scheduled = tripRepository.findByRiderIdAndStatusIn(driverId, List.of(TripStatus.SCHEDULED))
                .stream()
                .filter(trip -> exceptTripId == null || !exceptTripId.equals(trip.getId()))
                .toList();
        if (scheduled.isEmpty()) {
            return;
        }
        if (actor == null || actor.role() != UserRole.SUPER_ADMIN) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "The driver is already assigned to another active trip");
        }
        for (Trip other : scheduled) {
            Trip locked = tripRepository.findByIdForUpdate(other.getId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Trip not found"));
            if (locked.getStatus() == TripStatus.SCHEDULED) {
                locked.setRider(null);
                tripRepository.save(locked);
            }
        }
    }

    private void takeVehicleFromUnstartedTrips(AuthenticatedUser actor, UUID vehicleId, UUID exceptTripId) {
        List<TripStatus> started = List.of(TripStatus.ONGOING, TripStatus.DELAYED);
        if (tripRepository.existsByVehicle_IdAndStatusIn(vehicleId, started)) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "This vehicle is on a trip that has already started");
        }
        List<Trip> scheduled = tripRepository.findByVehicle_IdAndStatusIn(vehicleId, List.of(TripStatus.SCHEDULED))
                .stream()
                .filter(trip -> exceptTripId == null || !exceptTripId.equals(trip.getId()))
                .toList();
        if (scheduled.isEmpty()) {
            return;
        }
        if (actor == null || actor.role() != UserRole.SUPER_ADMIN) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "The vehicle is already assigned to another active trip");
        }
        for (Trip other : scheduled) {
            Trip locked = tripRepository.findByIdForUpdate(other.getId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Trip not found"));
            if (locked.getStatus() == TripStatus.SCHEDULED) {
                locked.setVehicle(null);
                tripRepository.save(locked);
            }
        }
    }

    private void requireSuperAdmin(AuthenticatedUser actor) {
        if (actor == null || actor.role() != UserRole.SUPER_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED,
                    "Only a super admin can delete a trip that has not started");
        }
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TripResponse confirmStopDelivery(AuthenticatedUser actor, UUID tripId, UUID stopId,
                                            ConfirmStopRequest request) {
        Trip trip = getAssignedTrip(actor, tripId);
        TripStop stop = trip.getStops().stream()
                .filter(s -> stopId.equals(s.getId()))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Stop not found in this trip"));
        if (stop.getStatus() != StopStatus.IN_PROGRESS) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Only the active stop can be completed");
        }
        if (stop.getOrder() == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "This endpoint only completes delivery stops");
        }
        if (stop.getOrder().getStatus() != OrderStatus.IN_TRANSIT) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Order is not in transit");
        }
        otpService.verifyDelivery(stop, request.otp());

        stop.setStatus(StopStatus.COMPLETED);
        orderService.updateOrderStatus(stop.getOrder().getId(), actor.userId(),
                new OrderStatusUpdate(OrderStatus.DELIVERED, "Delivery confirmed by customer OTP"));
        shipmentService.recordOrderDelivered(stop.getOrder().getId(), actor);
        sendDeliveryConfirmation(stop.getOrder());
        trip.getStops().stream()
                .filter(s -> s.getStatus() == StopStatus.PENDING
                        && s.getSequence() == stop.getSequence() + 1)
                .findFirst()
                .ifPresent(next -> next.setStatus(StopStatus.IN_PROGRESS));

        if (trip.getStops().stream().allMatch(s -> s.getStatus() == StopStatus.COMPLETED)) {
            trip.setStatus(TripStatus.COMPLETED);
        } else {
            trip.setStatus(TripStatus.ONGOING);
        }
        return TripResponse.from(tripRepository.save(trip), aggregateItems(trip));
    }

    @Transactional
    public void issueDeliveryOtp(UUID customerId, UUID tripId, UUID stopId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Trip not found"));
        TripStop stop = trip.getStops().stream()
                .filter(candidate -> stopId.equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Stop not found in this trip"));
        if (stop.getOrder() == null || stop.getOrder().getCustomer() == null
                || !customerId.equals(stop.getOrder().getCustomer().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        if (trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Delivery OTPs are unavailable for closed trips");
        }
        if (stop.getStatus() != StopStatus.IN_PROGRESS) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Delivery OTPs are only available for the active stop");
        }
        if (stop.getOrder().getStatus() != OrderStatus.IN_TRANSIT) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Order is not in transit");
        }
        otpService.issueDelivery(stop.getOrder().getCustomer(), stop);
    }

    @Transactional
    public LocationPingResponse recordLocation(AuthenticatedUser actor, UUID tripId,
                                               LocationPingRequest request) {
        Trip trip = getAssignedTrip(actor, tripId);
        if (trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Location cannot be recorded for a closed trip");
        }
        TripLocationPing ping = new TripLocationPing();
        ping.setTrip(trip);
        ping.setLat(request.lat());
        ping.setLng(request.lng());
        LocationPingResponse response = LocationPingResponse.from(locationPingRepository.save(ping));
        riderProfileRepository.findById(actor.userId()).ifPresent(profile -> {
            profile.setCurrentLat(request.lat());
            profile.setCurrentLng(request.lng());
            profile.setLastLocationAt(java.time.OffsetDateTime.now());
            riderProfileRepository.save(profile);
        });
        messagingTemplate.convertAndSend("/topic/trips/" + tripId + "/location", response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<LocationPingResponse> locationHistory(AuthenticatedUser actor, UUID tripId) {
        if (!tripAccessService.canAccess(actor, tripId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return locationPingRepository.findByTripIdOrderByRecordedAtDesc(tripId)
                .stream().map(LocationPingResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public Page<TripResponse> list(AuthenticatedUser actor, Pageable pageable) {
        return list(actor, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<TripResponse> list(AuthenticatedUser actor, TripStatus status, Pageable pageable) {
        if (actor == null || actor.role() == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        return switch (actor.role()) {
            case SUPER_ADMIN -> (status == null ? tripRepository.findAll(pageable)
                    : tripRepository.findByStatus(status, pageable))
                    .map(trip -> TripResponse.from(trip, aggregateItems(trip)));
            case DRIVER -> (status == null ? tripRepository.findByRiderId(actor.userId(), pageable)
                    : tripRepository.findByRiderIdAndStatus(actor.userId(), status, pageable))
                    .map(trip -> TripResponse.from(trip, aggregateItems(trip)));
            case CUSTOMER -> tripRepository.findByCustomerId(actor.userId(), status, pageable)
                    .map(trip -> customerResponse(trip, actor.userId()));
            case MANUFACTURER_ADMIN, MANUFACTURER_STAFF,
                    DISTRIBUTOR_ADMIN, DISTRIBUTOR_STAFF -> {
                if (actor.organizationId() == null) throw new ApiException(ErrorCode.ACCESS_DENIED);
                yield (status == null
                        ? tripRepository.findByOrganization_Id(actor.organizationId(), pageable)
                        : tripRepository.findByOrganization_IdAndStatus(actor.organizationId(), status, pageable))
                        .map(trip -> TripResponse.from(trip, aggregateItems(trip)));
            }
        };
    }

    @Transactional
    public TripStopResponse updateStopStatus(AuthenticatedUser actor, UUID tripId, UUID stopId,
                                             StopStatus requestedStatus) {
        Trip trip;
        if (actor != null && actor.role() == UserRole.DRIVER) {
            trip = tripRepository.findByIdAndRiderForUpdate(tripId, actor.userId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Assigned trip not found"));
        } else if (actor != null && (actor.role() == UserRole.MANUFACTURER_ADMIN
                || actor.role() == UserRole.MANUFACTURER_STAFF
                || actor.role() == UserRole.DISTRIBUTOR_ADMIN
                || actor.role() == UserRole.DISTRIBUTOR_STAFF)) {
            trip = getOrganizationTripForUpdate(actor, tripId);
        } else {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        TripStop stop = trip.getStops().stream().filter(candidate -> stopId.equals(candidate.getId()))
                .findFirst().orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Stop not found in this trip"));
        if (trip.getStatus() != TripStatus.ONGOING
                || requestedStatus != StopStatus.IN_PROGRESS || stop.getStatus() != StopStatus.PENDING
                || trip.getStops().stream().anyMatch(candidate -> candidate.getStatus() == StopStatus.IN_PROGRESS)
                || trip.getStops().stream().anyMatch(candidate -> candidate.getSequence() < stop.getSequence()
                && candidate.getStatus() != StopStatus.COMPLETED)) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only the next pending stop on an ongoing trip can be started; delivery completion requires OTP confirmation");
        }
        stop.setStatus(StopStatus.IN_PROGRESS);
        tripRepository.save(trip);
        return new TripStopResponse(stop.getId().toString(), stop.getSequence(),
                new TripLocation(stop.getLocationName(), stop.getLocationAddress(),
                        stop.getLocationLat(), stop.getLocationLng()), stop.getStatus(),
                stop.getEtaLabel(), stop.getDistanceFromPreviousKm(),
                stop.getOrder() == null ? null : stop.getOrder().getOrderNumber(),
                stop.getContactName(), stop.getContactPhone(),
                stop.getPurchaseOrderShipmentGroup() == null ? null
                        : stop.getPurchaseOrderShipmentGroup().getId(),
                stop.getPurchaseOrderShipmentGroup() == null ? null
                        : stop.getPurchaseOrderShipmentGroup().getShipmentReference(),
                stop.getPurchaseOrderShipmentGroup() == null ? List.of()
                        : stop.getPurchaseOrderShipmentGroup().getShipments().stream()
                            .map(shipment -> shipment.getPurchaseOrder().getPurchaseOrderNumber()).toList());
    }

    @Transactional(readOnly = true)
    public TripResponse get(AuthenticatedUser actor, UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Trip not found"));
        if (actor == null || actor.role() == null) throw new ApiException(ErrorCode.UNAUTHENTICATED);
        boolean allowed = actor.role() == UserRole.SUPER_ADMIN
                || tripAccessService.canAccess(actor, tripId);
        if (!allowed) throw new ApiException(ErrorCode.ACCESS_DENIED);
        return actor.role() == UserRole.CUSTOMER
                ? customerResponse(trip, actor.userId())
                : TripResponse.from(trip, aggregateItems(trip));
    }

    @Transactional
    public TripResponse updateStatus(AuthenticatedUser actor, UUID tripId, TripStatus requested,
                                     String note) {
        Trip trip = getOrganizationTripForUpdate(actor, tripId);
        if (!isAllowedTransition(trip.getStatus(), requested)) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Trip cannot transition from " + trip.getStatus() + " to " + requested);
        }
        if (requested == TripStatus.COMPLETED && !allStopsCompleted(trip)) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "A trip can only be completed after all stops are completed");
        }
        TripStatus previous = trip.getStatus();
        trip.setStatus(requested);
        Trip saved = tripRepository.save(trip);
        if (requested == TripStatus.CANCELLED) {
            if (previous == TripStatus.SCHEDULED) releaseManufacturerShipmentGroups(saved, false);
            for (Shipment shipment : shipmentRepository.findByTrip_Id(saved.getId())) {
                if (shipment.getStatus() == ShipmentStatus.ASSIGNED) {
                    shipment.setTrip(null);
                    shipment.setStatus(ShipmentStatus.READY);
                    shipmentRepository.save(shipment);
                }
            }
        }
        recordStatus(saved, previous, requested, actor,
                note == null || note.isBlank() ? "Trip status updated" : note.trim());
        return TripResponse.from(saved, aggregateItems(saved));
    }

    private void releaseManufacturerShipmentGroups(Trip trip, boolean deletingTrip) {
        for (PurchaseOrderShipmentGroup group : purchaseOrderShipmentGroupRepository
                .findByTrip_IdForUpdate(trip.getId())) {
            if (group.getStatus() == ManufacturerShipmentStatus.IN_TRANSIT
                    || group.getStatus() == ManufacturerShipmentStatus.DELIVERED
                    || group.getStatus() == ManufacturerShipmentStatus.RECEIVED) {
                if (deletingTrip) {
                    throw new ApiException(ErrorCode.CONFLICT,
                            "A trip with a moving or completed manufacturer shipment cannot be deleted");
                }
                continue;
            }
            if (group.getStatus() == ManufacturerShipmentStatus.TRIP_ASSIGNED) {
                group.setStatus(ManufacturerShipmentStatus.CREATED);
            }
            group.setTrip(null);
            group.setDispatchedAt(null);
            purchaseOrderShipmentGroupRepository.save(group);
        }
    }

    @Transactional
    public TripResponse dispatch(AuthenticatedUser actor, UUID tripId) {
        Trip trip = getOrganizationTripForUpdate(actor, tripId);
        if (trip.getStatus() != TripStatus.SCHEDULED) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only a scheduled trip can be dispatched");
        }
        if (trip.getRider() == null || trip.getVehicle() == null) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Assign an approved driver and active vehicle before dispatch");
        }
        if (trip.getStops() == null || trip.getStops().isEmpty()) {
            throw new ApiException(ErrorCode.CONFLICT, "A trip needs at least one stop before dispatch");
        }
        if (trip.getTripType() == TripType.DELIVERY) {
            // Standalone freight shipments are linked on shipment.trip; their stops
            // do not carry a customer order or manufacturer shipment group.
            List<Shipment> freightShipments = shipmentRepository.findByTrip_Id(trip.getId());
            boolean hasFreightShipments = !freightShipments.isEmpty();
            for (TripStop stop : trip.getStops()) {
                if (stop.getPurchaseOrderShipmentGroup() != null) {
                    PurchaseOrderShipmentGroup group = stop.getPurchaseOrderShipmentGroup();
                    if (group.getStatus() != ManufacturerShipmentStatus.TRIP_ASSIGNED
                            || !trip.getOrganization().getId().equals(group.getManufacturerOrganization().getId())) {
                        throw new ApiException(ErrorCode.CONFLICT, "Manufacturer shipment group is not assigned to this trip");
                    }
                    for (PurchaseOrderShipment consignment : group.getShipments()) {
                        purchaseOrderShipmentService.updateStatus(actor, consignment.getId(),
                                new ShipmentStatusRequest(PurchaseOrderShipmentStatus.IN_TRANSIT,
                                        "Dispatched on trip " + trip.getTripNumber()));
                    }
                    group.setStatus(ManufacturerShipmentStatus.IN_TRANSIT);
                    group.setDispatchedAt(java.time.OffsetDateTime.now());
                    purchaseOrderShipmentGroupRepository.save(group);
                    continue;
                }
                if (stop.getOrder() != null) {
                    orderService.dispatchFromTrip(actor, stop.getOrder().getId());
                    shipmentService.updateOrderDispatched(stop.getOrder().getId(), trip.getId(), actor);
                    otpService.issueDelivery(stop.getOrder().getCustomer(), stop);
                    continue;
                }
                if (!hasFreightShipments) {
                    throw new ApiException(ErrorCode.CONFLICT,
                            "Every delivery trip stop must reference a customer order");
                }
            }
            for (Shipment shipment : freightShipments) {
                if (shipment.getStatus() == ShipmentStatus.ASSIGNED) {
                    shipment.setStatus(ShipmentStatus.IN_TRANSIT);
                    shipmentRepository.save(shipment);
                }
            }
        }
        TripStatus previous = trip.getStatus();
        trip.setStatus(TripStatus.ONGOING);
        trip.getStops().stream()
                .filter(stop -> stop.getStatus() == StopStatus.PENDING)
                .min(java.util.Comparator.comparingInt(TripStop::getSequence))
                .ifPresent(stop -> stop.setStatus(StopStatus.IN_PROGRESS));
        Trip saved = tripRepository.save(trip);
        recordStatus(saved, previous, TripStatus.ONGOING, actor, "Trip dispatched");
        return TripResponse.from(saved, aggregateItems(saved));
    }

    private void sendDeliveryConfirmation(Order order) {
        if (order.getDeliveryEmailSentAt() != null) return;
        order.setDeliveryEmailStatus("PENDING");
        try {
            String recipient = order.getCustomer().getEmail();
            if (recipient == null || recipient.isBlank()) {
                throw new IllegalStateException("Recipient email is not available");
            }
            String subject = "Your LogiFlow Order Has Arrived";
            String body = "Your order has arrived at its destination and has been successfully handed over.\n"
                    + "Order reference: " + order.getOrderNumber() + "\n"
                    + "Delivery address: " + (order.getDeliveryAddress() == null ? "Not specified" : order.getDeliveryAddress())
                    + "\nThank you for using LogiFlow.";
            emailProviderClient.send(recipient, subject, body);
            order.setDeliveryEmailStatus("SENT");
            order.setDeliveryEmailSentAt(java.time.OffsetDateTime.now());
            order.setDeliveryEmailError(null);
        } catch (Exception ex) {
            order.setDeliveryEmailStatus("FAILED");
            order.setDeliveryEmailError(ex.getMessage() == null ? "Email delivery failed" : ex.getMessage());
            log.warn("Delivery confirmation email failed for order {}", order.getOrderNumber());
        }
    }

    @Transactional(readOnly = true)
    public List<LocationPingResponse> tracking(AuthenticatedUser actor, UUID tripId) {
        return locationHistory(actor, tripId);
    }

    @Transactional
    public TripOperationalActionResponse operationalAction(
            AuthenticatedUser actor, UUID tripId, TripOperationalActionRequest request) {
        Trip trip = getOrganizationTripForUpdate(actor, tripId);
        TripOperationalActionType action = request.action();
        if (action == null) throw new ApiException(ErrorCode.BAD_REQUEST, "Action is required");
        String note = request.note() == null ? "" : request.note().trim();
        if (note.isBlank()) throw new ApiException(ErrorCode.BAD_REQUEST, "Action note is required");
        if (actor.role() == UserRole.DRIVER) {
            if (trip.getRider() == null || !actor.userId().equals(trip.getRider().getId())
                    || (action != TripOperationalActionType.REPORT_DELAY
                    && action != TripOperationalActionType.REROUTE)) {
                throw new ApiException(ErrorCode.ACCESS_DENIED);
            }
        } else if (actor.role() != UserRole.MANUFACTURER_ADMIN
                && actor.role() != UserRole.MANUFACTURER_STAFF
                && actor.role() != UserRole.DISTRIBUTOR_ADMIN
                && actor.role() != UserRole.DISTRIBUTOR_STAFF) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }

        TripStatus previous = trip.getStatus();
        switch (action) {
            case REPORT_DELAY -> {
                if (trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED) {
                    throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                            "A closed trip cannot be marked delayed");
                }
                trip.setStatus(TripStatus.DELAYED);
            }
            case RESUME -> {
                if (trip.getStatus() != TripStatus.DELAYED) {
                    throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                            "Only a delayed trip can be resumed");
                }
                trip.setStatus(TripStatus.ONGOING);
            }
            case CANCEL -> {
                if (!isAllowedTransition(trip.getStatus(), TripStatus.CANCELLED)) {
                    throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                            "This trip cannot be cancelled in its current state");
                }
                trip.setStatus(TripStatus.CANCELLED);
            }
            case PAUSE, REROUTE, REQUEST_DRIVER_UPDATE -> {
                if (trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED) {
                    throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                            "A closed trip cannot receive operational actions");
                }
            }
        }
        Trip saved = tripRepository.save(trip);
        if (previous != saved.getStatus()) {
            recordStatus(saved, previous, saved.getStatus(), actor, note);
        }
        TripOperationalAction actionEntity = new TripOperationalAction();
        actionEntity.setTrip(saved);
        actionEntity.setActor(userRepository.getReferenceById(actor.userId()));
        actionEntity.setActionType(action);
        actionEntity.setNote(note);
        return TripOperationalActionResponse.from(tripOperationalActionRepository.save(actionEntity));
    }

    @Transactional(readOnly = true)
    public Page<TripOperationalActionResponse> operationalActions(
            AuthenticatedUser actor, UUID tripId, Pageable pageable) {
        if (actor == null || actor.role() == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        if (actor.role() == UserRole.CUSTOMER
                || (actor.role() != UserRole.SUPER_ADMIN
                && !tripAccessService.canAccess(actor, tripId))) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return tripOperationalActionRepository.findByTrip_IdOrderByCreatedAtDesc(tripId, pageable)
                .map(TripOperationalActionResponse::from);
    }

    @Transactional(readOnly = true)
    public List<TripResponse> getActiveTripsForRider(UUID riderId) {
        return tripRepository.findByRiderIdAndStatusIn(riderId,
                        List.of(TripStatus.SCHEDULED, TripStatus.ONGOING, TripStatus.DELAYED))
                .stream().map(t -> TripResponse.from(t, aggregateItems(t))).toList();
    }

    @Transactional(readOnly = true)
    public List<TripResponse> getTripHistoryForRider(UUID riderId) {
        return tripRepository.findByRiderIdAndStatusIn(riderId,
                        List.of(TripStatus.COMPLETED, TripStatus.CANCELLED))
                .stream().map(t -> TripResponse.from(t, aggregateItems(t))).toList();
    }

    private Trip getOrganizationTripForUpdate(AuthenticatedUser actor, UUID tripId) {
        if (actor == null) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        if (actor.role() == UserRole.SUPER_ADMIN) {
            return tripRepository.findByIdForUpdate(tripId)
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Trip not found"));
        }
        if (actor.organizationId() == null) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return tripRepository.findByIdAndOrganizationForUpdate(tripId, actor.organizationId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Trip not found"));
    }

    private Trip getAssignedTrip(AuthenticatedUser actor, UUID tripId) {
        if (actor == null || actor.role() != UserRole.DRIVER) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Only the assigned driver may perform this action");
        }
        return tripRepository.findByIdAndRiderForUpdate(tripId, actor.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Trip is not assigned to this driver"));
    }

    private UUID requireOrganization(AuthenticatedUser actor) {
        if (actor == null || actor.organizationId() == null
                || actor.role() == UserRole.CUSTOMER || actor.role() == UserRole.DRIVER) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "An organization identity is required");
        }
        return actor.organizationId();
    }

    private static void requireTripPin(Double latitude, Double longitude, String label) {
        if (latitude == null || longitude == null || (latitude == 0.0 && longitude == 0.0)) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    label + " needs a map pin before this shipment can go on a trip.");
        }
    }

    private static boolean isAllowedTransition(TripStatus current, TripStatus requested) {
        if (requested == null || current == requested) return false;
        return switch (current) {
            case SCHEDULED -> requested == TripStatus.ONGOING
                    || requested == TripStatus.DELAYED || requested == TripStatus.CANCELLED;
            case ONGOING -> requested == TripStatus.DELAYED
                    || requested == TripStatus.COMPLETED || requested == TripStatus.CANCELLED;
            case DELAYED -> requested == TripStatus.ONGOING || requested == TripStatus.CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    private void validateStopSequences(TripRequest request) {
        List<Integer> sortedSequences = request.stops().stream()
                .map(TripRequest.TripStopRequest::sequence)
                .sorted()
                .toList();
        for (int index = 0; index < sortedSequences.size(); index++) {
            int expected = index + 1;
            if (sortedSequences.get(index) != expected) {
                throw new ApiException(ErrorCode.BAD_REQUEST,
                        "Trip stop sequence numbers must be consecutive starting at 1");
            }
        }
    }

    private void recordStatus(Trip trip, TripStatus from, TripStatus to,
                              AuthenticatedUser actor, String note) {
        TripStatusHistory history = new TripStatusHistory();
        history.setTrip(trip);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setChangedBy(userRepository.getReferenceById(actor.userId()));
        history.setNote(note);
        tripStatusHistoryRepository.save(history);
    }

    private List<TripItemResponse> aggregateItems(Trip trip) {
        Map<String, TripItemResponse> itemMap = new LinkedHashMap<>();
        boolean directOrderIncluded = false;
        if (trip.getOrder() != null) {
            addOrderItems(itemMap, trip.getOrder());
            directOrderIncluded = true;
        }
        for (TripStop stop : trip.getStops()) {
            if (stop.getOrder() != null) {
                if (!directOrderIncluded || !stop.getOrder().getId().equals(trip.getOrder().getId())) {
                    addOrderItems(itemMap, stop.getOrder());
                }
            } else if (stop.getStockItem() != null) {
                addStockItem(itemMap, stop.getStockItem(), stop.getRestockQuantity());
            }
        }
        return new ArrayList<>(itemMap.values());
    }

    private TripResponse customerResponse(Trip trip, UUID customerId) {
        Map<String, TripItemResponse> itemMap = new LinkedHashMap<>();
        if (trip.getOrder() != null && trip.getOrder().getCustomer() != null
                && customerId.equals(trip.getOrder().getCustomer().getId())) {
            addOrderItems(itemMap, trip.getOrder());
        }
        for (TripStop stop : trip.getStops()) {
            if (stop.getOrder() != null && stop.getOrder().getCustomer() != null
                    && customerId.equals(stop.getOrder().getCustomer().getId())
                    && (trip.getOrder() == null || !stop.getOrder().getId().equals(trip.getOrder().getId()))) {
                addOrderItems(itemMap, stop.getOrder());
            }
        }
        return TripResponse.fromCustomer(trip, new ArrayList<>(itemMap.values()), customerId);
    }

    private static void addOrderItems(Map<String, TripItemResponse> itemMap, Order order) {
        for (OrderItem item : order.getOrderItems()) {
            StockItem stock = item.getStockItem();
            String key = stock.getId().toString();
            TripItemResponse current = itemMap.getOrDefault(key,
                    new TripItemResponse(key, stock.getName(), stock.getSku(), "unit", 0));
            itemMap.put(key, new TripItemResponse(current.id(), current.productName(), current.brand(),
                    current.unitLabel(), current.quantity() + item.getQuantity()));
        }
    }

    private static void addStockItem(Map<String, TripItemResponse> itemMap, StockItem stock, Integer quantity) {
        String key = stock.getId().toString();
        TripItemResponse current = itemMap.getOrDefault(key,
                new TripItemResponse(key, stock.getName(), stock.getSku(), "unit", 0));
        itemMap.put(key, new TripItemResponse(current.id(), current.productName(), current.brand(),
                current.unitLabel(), current.quantity() + (quantity == null ? 0 : quantity)));
    }

    private static boolean allStopsCompleted(Trip trip) {
        return !trip.getStops().isEmpty()
                && trip.getStops().stream().allMatch(stop -> stop.getStatus() == StopStatus.COMPLETED);
    }
}
