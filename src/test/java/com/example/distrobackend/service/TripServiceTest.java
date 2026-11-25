package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.Trip;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.DriverApprovalStatus;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.TripStatus;
import com.example.distrobackend.Domain.enums.TripType;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Domain.enums.UserStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.dto.TripLocation;
import com.example.distrobackend.dto.TripRequest;
import com.example.distrobackend.dto.TripResponse;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.OrderRepository;
import com.example.distrobackend.repository.ShipmentRepository;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.TripLocationPingRepository;
import com.example.distrobackend.repository.TripRepository;
import com.example.distrobackend.repository.TripStatusHistoryRepository;
import com.example.distrobackend.repository.PurchaseOrderShipmentGroupRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.security.TripAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

    @Mock private TripRepository tripRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private StockItemRepository stockItemRepository;
    @Mock private ShipmentRepository shipmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private TripLocationPingRepository locationPingRepository;
    @Mock private TripStatusHistoryRepository tripStatusHistoryRepository;
    @Mock private PurchaseOrderShipmentGroupRepository purchaseOrderShipmentGroupRepository;
    @Mock private TripAccessService tripAccessService;
    @Mock private OtpService otpService;
    @Mock private OrderService orderService;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks private TripService tripService;

    @Test
    void tripStopsMustStartAtOneAndBeConsecutive() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = new Organization();
        organization.setId(organizationId);
        organization.setType(Organizationtype.MANUFACTURER);
        when(organizationRepository.findById(organizationId)).thenReturn(Optional.of(organization));

        TripLocation location = new TripLocation("Warehouse", "Main Road", 0.0, 0.0);
        TripRequest.TripStopRequest stop = new TripRequest.TripStopRequest(
                2, location, null, null, null, null, null, null, null);
        TripRequest request = new TripRequest(TripType.DELIVERY, location, null, null, List.of(stop));

        assertThatThrownBy(() -> tripService.createTrip(principal(organizationId), request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("consecutive");
        verify(tripRepository, never()).save(any());
    }

    @Test
    void superAdminCanCancelAScheduledTripWithoutAnOrganization() {
        UUID tripId = UUID.randomUUID();
        Organization organization = new Organization();
        organization.setId(UUID.randomUUID());
        Trip trip = new Trip();
        trip.setId(tripId);
        trip.setTripNumber("TRIP-1");
        trip.setTripType(TripType.DELIVERY);
        trip.setStatus(TripStatus.SCHEDULED);
        trip.setOrganization(organization);
        when(tripRepository.findByIdForUpdate(tripId)).thenReturn(Optional.of(trip));
        when(tripRepository.save(trip)).thenReturn(trip);
        when(shipmentRepository.findByTrip_Id(tripId)).thenReturn(List.of());
        when(userRepository.getReferenceById(any())).thenReturn(new User());
        AuthenticatedUser admin = new AuthenticatedUser(
                UUID.randomUUID(), UserRole.SUPER_ADMIN, null, null,
                Instant.now().plus(Duration.ofMinutes(5)));

        TripResponse response = tripService.updateStatus(admin, tripId, TripStatus.CANCELLED, null);

        assertThat(response.status()).isEqualTo(TripStatus.CANCELLED);
        verify(tripRepository, never()).findByIdAndOrganizationForUpdate(any(), any());
    }

    @Test
    void driverCannotBeChangedAfterTheTripStarts() {
        UUID tripId = UUID.randomUUID();
        UUID riderId = UUID.randomUUID();
        Organization organization = new Organization();
        organization.setId(UUID.randomUUID());
        Trip trip = new Trip();
        trip.setId(tripId);
        trip.setStatus(TripStatus.ONGOING);
        trip.setOrganization(organization);
        User rider = new User();
        rider.setRole(UserRole.DRIVER);
        rider.setStatus(UserStatus.ACTIVE);
        rider.setDriverApprovalStatus(DriverApprovalStatus.APPROVED);
        rider.setOrganization(organization);
        when(tripRepository.findByIdForUpdate(tripId)).thenReturn(Optional.of(trip));
        when(userRepository.findByIdForUpdate(riderId)).thenReturn(Optional.of(rider));
        AuthenticatedUser admin = new AuthenticatedUser(
                UUID.randomUUID(), UserRole.SUPER_ADMIN, null, null,
                Instant.now().plus(Duration.ofMinutes(5)));

        assertThatThrownBy(() -> tripService.assignRider(admin, tripId, riderId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("before the trip starts");
        verify(tripRepository, never()).save(any());
    }

    @Test
    void superAdminMovesADriverOffAnotherTripThatHasNotStarted() {
        UUID tripId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        UUID riderId = UUID.randomUUID();
        Organization organization = new Organization();
        organization.setId(UUID.randomUUID());
        Trip trip = scheduledTrip(tripId, organization);
        Trip other = scheduledTrip(otherId, organization);
        User rider = new User();
        rider.setRole(UserRole.DRIVER);
        rider.setStatus(UserStatus.ACTIVE);
        rider.setDriverApprovalStatus(DriverApprovalStatus.APPROVED);
        rider.setOrganization(organization);
        when(tripRepository.findByIdForUpdate(tripId)).thenReturn(Optional.of(trip));
        when(tripRepository.findByIdForUpdate(otherId)).thenReturn(Optional.of(other));
        when(userRepository.findByIdForUpdate(riderId)).thenReturn(Optional.of(rider));
        when(tripRepository.existsByRider_IdAndStatusIn(eq(riderId), any())).thenReturn(false);
        when(tripRepository.findByRiderIdAndStatusIn(eq(riderId), any())).thenReturn(List.of(other));
        when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AuthenticatedUser admin = new AuthenticatedUser(
                UUID.randomUUID(), UserRole.SUPER_ADMIN, null, null,
                Instant.now().plus(Duration.ofMinutes(5)));

        tripService.assignRider(admin, tripId, riderId);

        assertThat(other.getRider()).isNull();
        assertThat(trip.getRider()).isSameAs(rider);
    }

    @Test
    void superAdminCannotDeleteATripThatHasStarted() {
        UUID tripId = UUID.randomUUID();
        Organization organization = new Organization();
        organization.setId(UUID.randomUUID());
        Trip trip = scheduledTrip(tripId, organization);
        trip.setStatus(TripStatus.ONGOING);
        when(tripRepository.findByIdForUpdate(tripId)).thenReturn(Optional.of(trip));
        AuthenticatedUser admin = new AuthenticatedUser(
                UUID.randomUUID(), UserRole.SUPER_ADMIN, null, null,
                Instant.now().plus(Duration.ofMinutes(5)));

        assertThatThrownBy(() -> tripService.deleteUnstarted(admin, tripId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("has not started");
        verify(tripRepository, never()).delete(any());
    }

    private Trip scheduledTrip(UUID tripId, Organization organization) {
        Trip trip = new Trip();
        trip.setId(tripId);
        trip.setTripNumber("TRIP-1");
        trip.setTripType(TripType.DELIVERY);
        trip.setStatus(TripStatus.SCHEDULED);
        trip.setOrganization(organization);
        return trip;
    }

    private AuthenticatedUser principal(UUID organizationId) {
        return new AuthenticatedUser(UUID.randomUUID(), UserRole.MANUFACTURER_ADMIN,
                organizationId, Organizationtype.MANUFACTURER,
                Instant.now().plus(Duration.ofMinutes(5)));
    }
}
