package com.example.distrobackend.security;

import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Performs the authorization decision for a trip tracking subscription.
 * The caller receives only a boolean so an unauthorized user cannot use this
 * boundary to distinguish a missing trip from an inaccessible trip.
 */
@Service
@RequiredArgsConstructor
public class TripAccessService {

    private final TripRepository tripRepository;

    public boolean canSubscribe(UUID tripId, AuthenticatedUser user) {
        if (tripId == null || user == null) {
            return false;
        }

        UserRole role = user.role();
        return switch (role) {
            case DRIVER -> tripRepository.existsByIdAndRider_Id(tripId, user.userId());
            case CUSTOMER -> tripRepository.existsByIdAndOrder_Customer_Id(tripId, user.userId());
            case MANUFACTURER_ADMIN, MANUFACTURER_STAFF,
                    DISTRIBUTOR_ADMIN, DISTRIBUTOR_STAFF -> user.organizationId() != null
                    && tripRepository.existsByIdAndOrganization_Id(tripId, user.organizationId());
        };
    }
}
