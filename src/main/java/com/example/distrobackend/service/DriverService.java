package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Invitation;
import com.example.distrobackend.Domain.entity.RiderProfile;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Domain.enums.UserStatus;
import com.example.distrobackend.Domain.enums.TripStatus;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.Util.PhoneNormalizer;
import com.example.distrobackend.dto.CreateDriverInvitationRequest;
import com.example.distrobackend.dto.DriverLocationRequest;
import com.example.distrobackend.dto.DriverResponse;
import com.example.distrobackend.dto.UpdateDriverAvailabilityRequest;
import com.example.distrobackend.dto.UpdateDriverRequest;
import com.example.distrobackend.dto.UpdateUserStatusRequest;
import com.example.distrobackend.dto.InvitationResponse;
import com.example.distrobackend.repository.InvitationRepository;
import com.example.distrobackend.repository.RiderProfileRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.repository.TripRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DriverService {

    private final UserRepository userRepository;
    private final RiderProfileRepository riderProfileRepository;
    private final InvitationRepository invitationRepository;
    private final InvitationService invitationService;
    private final RefreshTokenService refreshTokenService;
    private final TripRepository tripRepository;

    @Transactional
    public InvitationResponse invite(AuthenticatedUser actor, CreateDriverInvitationRequest request) {
        requireOrganizationAdmin(actor);
        return invitationService.createDriver(actor, request);
    }

    @Transactional
    public InvitationResponse resendInvitation(AuthenticatedUser actor, UUID driverId) {
        requireOrganizationAdmin(actor);
        return invitationService.resendDriverInvitation(actor, driverId);
    }

    @Transactional
    public Page<DriverResponse> list(AuthenticatedUser actor, Pageable pageable) {
        UUID organizationId = requireDistributor(actor);
        return userRepository.findByOrganization_IdAndRole(organizationId, UserRole.DRIVER, pageable)
                .map(user -> DriverResponse.from(user, ensureProfile(user)));
    }

    @Transactional
    public DriverResponse get(AuthenticatedUser actor, UUID driverId) {
        if (actor != null && actor.role() == UserRole.DRIVER
                && !Objects.equals(actor.userId(), driverId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        User driver = findDriver(actor, driverId);
        return DriverResponse.from(driver, ensureProfile(driver));
    }

    @Transactional
    public DriverResponse update(AuthenticatedUser actor, UUID driverId, UpdateDriverRequest request) {
        requireOrganizationAdmin(actor);
        User driver = findDriverForUpdate(actor, driverId);
        if (request.fullName() != null) {
            if (request.fullName().isBlank()) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Driver name cannot be blank");
            }
            driver.setFullName(request.fullName().trim());
        }
        if (request.email() != null && !request.email().trim().equalsIgnoreCase(driver.getEmail())) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Driver email cannot be changed through this endpoint");
        }
        if (request.phoneNumber() != null) {
            String phone = PhoneNormalizer.normalize(request.phoneNumber());
            if (!phone.equals(driver.getPhoneNumber()) && userRepository.existsByPhoneNumber(phone)) {
                throw new ApiException(ErrorCode.DUPLICATE_PHONE);
            }
            driver.setPhoneNumber(phone);
            driver.setPhoneVerified(false);
        }
        RiderProfile profile = ensureProfile(driver);
        if (request.licenseNumber() != null) {
            if (request.licenseNumber().isBlank()) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "License number cannot be blank");
            }
            profile.setLicenseNumber(request.licenseNumber().trim());
        }
        userRepository.save(driver);
        riderProfileRepository.save(profile);
        return DriverResponse.from(driver, profile);
    }

    @Transactional
    public DriverResponse updateStatus(AuthenticatedUser actor, UUID driverId,
                                       UpdateUserStatusRequest request) {
        requireOrganizationAdmin(actor);
        if (request.status() == null || request.status() == UserStatus.INVITED) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Driver status must be ACTIVE, SUSPENDED, or DEACTIVATED");
        }
        User driver = findDriverForUpdate(actor, driverId);
        if (driver.getStatus() == UserStatus.INVITED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "An invited driver must accept or have the invitation revoked before status changes");
        }
        if (request.status() != UserStatus.ACTIVE
                && tripRepository.existsByRider_IdAndStatusIn(driverId,
                java.util.List.of(TripStatus.SCHEDULED, TripStatus.ONGOING, TripStatus.DELAYED))) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "A driver assigned to an active trip cannot be suspended or deactivated");
        }
        driver.setStatus(request.status());
        if (request.status() != UserStatus.ACTIVE) {
            refreshTokenService.revokeAll(driver.getId());
            RiderProfile profile = ensureProfile(driver);
            profile.setAvailable(false);
            riderProfileRepository.save(profile);
        }
        return DriverResponse.from(driver, ensureProfile(driver));
    }

    @Transactional
    public DriverResponse updateAvailability(AuthenticatedUser actor, UUID driverId,
                                             UpdateDriverAvailabilityRequest request) {
        User driver = findDriverForUpdate(actor, driverId);
        if (actor.role() != UserRole.DRIVER && !isOrganizationAdmin(actor)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        if (actor.role() == UserRole.DRIVER && !actor.userId().equals(driverId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        if (driver.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CONFLICT, "Only an active driver can change availability");
        }
        RiderProfile profile = ensureProfile(driver);
        profile.setAvailable(request.available());
        return DriverResponse.from(driver, riderProfileRepository.save(profile));
    }

    @Transactional
    public DriverResponse recordLocation(AuthenticatedUser actor, DriverLocationRequest request) {
        if (actor == null || actor.role() != UserRole.DRIVER) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Only drivers can report their location");
        }
        User driver = findDriver(actor, actor.userId());
        RiderProfile profile = ensureProfile(driver);
        profile.setCurrentLat(request.lat());
        profile.setCurrentLng(request.lng());
        profile.setLastLocationAt(OffsetDateTime.now());
        return DriverResponse.from(driver, riderProfileRepository.save(profile));
    }

    private User findDriver(AuthenticatedUser actor, UUID driverId) {
        UUID organizationId = requireDistributor(actor);
        User driver = userRepository.findWithOrganizationById(driverId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Driver not found"));
        if (driver.getRole() != UserRole.DRIVER || driver.getOrganization() == null
                || !organizationId.equals(driver.getOrganization().getId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Driver not found");
        }
        return driver;
    }

    private User findDriverForUpdate(AuthenticatedUser actor, UUID driverId) {
        UUID organizationId = requireDistributor(actor);
        User driver = userRepository.findByIdForUpdate(driverId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Driver not found"));
        if (driver.getRole() != UserRole.DRIVER || driver.getOrganization() == null
                || !organizationId.equals(driver.getOrganization().getId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Driver not found");
        }
        return driver;
    }

    private RiderProfile ensureProfile(User driver) {
        return riderProfileRepository.findById(driver.getId()).orElseGet(() -> {
            RiderProfile profile = new RiderProfile();
            profile.setUserId(driver.getId());
            profile.setUser(driver);
            invitationRepository.findTopByInvitedUser_IdOrderByCreatedAtDesc(driver.getId())
                    .map(Invitation::getDriverLicenseNumber)
                    .ifPresent(profile::setLicenseNumber);
            return riderProfileRepository.save(profile);
        });
    }

    private static UUID requireOrganization(AuthenticatedUser actor) {
        if (actor == null || actor.organizationId() == null) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return actor.organizationId();
    }

    private static UUID requireDistributor(AuthenticatedUser actor) {
        UUID organizationId = requireOrganization(actor);
        if (actor.organizationType() != Organizationtype.DISTRIBUTOR) {
            throw new ApiException(ErrorCode.ACCESS_DENIED,
                    "Driver operations are available only to distributor organizations");
        }
        return organizationId;
    }

    private static void requireOrganizationAdmin(AuthenticatedUser actor) {
        requireDistributor(actor);
        if (!isOrganizationAdmin(actor)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }

    private static boolean isOrganizationAdmin(AuthenticatedUser actor) {
        return actor != null && (actor.role() == UserRole.MANUFACTURER_ADMIN
                || actor.role() == UserRole.DISTRIBUTOR_ADMIN);
    }
}
