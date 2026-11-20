package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.Shipment;
import com.example.distrobackend.Domain.entity.Trip;
import com.example.distrobackend.Domain.entity.TripStop;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.NotificationPriority;
import com.example.distrobackend.Domain.enums.NotificationType;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.ShipmentStatus;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.dto.ShipmentLocationUpdate;
import com.example.distrobackend.repository.OrderRepository;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.ShipmentRepository;
import com.example.distrobackend.repository.ShipmentStatusHistoryRepository;
import com.example.distrobackend.repository.TripRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentLocationServiceTest {

    @Mock private ShipmentRepository shipments;
    @Mock private ShipmentStatusHistoryRepository history;
    @Mock private OrderRepository orders;
    @Mock private OrganizationRepository organizations;
    @Mock private TripRepository trips;
    @Mock private UserRepository users;
    @Mock private OrderService orderService;
    @Mock private NotificationService notifications;
    @InjectMocks private ShipmentService service;

    @Test
    void correctingALocationUpdatesTheTripAndNotifiesTheDriver() {
        UUID shipmentId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        Organization organization = new Organization();
        organization.setId(organizationId);
        User driver = new User();
        Trip trip = new Trip();
        trip.setRider(driver);
        trip.setOriginLat(-1.2);
        trip.setOriginLng(36.8);
        trip.setOriginName("Old pickup");
        TripStop stop = new TripStop();
        stop.setLocationLat(-0.1);
        stop.setLocationLng(34.7);
        stop.setLocationAddress("Old drop");
        trip.addStop(stop);
        Shipment shipment = shipment(shipmentId, organization, trip);
        when(shipments.findByIdForUpdate(shipmentId)).thenReturn(Optional.of(shipment));
        when(shipments.save(shipment)).thenReturn(shipment);
        AuthenticatedUser admin = new AuthenticatedUser(
                UUID.randomUUID(), UserRole.MANUFACTURER_ADMIN, organizationId,
                Organizationtype.MANUFACTURER, Instant.now().plus(Duration.ofMinutes(5)));

        service.correctLocation(admin, shipmentId, new ShipmentLocationUpdate(
                "New pickup", -1.3, 36.9, "New drop", -0.2, 34.8));

        assertThat(trip.getOriginLat()).isEqualTo(-1.3);
        assertThat(trip.getOriginName()).isEqualTo("New pickup");
        assertThat(stop.getLocationLat()).isEqualTo(-0.2);
        assertThat(stop.getLocationAddress()).isEqualTo("New drop");
        verify(notifications).create(eq(driver), eq("Shipment location corrected"),
                contains("New drop"), eq(NotificationType.DELIVERY), eq(NotificationPriority.HIGH),
                eq(shipmentId), eq("SHIPMENT"));
    }

    @Test
    void organizationStaffCannotCorrectALocation() {
        UUID shipmentId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        Organization organization = new Organization();
        organization.setId(organizationId);
        Shipment shipment = shipment(shipmentId, organization, null);
        when(shipments.findByIdForUpdate(shipmentId)).thenReturn(Optional.of(shipment));
        AuthenticatedUser staff = new AuthenticatedUser(
                UUID.randomUUID(), UserRole.MANUFACTURER_STAFF, organizationId,
                Organizationtype.MANUFACTURER, Instant.now().plus(Duration.ofMinutes(5)));

        assertThatThrownBy(() -> service.correctLocation(staff, shipmentId,
                new ShipmentLocationUpdate("New pickup", -1.3, 36.9, "New drop", -0.2, 34.8)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("admin");
        verify(notifications, never()).create(any(), any(), any(), any(), any(), any(), any());
    }

    private Shipment shipment(UUID shipmentId, Organization organization, Trip trip) {
        Shipment shipment = new Shipment();
        shipment.setId(shipmentId);
        shipment.setShipmentNumber("SHP-1");
        shipment.setStatus(ShipmentStatus.ASSIGNED);
        shipment.setOrganization(organization);
        shipment.setTrip(trip);
        shipment.setPickupLocation("Old pickup");
        shipment.setPickupLat(-1.2);
        shipment.setPickupLng(36.8);
        shipment.setDeliveryLocation("Old drop");
        shipment.setDeliveryLat(-0.1);
        shipment.setDeliveryLng(34.7);
        return shipment;
    }
}
