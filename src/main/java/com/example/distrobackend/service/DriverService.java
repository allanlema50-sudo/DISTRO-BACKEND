package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Invitation;
import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.RiderProfile;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.entity.Warehouse;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Domain.enums.UserStatus;
import com.example.distrobackend.Domain.enums.DriverApprovalStatus;
import com.example.distrobackend.Domain.enums.TripStatus;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.OtpPurpose;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.Util.PhoneNormalizer;
import com.example.distrobackend.dto.DriverRegistrationRequest;
import com.example.distrobackend.dto.DriverLocationRequest;
import com.example.distrobackend.dto.DriverResponse;
import com.example.distrobackend.dto.DriverSelfProfileResponse;
import com.example.distrobackend.dto.UpdateDriverAvailabilityRequest;
import com.example.distrobackend.dto.UpdateDriverRequest;
import com.example.distrobackend.dto.UpdateUserStatusRequest;
import com.example.distrobackend.repository.InvitationRepository;
import com.example.distrobackend.repository.RiderProfileRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.repository.TripRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.Util.SecretTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriverService {

    @PersistenceContext
    private EntityManager entityManager;

    private final UserRepository userRepository;
    private final RiderProfileRepository riderProfileRepository;
    private final InvitationRepository invitationRepository;
    private final RefreshTokenService refreshTokenService;
    private final TripRepository tripRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final DriverLoginEmailService driverLoginEmailService;

    @Transactional
    public DriverResponse registerForReview(AuthenticatedUser actor, DriverRegistrationRequest request) {
        if (actor == null || actor.role() != UserRole.SUPER_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return registerDirect(request, null, null, DriverApprovalStatus.PENDING_REVIEW, false);
    }

    @Transactional
    public DriverResponse registerForOrganization(AuthenticatedUser actor, DriverRegistrationRequest request) {
        requireOrganizationAdmin(actor);
        UUID organizationId = requireDriverOrganization(actor);
        Warehouse baseWarehouse = null;
        if (request.baseWarehouseId() != null) {
            baseWarehouse = entityManager.createQuery(
                            "select w from Warehouse w where w.id = :warehouseId and w.organization.id = :organizationId",
                            Warehouse.class)
                    .setParameter("warehouseId", request.baseWarehouseId())
                    .setParameter("organizationId", organizationId)
                    .getResultStream().findFirst()
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                            "Warehouse not found in this organization"));
        }
        Organization organization = entityManager.getReference(Organization.class, organizationId);
        DriverResponse response = registerDirect(request, organization, baseWarehouse,
                DriverApprovalStatus.APPROVED, true);
        return response;
    }

    private DriverResponse registerDirect(DriverRegistrationRequest request,
                                          Organization organization,
                                          Warehouse baseWarehouse,
                                          DriverApprovalStatus approvalStatus,
                                          boolean organizationManaged) {
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
        driver.setDriverApprovalStatus(approvalStatus);
        driver.setOrganization(organization);
        driver.setBaseWarehouse(baseWarehouse);
        driver.setPhoneVerified(false);
        driver.setEmailVerified(false);
        User saved = userRepository.save(driver);

        RiderProfile profile = new RiderProfile();
        profile.setUser(saved);
        profile.setLicenseNumber(request.licenseNumber().trim());
        profile.setNationalId(request.nationalId().trim());
        profile.setLicenseExpiryDate(request.licenseExpiryDate());
        profile.setInternalNotes(request.notes() == null || request.notes().isBlank()
                ? null : request.notes().trim());
        RiderProfile savedProfile = persistNewProfile(profile);
        if (organizationManaged) {
            otpService.issue(saved, OtpPurpose.ACCOUNT_VERIFICATION);
        }
        return DriverResponse.from(saved, savedProfile);
    }

    @Transactional
    public Page<DriverResponse> list(AuthenticatedUser actor, Pageable pageable) {
        UUID organizationId = requireDriverOrganization(actor);
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
            organizationId = requireDriverOrganization(actor);
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
        String temporaryPassword = null;
        if (status == DriverApprovalStatus.APPROVED
                && driver.getStatus() == UserStatus.ACTIVE
                && driver.getEmail() != null
                && !driver.getEmail().isBlank()) {
            temporaryPassword = temporaryPassword();
            driver.setPasswordHash(passwordEncoder.encode(temporaryPassword));
            // The administrator already recorded this phone. Sign-in requires it
            // to be verified, and the emailed password is the driver's first login.
            driver.setPhoneVerified(true);
        }
        userRepository.save(driver);
        if (temporaryPassword != null) {
            try {
                driverLoginEmailService.sendTemporaryPassword(driver.getEmail(), temporaryPassword);
            } catch (ApiException ex) {
                log.warn("Driver {} was approved but the sign-in email was not sent: {}",
                        driver.getId(), ex.getMessage());
            }
        }
        return DriverResponse.from(driver, ensureProfile(driver));
    }

    /**
     * Emails a new temporary password for an already approved driver.
     * Approval status is left unchanged.
     */
    @Transactional
    public DriverResponse sendLoginEmail(AuthenticatedUser actor, UUID driverId) {
        if (actor == null || actor.role() != UserRole.SUPER_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        User driver = userRepository.findByIdForUpdate(driverId)
                .filter(user -> user.getRole() == UserRole.DRIVER)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Driver not found"));
        if (driver.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Only an active driver can receive sign-in details");
        }
        if (driver.getDriverApprovalStatus() != DriverApprovalStatus.APPROVED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Approve the driver before sending sign-in details");
        }
        if (driver.getEmail() == null || driver.getEmail().isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "This driver has no email address");
        }
        String temporaryPassword = temporaryPassword();
        driver.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        driver.setPhoneVerified(true);
        userRepository.save(driver);
        refreshTokenService.revokeAll(driver.getId());
        driverLoginEmailService.sendTemporaryPassword(driver.getEmail(), temporaryPassword);
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

    @Transactional(readOnly = true)
    public DriverSelfProfileResponse getMyProfile(AuthenticatedUser actor) {
        if (actor == null || actor.role() != UserRole.DRIVER) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        User driver = userRepository.findById(actor.userId())
                .filter(user -> user.getRole() == UserRole.DRIVER)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Driver not found"));
        RiderProfile profile = riderProfileRepository.findById(actor.userId()).orElse(null);
        return DriverSelfProfileResponse.from(driver, profile);
    }

    @Transactional
    public DriverResponse update(AuthenticatedUser actor, UUID driverId, UpdateDriverRequest request) {
        if (actor == null) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        if (actor.role() == UserRole.DRIVER) {
            if (!actor.userId().equals(driverId)) {
                throw new ApiException(ErrorCode.ACCESS_DENIED,
                        "A driver cannot update another driver's profile");
            }
            User driver = findDriverForUpdate(actor, driverId);
            return submitOwnProfileChange(driver, request);
        }
        if (actor.role() != UserRole.SUPER_ADMIN) {
            requireOrganizationAdmin(actor);
        }
        User driver = findDriverForUpdate(actor, driverId);
        RiderProfile profile = ensureProfile(driver);
        applyProfileUpdate(driver, profile, request);
        userRepository.save(driver);
        riderProfileRepository.save(profile);
        return DriverResponse.from(driver, profile);
    }

    @Transactional
    public DriverResponse reviewProfileChange(AuthenticatedUser actor, UUID driverId, String decision) {
        if (actor == null || actor.role() != UserRole.SUPER_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        String normalized = decision == null ? "" : decision.trim().toUpperCase(java.util.Locale.ROOT);
        boolean approve = switch (normalized) {
            case "APPROVED", "APPROVE" -> true;
            case "REJECTED", "REJECT" -> false;
            default -> throw new ApiException(ErrorCode.BAD_REQUEST,
                    "decision must be APPROVED or REJECTED");
        };
        User driver = userRepository.findByIdForUpdate(driverId)
                .filter(user -> user.getRole() == UserRole.DRIVER)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Driver not found"));
        RiderProfile profile = ensureProfile(driver);
        if (profile.getPendingFullName() == null
                && profile.getPendingPhoneNumber() == null
                && profile.getPendingLicenseNumber() == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "This driver has no profile change waiting for approval");
        }
        if (approve) {
            if (profile.getPendingFullName() != null) {
                driver.setFullName(profile.getPendingFullName());
            }
            if (profile.getPendingPhoneNumber() != null
                    && !profile.getPendingPhoneNumber().equals(driver.getPhoneNumber())) {
                if (userRepository.existsByPhoneNumber(profile.getPendingPhoneNumber())) {
                    throw new ApiException(ErrorCode.DUPLICATE_PHONE);
                }
                driver.setPhoneNumber(profile.getPendingPhoneNumber());
                driver.setPhoneVerified(false);
            }
            if (profile.getPendingLicenseNumber() != null) {
                profile.setLicenseNumber(profile.getPendingLicenseNumber());
            }
            userRepository.save(driver);
        }
        profile.setPendingFullName(null);
        profile.setPendingPhoneNumber(null);
        profile.setPendingLicenseNumber(null);
        riderProfileRepository.save(profile);
        return DriverResponse.from(driver, profile);
    }

    private DriverResponse submitOwnProfileChange(User driver, UpdateDriverRequest request) {
        rejectEmailChange(driver, request);
        RiderProfile profile = ensureProfile(driver);
        if (request.fullName() != null) {
            if (request.fullName().isBlank()) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Driver name cannot be blank");
            }
            String name = request.fullName().trim();
            profile.setPendingFullName(name.equals(driver.getFullName()) ? null : name);
        }
        if (request.phoneNumber() != null) {
            String phone = PhoneNormalizer.normalize(request.phoneNumber());
            if (phone.equals(driver.getPhoneNumber())) {
                profile.setPendingPhoneNumber(null);
            } else {
                if (userRepository.existsByPhoneNumber(phone)) {
                    throw new ApiException(ErrorCode.DUPLICATE_PHONE);
                }
                profile.setPendingPhoneNumber(phone);
            }
        }
        if (request.licenseNumber() != null) {
            if (request.licenseNumber().isBlank()) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "License number cannot be blank");
            }
            String license = request.licenseNumber().trim();
            String current = profile.getLicenseNumber();
            profile.setPendingLicenseNumber(license.equals(current) ? null : license);
        }
        riderProfileRepository.save(profile);
        return DriverResponse.from(driver, profile, false);
    }

    private void applyProfileUpdate(User driver, RiderProfile profile, UpdateDriverRequest request) {
        rejectEmailChange(driver, request);
        if (request.fullName() != null) {
            if (request.fullName().isBlank()) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Driver name cannot be blank");
            }
            driver.setFullName(request.fullName().trim());
            profile.setPendingFullName(null);
        }
        if (request.phoneNumber() != null) {
            String phone = PhoneNormalizer.normalize(request.phoneNumber());
            if (!phone.equals(driver.getPhoneNumber()) && userRepository.existsByPhoneNumber(phone)) {
                throw new ApiException(ErrorCode.DUPLICATE_PHONE);
            }
            driver.setPhoneNumber(phone);
            driver.setPhoneVerified(false);
            profile.setPendingPhoneNumber(null);
        }
        if (request.licenseNumber() != null) {
            if (request.licenseNumber().isBlank()) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "License number cannot be blank");
            }
            profile.setLicenseNumber(request.licenseNumber().trim());
            profile.setPendingLicenseNumber(null);
        }
    }

    private static void rejectEmailChange(User driver, UpdateDriverRequest request) {
        if (request.email() != null && !request.email().trim().equalsIgnoreCase(
                driver.getEmail() == null ? "" : driver.getEmail())) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Driver email cannot be changed through this endpoint");
        }
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
        if (actor != null && actor.role() == UserRole.DRIVER) {
            if (!actor.userId().equals(driverId)) throw new ApiException(ErrorCode.ACCESS_DENIED);
            return userRepository.findWithOrganizationById(driverId)
                    .filter(user -> user.getRole() == UserRole.DRIVER)
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Driver not found"));
        }
        UUID organizationId = requireDriverOrganization(actor);
        User driver = userRepository.findWithOrganizationById(driverId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Driver not found"));
        if (driver.getRole() != UserRole.DRIVER || driver.getOrganization() == null
                || !organizationId.equals(driver.getOrganization().getId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Driver not found");
        }
        return driver;
    }

    private User findDriverForUpdate(AuthenticatedUser actor, UUID driverId) {
        if (actor != null && actor.role() == UserRole.SUPER_ADMIN) {
            return userRepository.findByIdForUpdate(driverId)
                    .filter(user -> user.getRole() == UserRole.DRIVER)
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Driver not found"));
        }
        if (actor != null && actor.role() == UserRole.DRIVER) {
            if (!actor.userId().equals(driverId)) throw new ApiException(ErrorCode.ACCESS_DENIED);
            return userRepository.findByIdForUpdate(driverId)
                    .filter(user -> user.getRole() == UserRole.DRIVER)
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Driver not found"));
        }
        UUID organizationId = requireDriverOrganization(actor);
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
            profile.setUser(driver);
            invitationRepository.findTopByInvitedUser_IdOrderByCreatedAtDesc(driver.getId())
                    .map(Invitation::getDriverLicenseNumber)
                    .ifPresent(profile::setLicenseNumber);
            return persistNewProfile(profile);
        });
    }

    private RiderProfile persistNewProfile(RiderProfile profile) {
        // Hibernate's registration failure reported a RiderProfile with a null
        // identifier while processing its @MapsId one-to-one association. Set
        // the derived key from the already-persisted User before writing it.
        if (profile.getUser() == null || profile.getUser().getId() == null) {
            throw new IllegalStateException("A rider profile requires a persisted user");
        }
        profile.setUserId(profile.getUser().getId());
        entityManager.persist(profile);
        return profile;
    }

    private static UUID requireOrganization(AuthenticatedUser actor) {
        if (actor == null || actor.organizationId() == null) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return actor.organizationId();
    }

    private static UUID requireDriverOrganization(AuthenticatedUser actor) {
        UUID organizationId = requireOrganization(actor);
        if (actor.organizationType() != Organizationtype.DISTRIBUTOR
                && actor.organizationType() != Organizationtype.MANUFACTURER) {
            throw new ApiException(ErrorCode.ACCESS_DENIED,
                    "Driver operations are available only to manufacturer or distributor organizations");
        }
        return organizationId;
    }

    private static void requireOrganizationAdmin(AuthenticatedUser actor) {
        requireDriverOrganization(actor);
        if (!isOrganizationAdmin(actor)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }

    private static String temporaryPassword() {
        SecureRandom random = new SecureRandom();
        char[] alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();
        char[] password = new char[12];
        for (int i = 0; i < password.length; i++) {
            password[i] = alphabet[random.nextInt(alphabet.length)];
        }
        password[0] = "ABCDEFGHJKLMNPQRSTUVWXYZ".charAt(random.nextInt(24));
        password[1] = "abcdefghijkmnopqrstuvwxyz".charAt(random.nextInt(23));
        password[2] = "23456789".charAt(random.nextInt(8));
        return new String(password);
    }

    private static boolean isOrganizationAdmin(AuthenticatedUser actor) {
        return actor != null && (actor.role() == UserRole.MANUFACTURER_ADMIN
                || actor.role() == UserRole.DISTRIBUTOR_ADMIN);
    }
}
