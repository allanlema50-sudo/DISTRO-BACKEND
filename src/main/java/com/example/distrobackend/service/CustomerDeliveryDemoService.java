package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Order;
import com.example.distrobackend.Domain.entity.Trip;
import com.example.distrobackend.Domain.entity.TripStop;
import com.example.distrobackend.Domain.enums.StopStatus;
import com.example.distrobackend.Domain.enums.TripStatus;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.DeliveryOtpResponse;
import com.example.distrobackend.dto.DemoDispatchRequest;
import com.example.distrobackend.dto.DemoDispatchResponse;
import com.example.distrobackend.dto.TripResponse;
import com.example.distrobackend.repository.OrderRepository;
import com.example.distrobackend.repository.TripStopRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.UUID;

/**
 * One action for the presentation: pay (if needed), pick, pack, assign a driver
 * and vehicle, dispatch, and return the delivery code the customer phone shows.
 */
@Service
@RequiredArgsConstructor
public class CustomerDeliveryDemoService {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final TripService tripService;
    private final OtpService otpService;
    private final OrderRepository orderRepository;
    private final TripStopRepository tripStopRepository;

    @Value("${app.demo.customer-fulfillment.enabled:false}")
    private boolean enabled;

    @Transactional
    public DemoDispatchResponse dispatch(AuthenticatedUser actor, UUID orderId, DemoDispatchRequest request) {
        if (!enabled) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Demo customer fulfillment is turned off");
        }
        if (actor == null || actor.role() != UserRole.SUPER_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Only a super admin can dispatch this delivery");
        }
        if (request.driverId() == null || request.vehicleId() == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Choose a driver and a vehicle");
        }

        paymentService.confirmPendingDemoPayment(orderId);
        orderService.prepareCustomerOrderForDispatch(actor, orderId);

        Order order = orderRepository.findByIdWithItemsForUpdate(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found"));
        TripStop open = tripStopRepository.findByOrderIdWithTrip(orderId).stream()
                .filter(stop -> stop.getTrip() != null)
                .filter(stop -> {
                    TripStatus status = stop.getTrip().getStatus();
                    return status != TripStatus.COMPLETED && status != TripStatus.CANCELLED;
                })
                .max(Comparator.comparingInt(stop -> stop.getSequence() == null ? 0 : stop.getSequence()))
                .orElse(null);

        UUID tripId;
        String tripNumber;
        UUID stopId;
        String driverName;
        String vehicleRegistration;
        if (open != null && (open.getTrip().getStatus() == TripStatus.ONGOING
                || open.getTrip().getStatus() == TripStatus.DELAYED)) {
            if (open.getStatus() != StopStatus.IN_PROGRESS) {
                open.setStatus(StopStatus.IN_PROGRESS);
                tripStopRepository.save(open);
            }
            Trip live = open.getTrip();
            tripId = live.getId();
            tripNumber = live.getTripNumber();
            stopId = open.getId();
            driverName = live.getRider() == null ? "" : live.getRider().getFullName();
            vehicleRegistration = live.getVehicle() == null || live.getVehicle().getRegistrationNumber() == null
                    ? "" : live.getVehicle().getRegistrationNumber();
        } else {
            TripResponse trip = open != null
                    ? tripService.dispatchExistingCustomerDelivery(
                            actor, open.getTrip().getId(), request.driverId(), request.vehicleId())
                    : tripService.dispatchCustomerDelivery(
                            actor, orderId, request.driverId(), request.vehicleId());
            if (trip.stops() == null || trip.stops().isEmpty()) {
                throw new ApiException(ErrorCode.CONFLICT, "The delivery trip has no stop");
            }
            tripId = UUID.fromString(trip.id());
            tripNumber = trip.tripNumber();
            stopId = trip.stops().stream()
                    .filter(stop -> order.getOrderNumber() != null
                            && order.getOrderNumber().equals(stop.orderNumber()))
                    .map(stop -> UUID.fromString(stop.id()))
                    .findFirst()
                    .orElse(UUID.fromString(trip.stops().get(0).id()));
            driverName = trip.driverName();
            vehicleRegistration = trip.vehicleRegistration();
        }

        DeliveryOtpResponse otp = reveal(order, stopId);
        Order refreshed = orderRepository.findByIdWithItemsForUpdate(orderId).orElse(order);
        return new DemoDispatchResponse(
                refreshed.getId(),
                refreshed.getOrderNumber(),
                refreshed.getStatus().name(),
                tripId,
                tripNumber,
                stopId,
                driverName,
                vehicleRegistration,
                otp.code(),
                "Dispatched. The customer phone shows this code. The driver types it to finish the trip. No money was transferred.");
    }

    private DeliveryOtpResponse reveal(Order order, UUID stopId) {
        try {
            return otpService.currentDeliveryCode(stopId);
        } catch (ApiException ex) {
            TripStop stop = tripStopRepository.findById(stopId)
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Delivery stop not found"));
            if (order.getCustomer() == null) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Order has no customer");
            }
            return otpService.issueDelivery(order.getCustomer(), stop);
        }
    }
}
