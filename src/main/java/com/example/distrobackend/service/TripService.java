package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.*;
import com.example.distrobackend.Domain.enums.*;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.*;
import com.example.distrobackend.repository.*;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.security.TripAccessService;
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
    private final TripAccessService tripAccessService;
    private final OtpService otpService;
    private final OrderService orderService;
    private final SimpMessagingTemplate messagingTemplate;

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
        Trip trip = getOrganizationTripForUpdate(actor, tripId);
        User rider = userRepository.findByIdForUpdate(riderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Rider not found"));
        if (rider.getRole() != UserRole.DRIVER || rider.getOrganization() == null
                || !trip.getOrganization().getId().equals(rider.getOrganization().getId())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Rider is not valid for this organization");
        }
        if (rider.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CONFLICT, "Only an active driver can be assigned");
        }
        if (trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Completed or cancelled trips cannot be assigned");
        }
        List<TripStatus> activeStatuses = List.of(TripStatus.SCHEDULED, TripStatus.ONGOING, TripStatus.DELAYED);
        if (tripRepository.existsByRider_IdAndStatusInAndIdNot(riderId, activeStatuses, tripId)) {
            throw new ApiException(ErrorCode.CONFLICT, "The driver is already assigned to another active trip");
        }
        if (vehicleId != null) {
            Vehicle vehicle = vehicleRepository.findByIdAndOrganizationForUpdate(
                            vehicleId, trip.getOrganization().getId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Vehicle not found"));
            if (vehicle.getStatus() != VehicleStatus.ACTIVE) {
                throw new ApiException(ErrorCode.CONFLICT, "Only an active vehicle can be assigned");
            }
            if (tripRepository.existsByVehicle_IdAndStatusInAndIdNot(vehicleId, activeStatuses, tripId)) {
                throw new ApiException(ErrorCode.CONFLICT, "The vehicle is already assigned to another active trip");
            }
            trip.setVehicle(vehicle);
        } else if (clearVehicle) {
            trip.setVehicle(null);
        }
        trip.setRider(rider);
        Trip saved = tripRepository.save(trip);
        return TripResponse.from(saved, aggregateItems(saved));
    }

    @Transactional
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
        otpService.verify(stop.getOrder().getCustomer(), OtpPurpose.DELIVERY_CONFIRMATION, request.otp());
        if (stop.getOrder().getStatus() != OrderStatus.IN_TRANSIT) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Order is not in transit");
        }

        stop.setStatus(StopStatus.COMPLETED);
        orderService.updateOrderStatus(stop.getOrder().getId(), actor.userId(),
                new OrderStatusUpdate(OrderStatus.DELIVERED, "Delivery confirmed by customer OTP"));
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
        otpService.issue(stop.getOrder().getCustomer(), OtpPurpose.DELIVERY_CONFIRMATION);
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
        if (actor == null || actor.role() == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        return switch (actor.role()) {
            case SUPER_ADMIN -> tripRepository.findAll(pageable)
                    .map(trip -> TripResponse.from(trip, aggregateItems(trip)));
            case DRIVER -> tripRepository.findByRiderId(actor.userId(), pageable)
                    .map(trip -> TripResponse.from(trip, aggregateItems(trip)));
            case CUSTOMER -> tripRepository.findByCustomerId(actor.userId(), pageable)
                    .map(trip -> customerResponse(trip, actor.userId()));
            case MANUFACTURER_ADMIN, MANUFACTURER_STAFF,
                    DISTRIBUTOR_ADMIN, DISTRIBUTOR_STAFF -> {
                if (actor.organizationId() == null) throw new ApiException(ErrorCode.ACCESS_DENIED);
                yield tripRepository.findByOrganization_Id(actor.organizationId(), pageable)
                        .map(trip -> TripResponse.from(trip, aggregateItems(trip)));
            }
        };
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
        recordStatus(saved, previous, requested, actor,
                note == null || note.isBlank() ? "Trip status updated" : note.trim());
        return TripResponse.from(saved, aggregateItems(saved));
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
            case PAUSE, REROUTE -> {
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

    private Trip getOrganizationTrip(AuthenticatedUser actor, UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Trip not found"));
        if (actor == null || actor.organizationId() == null || trip.getOrganization() == null
                || !actor.organizationId().equals(trip.getOrganization().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return trip;
    }

    private Trip getOrganizationTripForUpdate(AuthenticatedUser actor, UUID tripId) {
        if (actor == null || actor.organizationId() == null) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return tripRepository.findByIdAndOrganizationForUpdate(tripId, actor.organizationId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Trip not found"));
    }

    private Trip getAssignedTrip(AuthenticatedUser actor, UUID tripId) {
        if (actor == null || actor.role() != UserRole.DRIVER) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Only the assigned driver may perform this action");
        }
        Trip trip = getOrganizationTrip(actor, tripId);
        if (trip.getRider() == null || !actor.userId().equals(trip.getRider().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Driver is not assigned to this trip");
        }
        return trip;
    }

    private UUID requireOrganization(AuthenticatedUser actor) {
        if (actor == null || actor.organizationId() == null
                || actor.role() == UserRole.CUSTOMER || actor.role() == UserRole.DRIVER) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "An organization identity is required");
        }
        return actor.organizationId();
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
