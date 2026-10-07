package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.*;
import com.example.distrobackend.Domain.enums.StopStatus;
import com.example.distrobackend.Domain.enums.TripStatus;
import com.example.distrobackend.Domain.enums.TripType;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.*;
import com.example.distrobackend.repository.*;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
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
    private final OtpService otpService;

    @Transactional
    public TripResponse createTrip(AuthenticatedUser user, TripRequest request) {
        if (user == null || user.organizationId() == null) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "An organization is required to create a trip");
        }
        Organization organization = organizationRepository.findById(user.organizationId())
                .orElseThrow(() -> new ApiException(ErrorCode.ACCESS_DENIED));

        Trip trip = new Trip();
        trip.setOrganization(organization);
        trip.setTripType(request.tripType());
        // generate trip number
        trip.setTripNumber("TRIP-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 16).toUpperCase(Locale.ROOT));
        trip.setStatus(TripStatus.SCHEDULED);
        trip.setOriginName(request.origin().name());
        trip.setOriginAddress(request.origin().address());
        trip.setOriginLat(request.origin().latitude());
        trip.setOriginLng(request.origin().longitude());
        trip.setScheduledStartLabel(request.scheduledStartLabel());
        trip.setTotalDistanceKm(request.totalDistanceKm());

        Set<Integer> sequences = new HashSet<>();
        for (TripRequest.TripStopRequest stopReq : request.stops()) {
                if (!sequences.add(stopReq.sequence())) {
                    throw new ApiException(ErrorCode.BAD_REQUEST, "Trip stop sequences must be unique");
                }
                TripStop stop = new TripStop();
                stop.setSequence(stopReq.sequence());
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
                                "Delivery stops require an order and cannot reference stock directly");
                    }
                    Order order = orderRepository.findById(stopReq.orderId())
                            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found: " + stopReq.orderId()));
                    if (order.getOrganization() == null
                            || !organization.getId().equals(order.getOrganization().getId())) {
                        throw new ApiException(ErrorCode.ACCESS_DENIED, "The order does not belong to your organization");
                    }
                    stop.setOrder(order);
                } else if (request.tripType() == TripType.RESTOCK) {
                    if (stopReq.stockItemId() == null || stopReq.orderId() != null
                            || stopReq.restockQuantity() == null || stopReq.restockQuantity() < 1) {
                        throw new ApiException(ErrorCode.BAD_REQUEST,
                                "Restock stops require a stock item and positive quantity");
                    }
                    StockItem stockItem = stockItemRepository.findByIdAndOrganization_Id(
                                    stopReq.stockItemId(), organization.getId())
                            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "StockItem not found: " + stopReq.stockItemId()));
                    stop.setStockItem(stockItem);
                    stop.setRestockQuantity(stopReq.restockQuantity());
                }

                trip.addStop(stop);
        }

        Trip saved = tripRepository.save(trip);
        return TripResponse.from(saved, aggregateItems(saved));
    }

    @Transactional
    public TripResponse assignRider(AuthenticatedUser user, UUID tripId, UUID riderId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Trip not found"));
        if (user == null || user.organizationId() == null || trip.getOrganization() == null
                || !user.organizationId().equals(trip.getOrganization().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }

        User rider = userRepository.findById(riderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Rider not found"));
        if (rider.getRole() != UserRole.DRIVER) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Only drivers may be assigned to trips");
        }
        if (rider.getOrganization() != null
                && !user.organizationId().equals(rider.getOrganization().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "The driver does not belong to your organization");
        }

        trip.setRider(rider);
        Trip saved = tripRepository.save(trip);
        return TripResponse.from(saved, aggregateItems(saved));
    }

    @Transactional
    public TripResponse confirmStopDelivery(UUID driverId, UUID tripId, UUID stopId, ConfirmStopRequest request) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Trip not found"));
        if (trip.getRider() == null || !trip.getRider().getId().equals(driverId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Only the assigned driver may confirm this stop");
        }

        TripStop stop = trip.getStops().stream()
                .filter(s -> s.getId().equals(stopId))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Stop not found in this trip"));

        if (stop.getStatus() == StopStatus.COMPLETED) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Stop already completed");
        }

        if (stop.getOrder() == null || stop.getOrder().getCustomer() == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Only delivery stops support OTP confirmation");
        }
        otpService.verify(stop.getOrder().getCustomer(),
                com.example.distrobackend.Domain.enums.OtpPurpose.DELIVERY_CONFIRMATION,
                request.otp());

        stop.setStatus(StopStatus.COMPLETED);

        // Advance next stop to IN_PROGRESS if pending
        trip.getStops().stream()
                .filter(s -> s.getStatus() == StopStatus.PENDING && s.getSequence() == stop.getSequence() + 1)
                .findFirst()
                .ifPresent(next -> next.setStatus(StopStatus.IN_PROGRESS));

        // Mark trip as complete if all stops are completed
        boolean allDone = trip.getStops().stream().allMatch(s -> s.getStatus() == StopStatus.COMPLETED);
        if (allDone) {
            trip.setStatus(TripStatus.COMPLETED);
        } else if (trip.getStatus() == TripStatus.SCHEDULED) {
            trip.setStatus(TripStatus.ONGOING);
        }

        Trip saved = tripRepository.save(trip);
        return TripResponse.from(saved, aggregateItems(saved));
    }

    @Transactional
    public void issueDeliveryOtp(UUID customerId, UUID tripId, UUID stopId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Trip not found"));
        TripStop stop = trip.getStops().stream()
                .filter(candidate -> candidate.getId().equals(stopId))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Stop not found in this trip"));
        if (stop.getOrder() == null || stop.getOrder().getCustomer() == null
                || !stop.getOrder().getCustomer().getId().equals(customerId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        otpService.issue(stop.getOrder().getCustomer(),
                com.example.distrobackend.Domain.enums.OtpPurpose.DELIVERY_CONFIRMATION);
    }

    @Transactional(readOnly = true)
    public List<TripResponse> getActiveTripsForRider(UUID riderId) {
        return tripRepository.findByRiderIdAndStatusIn(riderId,
                List.of(TripStatus.SCHEDULED, TripStatus.ONGOING, TripStatus.DELAYED))
                .stream()
                .map(t -> TripResponse.from(t, aggregateItems(t)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TripResponse> getTripHistoryForRider(UUID riderId) {
        return tripRepository.findByRiderIdAndStatusIn(riderId,
                List.of(TripStatus.COMPLETED, TripStatus.CANCELLED))
                .stream()
                .map(t -> TripResponse.from(t, aggregateItems(t)))
                .collect(Collectors.toList());
    }

    // Helper to calculate aggregated items on board based on all orders/restocks in the trip stops
    private List<TripItemResponse> aggregateItems(Trip trip) {
        Map<String, TripItemResponse> itemMap = new LinkedHashMap<>();

        for (TripStop stop : trip.getStops()) {
            if (stop.getOrder() != null) {
                for (OrderItem oi : stop.getOrder().getOrderItems()) {
                    StockItem si = oi.getStockItem();
                    String key = si.getId().toString();
                    TripItemResponse existing = itemMap.getOrDefault(key,
                            new TripItemResponse(key, si.getName(), si.getSku(), "unit", 0)); // sku as brand for now

                    itemMap.put(key, new TripItemResponse(
                            existing.id(), existing.productName(), existing.brand(), existing.unitLabel(),
                            existing.quantity() + oi.getQuantity()));
                }
            } else if (stop.getStockItem() != null) {
                StockItem si = stop.getStockItem();
                String key = si.getId().toString();
                TripItemResponse existing = itemMap.getOrDefault(key,
                        new TripItemResponse(key, si.getName(), si.getSku(), "unit", 0));

                itemMap.put(key, new TripItemResponse(
                        existing.id(), existing.productName(), existing.brand(), existing.unitLabel(),
                        existing.quantity() + (stop.getRestockQuantity() != null ? stop.getRestockQuantity() : 0)));
            }
        }

        return new ArrayList<>(itemMap.values());
    }
}
