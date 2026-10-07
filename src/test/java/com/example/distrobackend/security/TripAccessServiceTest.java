package com.example.distrobackend.security;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.Trip;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.repository.TripRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripAccessServiceTest {

    @Mock
    private TripRepository tripRepository;

    @InjectMocks
    private TripAccessService tripAccessService;

    @Test
    void assignedDriverCanAccessTrip() {
        UUID tripId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Trip trip = tripWithOrganization(UUID.randomUUID());
        User driver = new User();
        driver.setId(driverId);
        driver.setRole(UserRole.DRIVER);
        trip.setRider(driver);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        assertThat(tripAccessService.canAccess(principal(driverId, UserRole.DRIVER, null), tripId)).isTrue();
    }

    @Test
    void driverCannotAccessAnotherDriversTrip() {
        UUID tripId = UUID.randomUUID();
        Trip trip = tripWithOrganization(UUID.randomUUID());
        User driver = new User();
        driver.setId(UUID.randomUUID());
        driver.setRole(UserRole.DRIVER);
        trip.setRider(driver);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        assertThat(tripAccessService.canAccess(principal(UUID.randomUUID(), UserRole.DRIVER, null), tripId)).isFalse();
    }

    private Trip tripWithOrganization(UUID organizationId) {
        Organization organization = new Organization();
        organization.setId(organizationId);
        organization.setType(Organizationtype.DISTRIBUTOR);
        Trip trip = new Trip();
        trip.setOrganization(organization);
        return trip;
    }

    private AuthenticatedUser principal(UUID userId, UserRole role, UUID organizationId) {
        return new AuthenticatedUser(userId, role,
                organizationId, organizationId == null ? null : Organizationtype.DISTRIBUTOR,
                Instant.now().plus(Duration.ofMinutes(5)));
    }
}
