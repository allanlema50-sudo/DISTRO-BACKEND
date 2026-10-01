package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.*;
import com.example.distrobackend.Domain.enums.StopStatus;
import com.example.distrobackend.Domain.enums.TripStatus;
import com.example.distrobackend.Domain.enums.TripType;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.*;
import com.example.distrobackend.repository.*;
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

    @Transactional
    public TripResponse createTrip(TripRequest request) {
        Trip trip = new Trip();
        trip.setTripType(request.tripType());
        // generate trip number
        trip.setTripNumber("TRIP-" + (System.currentTimeMillis() % 100000));
        trip.setStatus(TripStatus.SCHEDULED);
        trip.setOriginName(request.origin().name());
        trip.setOriginAddress(request.origin().address());
        trip.setOriginLat(request.origin().latitude());
        trip.setOriginLng(request.origin().longitude());
        trip.setScheduledStartLabel(request.scheduledStartLabel());
        trip.setTotalDistanceKm(request.totalDistanceKm());

        if (request.stops() != null) {
            for (TripRequest.TripStopRequest stopReq : request.stops()) {
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
                
                if (request.tripType() == TripType.DELIVERY && stopReq.orderId() != null) {
                    Order order = orderRepository.findById(stopReq.orderId())
                            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found: " + stopReq.orderId()));
                    stop.setOrder(order);
                } else if (request.tripType() == TripType.RESTOCK && stopReq.stockItemId() != null) {
                    StockItem stockItem = stockItemRepository.findById(stopReq.stockItemId())
                            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "StockItem not found: " + stopReq.stockItemId()));
                    stop.setStockItem(stockItem);
                    stop.setRestockQuantity(stopReq.restockQuantity());
                }

                trip.addStop(stop);
            }
        }

        Trip saved = tripRepository.save(trip);
        return TripResponse.from(saved, aggregateItems(saved));
    }

    @Transactional
    public TripResponse assignRider(UUID tripId, UUID riderId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Trip not found"));

        User rider = userRepository.findById(riderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Rider not found"));

        trip.setRider(rider);
        Trip saved = tripRepository.save(trip);
        return TripResponse.from(saved, aggregateItems(saved));
    }

    @Transactional
    public TripResponse confirmStopDelivery(UUID tripId, UUID stopId, ConfirmStopRequest request) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Trip not found"));

        TripStop stop = trip.getStops().stream()
                .filter(s -> s.getId().equals(stopId))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Stop not found in this trip"));

        if (stop.getStatus() == StopStatus.COMPLETED) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Stop already completed");
        }

        // Mock OTP validation - frontend mock says "184279" is the hardcoded test OTP
        if (!"184279".equals(request.otp())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Invalid OTP");
        }

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
