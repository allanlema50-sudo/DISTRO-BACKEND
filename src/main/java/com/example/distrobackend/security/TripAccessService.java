package com.example.distrobackend.security;

import com.example.distrobackend.Domain.entity.Trip;
import com.example.distrobackend.Domain.entity.TripStop;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Centralized authorization for trip data and live tracking feeds. */
@Service
@RequiredArgsConstructor
public class TripAccessService {

    private final TripRepository tripRepository;

    @Transactional(readOnly = true)
    public boolean canAccess(AuthenticatedUser user, UUID tripId) {
        if (user == null || user.isExpired() || tripId == null) {
            return false;
        }

        Trip trip = tripRepository.findById(tripId).orElse(null);
        if (trip == null) {
            return false;
        }

        if (user.role() == UserRole.DRIVER) {
            return trip.getRider() != null && user.userId().equals(trip.getRider().getId());
        }

        if (user.organizationId() != null && trip.getOrganization() != null
                && user.organizationId().equals(trip.getOrganization().getId())) {
            return true;
        }

        if (user.role() == UserRole.CUSTOMER) {
            return trip.getStops().stream()
                    .map(TripStop::getOrder)
                    .filter(order -> order != null && order.getCustomer() != null)
                    .anyMatch(order -> user.userId().equals(order.getCustomer().getId()));
        }

        return false;
    }
}
