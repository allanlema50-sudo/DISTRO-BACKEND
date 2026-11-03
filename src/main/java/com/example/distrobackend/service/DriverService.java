package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Invitation;
import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.RiderProfile;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Domain.enums.UserStatus;
import com.example.distrobackend.Domain.enums.DriverApprovalStatus;
import com.example.distrobackend.Domain.enums.TripStatus;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.OrganizationStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.Util.PhoneNormalizer;
import com.example.distrobackend.dto.CreateDriverInvitationRequest;
import com.example.distrobackend.dto.DriverRegistrationRequest;
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
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.Util.SecretTokenService;
import com.example.distrobackend.Util.PhoneNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

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
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public DriverResponse registerForReview(AuthenticatedUser actor, DriverRegistrationRequest request) {
        if (actor == null || actor.role() != UserRole.SUPER_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        Organization organization = organizationRepository.findById(request.organizationId())
                .filter(org -> org.getType() == Organizationtype.DISTRIBUTOR
                        && org.getStatus() == OrganizationStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST,
                        "Select an active distributor organization for this driver"));
        String phone = PhoneNormalizer.normalize(request.phoneNumber());
        String email = request.email() == null || request.email().isBlank()
                ? null : request.email().trim().toLowerCase(java.util.Locale.ROOT);
        if (email != null && userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByPhoneNumber(phone)) {
            throw new ApiException(ErrorCode.DUPLICATE_PHONE);
        }
        if (riderProfileRepository.existsByNationalIdIgnoreCase(request.nationalId().trim())) {
            throw new ApiException(ErrorCode.CONFLICT, "A driver with this national ID is already registered");
        }

        User driver = new User();
        driver.setFullName(request.fullName().trim());
        driver.setEmail(email);
        driver.setPhoneNumber(phone);
        driver.setPasswordHash(passwordEncoder.encode(SecretTokenService.generate()));
        driver.setRole(UserRole.DRIVER);
        driver.setStatus(UserStatus.ACTIVE);
        driver.setDriverApprovalStatus(DriverApprovalStatus.PENDING_REVIEW);
        driver.setOrganization(organization);
        driver.setPhoneVerified(false);
        driver.setEmailVerified(false);
        User saved = userRepository.save(driver);

        RiderProfile profile = new RiderProfile();
        profile.setUserId(saved.getId());
        profile.setUser(saved);
        profile.setLicenseNumber(request.licenseNumber().trim());
        profile.setNationalId(request.nationalId().trim());
        profile.setLicenseExpiryDate(request.licenseExpiryDate());
        profile.setInternalNotes(request.notes() == null || request.notes().isBlank()
                ? null : request.notes().trim());
        return DriverResponse.from(saved, riderProfileRepository.save(profile));
    }

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
    public Page<DriverResponse> list(AuthenticatedUser actor, UUID requestedOrganizationId,
                                     UserStatus userStatus, DriverApprovalStatus approvalStatus,
                                     Boolean available, Pageable pageable) {
        UUID organizationId;
        if (actor != null && actor.role() == UserRole.SUPER_ADMIN) {
            organizationId = requestedOrganizationId;
        } else {
            organizationId = requireDistributor(actor);
            if (requestedOrganizationId != null && !requestedOrganizationId.equals(organizationId)) {
                throw new ApiException(ErrorCode.ACCESS_DENIED);
            }
        }
        return userRepository.searchDrivers(organizationId, userStatus, approvalStatus,
                        available, UserRole.DRIVER, pageable)
                .map(user -> DriverResponse.from(user, ensureProfile(user)));
    }

    @Transactional
    public DriverResponse approve(AuthenticatedUser actor, UUID driverId, DriverApprovalStatus status) {
        if (actor == null || actor.role() != UserRole.SUPER_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        if (status == null || status == DriverApprovalStatus.PENDING_REVIEW) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Approval status must be APPROVED or REJECTED");
        }
        User driver = userRepository.findByIdForUpdate(driverId)
                .filter(user -> user.getRole() == UserRole.DRIVER)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Driver not found"));
        if (driver.getStatus() == UserStatus.INVITED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "A driver must accept the invitation before platform approval");
        }
        driver.setDriverApprovalStatus(status);
        if (status == DriverApprovalStatus.REJECTED) {
            refreshTokenService.revokeAll(driverId);
            RiderProfile profile = ensureProfile(driver);
            profile.setAvailable(false);
            riderProfileRepository.save(profile);
        }
        userRepository.save(driver);
        return DriverResponse.from(driver, ensureProfile(driver));
    }

    @Transactional
    public DriverResponse get(AuthenticatedUser actor, UUID driverId) {
        if (actor != null && actor.role() == UserRole.SUPER_ADMIN) {
            User driver = userRepository.findWithOrganizationById(driverId)
                    .filter(user -> user.getRole() == UserRole.DRIVER)
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Driver not found"));
            return DriverResponse.from(driver, ensureProfile(driver));
        }
        if (actor != null && actor.role() == UserRole.DRIVER
                && !Objects.equals(actor.userId(), driverId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        User driver = findDriver(actor, driverId);
        return DriverResponse.from(driver, ensureProfile(driver), actor.role() != UserRole.DRIVER);
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
        if (driver.getStatus() != UserStatus.ACTIVE
                || driver.getDriverApprovalStatus() != DriverApprovalStatus.APPROVED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Only an approved active driver can change availability");
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
