package com.example.distrobackend.security;

import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.repository.TripRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripAccessServiceTest {

    @Mock
    private TripRepository tripRepository;

    @InjectMocks
    private TripAccessService tripAccessService;

    @Test
    void driverCanSubscribeOnlyToAssignedTrip() {
        UUID tripId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        AuthenticatedUser user = user(driverId, UserRole.DRIVER, null);
        when(tripRepository.existsByIdAndRider_Id(tripId, driverId)).thenReturn(true);

        assertThat(tripAccessService.canSubscribe(tripId, user)).isTrue();
        verify(tripRepository).existsByIdAndRider_Id(tripId, driverId);
    }

    @Test
    void customerCannotSubscribeToAnotherCustomersTrip() {
        UUID tripId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        AuthenticatedUser user = user(customerId, UserRole.CUSTOMER, null);
        when(tripRepository.existsByIdAndCustomerAccess(tripId, customerId)).thenReturn(false);

        assertThat(tripAccessService.canSubscribe(tripId, user)).isFalse();
        verify(tripRepository).existsByIdAndCustomerAccess(tripId, customerId);
    }

    @Test
    void organizationUserRequiresOrganizationOwnedTrip() {
        UUID tripId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        AuthenticatedUser user = user(userId, UserRole.DISTRIBUTOR_STAFF, organizationId);
        when(tripRepository.existsByIdAndOrganization_Id(tripId, organizationId)).thenReturn(true);

        assertThat(tripAccessService.canSubscribe(tripId, user)).isTrue();
        verify(tripRepository).existsByIdAndOrganization_Id(tripId, organizationId);
    }

    @Test
    void organizationUserWithoutOrganizationClaimIsDenied() {
        AuthenticatedUser user = user(UUID.randomUUID(), UserRole.MANUFACTURER_STAFF, null);

        assertThat(tripAccessService.canSubscribe(UUID.randomUUID(), user)).isFalse();
        verifyNoInteractions(tripRepository);
    }

    private static AuthenticatedUser user(UUID userId, UserRole role, UUID organizationId) {
        return new AuthenticatedUser(
                userId, role, organizationId, null, Instant.now().plusSeconds(60));
    }
}
