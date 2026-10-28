package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.RiderProfile;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Domain.enums.UserStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.dto.DriverLocationRequest;
import com.example.distrobackend.dto.UpdateDriverAvailabilityRequest;
import com.example.distrobackend.repository.InvitationRepository;
import com.example.distrobackend.repository.RiderProfileRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.repository.TripRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RiderProfileRepository riderProfileRepository;
    @Mock private InvitationRepository invitationRepository;
    @Mock private InvitationService invitationService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private TripRepository tripRepository;

    private DriverService driverService;
    private UUID organizationId;
    private User driver;

    @BeforeEach
    void setUp() {
        driverService = new DriverService(userRepository, riderProfileRepository,
                invitationRepository, invitationService, refreshTokenService, tripRepository);
        organizationId = UUID.randomUUID();
        Organization organization = new Organization();
        organization.setId(organizationId);
        organization.setType(Organizationtype.DISTRIBUTOR);
        driver = new User();
        driver.setId(UUID.randomUUID());
        driver.setRole(UserRole.DRIVER);
        driver.setStatus(UserStatus.ACTIVE);
        driver.setOrganization(organization);
        driver.setPhoneNumber("+254700000000");
    }

    @Test
    void driverCannotChangeAnotherDriversAvailability() {
        UUID actorId = UUID.randomUUID();
        AuthenticatedUser actor = principal(actorId, UserRole.DRIVER);
        when(userRepository.findWithOrganizationById(driver.getId())).thenReturn(Optional.of(driver));

        assertThatThrownBy(() -> driverService.updateAvailability(actor, driver.getId(),
                new UpdateDriverAvailabilityRequest(true)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("permission");
        verifyNoInteractions(riderProfileRepository);
    }

    @Test
    void suspendedDriverCannotBecomeAvailable() {
        driver.setStatus(UserStatus.SUSPENDED);
        AuthenticatedUser actor = principal(UUID.randomUUID(), UserRole.DISTRIBUTOR_ADMIN);
        RiderProfile profile = new RiderProfile();
        profile.setUser(driver);
        when(userRepository.findWithOrganizationById(driver.getId())).thenReturn(Optional.of(driver));
        when(riderProfileRepository.findById(driver.getId())).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> driverService.updateAvailability(actor, driver.getId(),
                new UpdateDriverAvailabilityRequest(true)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("active driver");
        verify(riderProfileRepository, never()).save(any());
    }

    @Test
    void onlyDriversCanPublishTheirOwnLocation() {
        AuthenticatedUser actor = principal(UUID.randomUUID(), UserRole.DISTRIBUTOR_ADMIN);

        assertThatThrownBy(() -> driverService.recordLocation(actor,
                new DriverLocationRequest(0.0, 36.0)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Only drivers");
    }

    private AuthenticatedUser principal(UUID userId, UserRole role) {
        return new AuthenticatedUser(userId, role, organizationId,
                Organizationtype.DISTRIBUTOR,
                Instant.now().plus(Duration.ofMinutes(5)));
    }
}
