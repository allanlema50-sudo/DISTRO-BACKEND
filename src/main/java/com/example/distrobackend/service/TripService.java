package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.*;
import com.example.distrobackend.Domain.enums.*;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.*;
import com.example.distrobackend.repository.*;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.security.TripAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

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
    private final TripAccessService tripAccessService;
    private final OtpService otpService;
    private final OrderService orderService;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public TripResponse createTrip(AuthenticatedUser actor, TripRequest request) {
        UUID organizationId = requireOrganization(actor);
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Organization not found"));

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
        Trip trip = getOrganizationTrip(actor, tripId);
        User rider = userRepository.findById(riderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Rider not found"));
        if (rider.getRole() != UserRole.DRIVER || rider.getOrganization() == null
                || !trip.getOrganization().getId().equals(rider.getOrganization().getId())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Rider is not valid for this organization");
        }
        if (trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Completed or cancelled trips cannot be assigned");
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
        for (TripStop stop : trip.getStops()) {
            if (stop.getOrder() != null) {
                for (OrderItem item : stop.getOrder().getOrderItems()) {
                    StockItem stock = item.getStockItem();
                    String key = stock.getId().toString();
                    TripItemResponse current = itemMap.getOrDefault(key,
                            new TripItemResponse(key, stock.getName(), stock.getSku(), "unit", 0));
                    itemMap.put(key, new TripItemResponse(current.id(), current.productName(), current.brand(),
                            current.unitLabel(), current.quantity() + item.getQuantity()));
                }
            } else if (stop.getStockItem() != null) {
                StockItem stock = stop.getStockItem();
                String key = stock.getId().toString();
                TripItemResponse current = itemMap.getOrDefault(key,
                        new TripItemResponse(key, stock.getName(), stock.getSku(), "unit", 0));
                itemMap.put(key, new TripItemResponse(current.id(), current.productName(), current.brand(),
                        current.unitLabel(), current.quantity() + stop.getRestockQuantity()));
            }
        }
        return new ArrayList<>(itemMap.values());
    }
}
