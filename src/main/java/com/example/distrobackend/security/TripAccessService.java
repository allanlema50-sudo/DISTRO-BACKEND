package com.example.distrobackend.security;

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
        if (user == null || user.isExpired() || tripId == null || user.role() == null) {
            return false;
        }
        return switch (user.role()) {
            case DRIVER -> tripRepository.existsByIdAndRider_Id(tripId, user.userId());
            case CUSTOMER -> tripRepository.existsByIdAndCustomerAccess(tripId, user.userId());
            case MANUFACTURER_ADMIN, MANUFACTURER_STAFF,
                    DISTRIBUTOR_ADMIN, DISTRIBUTOR_STAFF -> user.organizationId() != null
                    && tripRepository.existsByIdAndOrganization_Id(tripId, user.organizationId());
        };
    }

    public boolean canSubscribe(UUID tripId, AuthenticatedUser user) {
        return canAccess(user, tripId);
    }
}
