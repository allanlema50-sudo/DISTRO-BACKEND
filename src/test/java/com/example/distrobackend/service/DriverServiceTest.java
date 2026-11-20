package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Domain.enums.UserStatus;
import com.example.distrobackend.Domain.enums.DriverApprovalStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.dto.DriverLocationRequest;
import com.example.distrobackend.dto.DriverRegistrationRequest;
import com.example.distrobackend.dto.DriverResponse;
import com.example.distrobackend.dto.UpdateDriverAvailabilityRequest;
import com.example.distrobackend.dto.UpdateUserStatusRequest;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import jakarta.persistence.EntityManager;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RiderProfileRepository riderProfileRepository;
    @Mock private InvitationRepository invitationRepository;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private TripRepository tripRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private OtpService otpService;
    @Mock private EntityManager entityManager;

    private DriverService driverService;
    private UUID organizationId;
    private User driver;

    @BeforeEach
    void setUp() {
        driverService = new DriverService(userRepository, riderProfileRepository,
                invitationRepository, refreshTokenService, tripRepository, passwordEncoder, otpService);
        ReflectionTestUtils.setField(driverService, "entityManager", entityManager);
        organizationId = UUID.randomUUID();
        Organization organization = new Organization();
        organization.setId(organizationId);
        organization.setType(Organizationtype.DISTRIBUTOR);
        driver = new User();
        driver.setId(UUID.randomUUID());
        driver.setRole(UserRole.DRIVER);
        driver.setStatus(UserStatus.ACTIVE);
        driver.setDriverApprovalStatus(DriverApprovalStatus.APPROVED);
        driver.setOrganization(organization);
        driver.setPhoneNumber("+254700000000");
    }

    @Test
    void manufacturerAdminDirectlyCreatesApprovedDriverInItsOwnOrganizationAndSendsPhoneOtp() {
        directRegistrationBindsTenantAndStartsPhoneVerification(UserRole.MANUFACTURER_ADMIN,
                Organizationtype.MANUFACTURER);
    }

    @Test
    void distributorAdminDirectlyCreatesApprovedDriverInItsOwnOrganizationAndSendsPhoneOtp() {
        directRegistrationBindsTenantAndStartsPhoneVerification(UserRole.DISTRIBUTOR_ADMIN,
                Organizationtype.DISTRIBUTOR);
    }

    private void directRegistrationBindsTenantAndStartsPhoneVerification(
            UserRole adminRole, Organizationtype organizationType) {
        when(userRepository.existsByEmail("new-driver@example.test")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("+254700000123")).thenReturn(false);
        when(riderProfileRepository.existsByNationalIdIgnoreCase("NID-123")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("random-password-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(entityManager.getReference(com.example.distrobackend.Domain.entity.Organization.class,
                organizationId)).thenAnswer(invocation -> {
            driver.getOrganization().setType(organizationType);
            return driver.getOrganization();
        });

        AuthenticatedUser actor = new AuthenticatedUser(UUID.randomUUID(), adminRole, organizationId,
                organizationType, Instant.now().plus(Duration.ofMinutes(5)));
        DriverResponse response = driverService.registerForOrganization(actor,
                new DriverRegistrationRequest("New Driver", "new-driver@example.test", "+254700000123",
                        "NID-123", "DL-123", java.time.LocalDate.now().plusYears(1), null, null));

        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.approvalStatus()).isEqualTo(DriverApprovalStatus.APPROVED);
        assertThat(response.phoneVerified()).isFalse();
        verify(userRepository).save(argThat(saved -> saved.getOrganization().getId().equals(organizationId)
                && saved.getRole() == UserRole.DRIVER && saved.getBaseWarehouse() == null));
        verify(entityManager).persist(argThat(profile -> profile instanceof com.example.distrobackend.Domain.entity.RiderProfile
                && ((com.example.distrobackend.Domain.entity.RiderProfile) profile).getNationalId().equals("NID-123")));
        verify(otpService).issue(any(User.class), eq(com.example.distrobackend.Domain.enums.OtpPurpose.ACCOUNT_VERIFICATION));
        verifyNoInteractions(invitationRepository);
    }

    @Test
    void driverCannotChangeAnotherDriversAvailability() {
        UUID actorId = UUID.randomUUID();
        AuthenticatedUser actor = principal(actorId, UserRole.DRIVER);
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
        when(userRepository.findByIdForUpdate(driver.getId())).thenReturn(Optional.of(driver));

        assertThatThrownBy(() -> driverService.updateAvailability(actor, driver.getId(),
                new UpdateDriverAvailabilityRequest(true)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("active driver");
        verify(riderProfileRepository, never()).save(any());
    }

    @Test
    void invitedDriverCannotHaveItsStatusChangedBeforeAcceptingInvitation() {
        driver.setStatus(UserStatus.INVITED);
        AuthenticatedUser actor = principal(UUID.randomUUID(), UserRole.DISTRIBUTOR_ADMIN);
        when(userRepository.findByIdForUpdate(driver.getId())).thenReturn(Optional.of(driver));

        assertThatThrownBy(() -> driverService.updateStatus(actor, driver.getId(),
                new UpdateUserStatusRequest(UserStatus.ACTIVE)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("invited driver");
        verify(refreshTokenService, never()).revokeAll(any());
    }

    @Test
    void driverCannotReadAnotherDriversProfile() {
        UUID actorId = UUID.randomUUID();
        AuthenticatedUser actor = principal(actorId, UserRole.DRIVER);

        assertThatThrownBy(() -> driverService.get(actor, driver.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("permission");
        verifyNoInteractions(userRepository, riderProfileRepository);
    }

    @Test
    void onlyDriversCanPublishTheirOwnLocation() {
        AuthenticatedUser actor = principal(UUID.randomUUID(), UserRole.DISTRIBUTOR_ADMIN);

        assertThatThrownBy(() -> driverService.recordLocation(actor,
                new DriverLocationRequest(0.0, 36.0)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Only drivers");
    }

    @Test
    void manufacturerCanUseDriverListingWithinItsOrganization() {
        when(userRepository.searchDrivers(eq(organizationId), isNull(), isNull(), isNull(),
                eq(UserRole.DRIVER), any())).thenReturn(org.springframework.data.domain.Page.empty());
        AuthenticatedUser actor = new AuthenticatedUser(
                UUID.randomUUID(), UserRole.MANUFACTURER_ADMIN, organizationId,
                Organizationtype.MANUFACTURER,
                Instant.now().plus(Duration.ofMinutes(5)));

        driverService.list(actor, organizationId, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 20));

        verify(userRepository).searchDrivers(eq(organizationId), isNull(), isNull(), isNull(),
                eq(UserRole.DRIVER), any());
    }

    private AuthenticatedUser principal(UUID userId, UserRole role) {
        return new AuthenticatedUser(userId, role, organizationId,
                Organizationtype.DISTRIBUTOR,
                Instant.now().plus(Duration.ofMinutes(5)));
    }
}
