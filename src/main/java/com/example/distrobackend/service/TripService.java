package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Trip;
import com.example.distrobackend.Domain.enums.TripStatus;
import com.example.distrobackend.Domain.enums.TripType;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.dto.TripResponse;
import com.example.distrobackend.repository.TripRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;

    @Transactional(readOnly = true)
    public Page<TripResponse> getDeliveries(
            AuthenticatedUser me,
            int page,
            int size,
            String search
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String safeSearch = search == null ? "" : search.trim();

        PageRequest pageable = PageRequest.of(
                safePage,
                safeSize
        );

        Page<Trip> trips;

        if (me.role().isPlatformAdmin()) {

            trips = tripRepository.searchDeliveryTrips(
                    TripType.DELIVERY,
                    safeSearch,
                    pageable
            );

        } else {

            if (me.organizationId() == null) {
                throw new IllegalStateException(
                        "Authenticated user does not belong to an organization"
                );
            }

            trips = tripRepository.searchDeliveryTripsByOrganizationId(
                    TripType.DELIVERY,
                    me.organizationId(),
                    safeSearch,
                    pageable
            );
        }

        return trips.map(this::toResponse);
    }

    private TripResponse toResponse(Trip trip) {

        var order = trip.getOrder();

        String customerName = null;
        String customerPhone = null;
        String orderId = null;
        String deliveryAddress = null;

        if (order != null) {
            orderId = order.getOrderNumber();
            deliveryAddress = order.getDeliveryAddress();

            if (order.getCustomer() != null) {
                customerName = order.getCustomer().getFullName();
                customerPhone = order.getCustomer().getPhoneNumber();
            }
        }

        String driverName = null;
        java.util.UUID driverId = null;

        if (trip.getRider() != null) {
            driverId = trip.getRider().getId();
            driverName = trip.getRider().getFullName();
        }

        return new TripResponse(
                trip.getId(),
                orderId,
                customerName,
                customerPhone,
                deliveryAddress,
                driverName,
                driverId,
                null,
                mapStatus(trip.getStatus()),
                trip.getEstimatedEta(),
                trip.getCompletedAt(),
                trip.getCreatedAt(),
                trip.getUpdatedAt(),
                mapTrackingStatus(trip.getStatus()),
                null
        );
    }

    private String mapStatus(TripStatus status) {

        if (status == null) {
            return "PENDING";
        }

        return switch (status) {
            case UNASSIGNED -> "PENDING";
            case ASSIGNED -> "ASSIGNED";
            case IN_PROGRESS -> "IN_TRANSIT";
            case COMPLETED -> "DELIVERED";
            case CANCELLED -> "CANCELLED";
            case FAILED -> "FAILED";
        };
    }

    private String mapTrackingStatus(TripStatus status) {

        if (status == null) {
            return "Awaiting driver assignment";
        }

        return switch (status) {
            case UNASSIGNED -> "Awaiting driver assignment";
            case ASSIGNED -> "Driver assigned";
            case IN_PROGRESS -> "In transit";
            case COMPLETED -> "Delivered";
            case CANCELLED -> "Cancelled";
            case FAILED -> "Delivery failed";
        };
    }
}