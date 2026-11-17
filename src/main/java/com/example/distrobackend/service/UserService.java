package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.entity.CustomerAddress;
import com.example.distrobackend.Domain.entity.PushDeviceToken;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Domain.enums.UserStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.UpdateUserStatusRequest;
import com.example.distrobackend.dto.UserResponse;
import com.example.distrobackend.dto.CustomerAddressRequest;
import com.example.distrobackend.dto.CustomerAddressResponse;
import com.example.distrobackend.dto.PushDeviceTokenRequest;
import com.example.distrobackend.dto.PushDeviceTokenResponse;
import com.example.distrobackend.repository.CustomerAddressRepository;
import com.example.distrobackend.repository.PushDeviceTokenRepository;
import com.example.distrobackend.repository.OtpVerificationRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class UserService {

    private static final List<UserRole> ORGANIZATION_ADMIN_ROLES = List.of(
            UserRole.MANUFACTURER_ADMIN, UserRole.DISTRIBUTOR_ADMIN);

    private final UserRepository userRepository;
    private final CustomerAddressRepository customerAddressRepository;
    private final PushDeviceTokenRepository pushDeviceTokenRepository;
    private final OtpVerificationRepository otpRepository;
    private final RefreshTokenService refreshTokenService;

    @Autowired
    public UserService(UserRepository userRepository, CustomerAddressRepository customerAddressRepository,
                       PushDeviceTokenRepository pushDeviceTokenRepository,
                       OtpVerificationRepository otpRepository, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.customerAddressRepository = customerAddressRepository;
        this.pushDeviceTokenRepository = pushDeviceTokenRepository;
        this.otpRepository = otpRepository;
        this.refreshTokenService = refreshTokenService;
    }

    /** Compatibility constructor for existing unit fixtures that do not exercise mobile profile APIs. */
    public UserService(UserRepository userRepository, OtpVerificationRepository otpRepository,
                       RefreshTokenService refreshTokenService) {
        this(userRepository, null, null, otpRepository, refreshTokenService);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> listForPlatform(AuthenticatedUser actor,
                                              UUID organizationId,
                                              UserStatus status,
                                              UserRole role,
                                              Pageable pageable) {
        requirePlatformAdmin(actor);
        return userRepository.searchForAdministration(organizationId, status, role, pageable)
                .map(UserResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> listForOrganization(AuthenticatedUser actor, Pageable pageable) {
        UUID organizationId = requireOrganizationAdmin(actor);
        return userRepository.searchForAdministration(organizationId, null, null, pageable)
                .map(UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse getForPlatform(AuthenticatedUser actor, UUID userId) {
        requirePlatformAdmin(actor);
        return userRepository.findWithOrganizationById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
    }

    @Transactional
    public UserResponse updateStatusForPlatform(AuthenticatedUser actor, UUID userId,
                                                UpdateUserStatusRequest request) {
        requirePlatformAdmin(actor);
        User target = lockTargetWithAdminSetIfRequired(userId);
        ensurePlatformMayManage(target, actor, request.status());
        applyStatus(target, request.status());
        return UserResponse.from(target);
    }

    @Transactional
    public UserResponse updateStatusForOrganization(AuthenticatedUser actor, UUID userId,
                                                    UpdateUserStatusRequest request) {
        UUID organizationId = requireOrganizationAdmin(actor);
        if (actor.userId().equals(userId)) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "An administrator cannot change their own account status");
        }

        // Lock all organization administrators before evaluating the last-admin
        // rule. This prevents two concurrent requests from disabling the last
        // two active administrators at the same time.
        List<User> administrators = userRepository
                .findOrganizationAdministratorsForUpdate(organizationId, ORGANIZATION_ADMIN_ROLES);
        User target = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
        ensureOrganizationTarget(target, organizationId);
        ensureNotLastActiveAdministrator(target, request.status(), administrators);
        applyStatus(target, request.status());
        return UserResponse.from(target);
    }

    private User lockTargetWithAdminSetIfRequired(UUID userId) {
        User target = userRepository.findWithOrganizationById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
        if (target.getOrganization() != null && isOrganizationAdmin(target.getRole())) {
            List<User> administrators = userRepository.findOrganizationAdministratorsForUpdate(
                    target.getOrganization().getId(), ORGANIZATION_ADMIN_ROLES);
            return administrators.stream()
                    .filter(candidate -> candidate.getId().equals(userId))
                    .findFirst()
                    .orElseGet(() -> userRepository.findByIdForUpdate(userId)
                            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found")));
        }
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
    }

    private void ensurePlatformMayManage(User target, AuthenticatedUser actor,
                                         UserStatus requestedStatus) {
        if (target.getRole() == UserRole.SUPER_ADMIN) {
            throw new ApiException(ErrorCode.ROLE_NOT_ALLOWED,
                    "Platform administrator accounts require a controlled operator workflow");
        }
        if (actor.userId().equals(target.getId())) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "An administrator cannot change their own account status");
        }
        if (target.getOrganization() != null && isOrganizationAdmin(target.getRole())) {
            List<User> administrators = userRepository.findOrganizationAdministratorsForUpdate(
                    target.getOrganization().getId(), ORGANIZATION_ADMIN_ROLES);
            ensureNotLastActiveAdministrator(target, requestedStatus, administrators);
        }
    }

    private void applyStatus(User target, UserStatus status) {
        if (status == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "User status is required");
        }
        target.setStatus(status);
        if (status != UserStatus.ACTIVE) {
            refreshTokenService.revokeAll(target.getId());
        }
    }

    private void ensureOrganizationTarget(User target, UUID organizationId) {
        if (target.getOrganization() == null
                || !organizationId.equals(target.getOrganization().getId())
                || target.getRole() == UserRole.CUSTOMER
                || target.getRole() == UserRole.DRIVER
                || target.getRole() == UserRole.SUPER_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }

    private void ensureNotLastActiveAdministrator(User target, UserStatus requestedStatus,
                                                  List<User> administrators) {
        if (requestedStatus == UserStatus.ACTIVE
                || target.getStatus() != UserStatus.ACTIVE
                || !isOrganizationAdmin(target.getRole())) {
            return;
        }
        long otherActiveAdministrators = administrators.stream()
                .filter(candidate -> !candidate.getId().equals(target.getId()))
                .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                .count();
        if (otherActiveAdministrators == 0) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "The organization must retain at least one active administrator");
        }
    }

    @Transactional(readOnly = true)
    public List<CustomerAddressResponse> listMyAddresses(UUID userId) {
        requireCustomer(userId);
        return customerAddressRepository.findAllByUser_IdOrderByDefaultAddressDescCreatedAtDesc(userId)
                .stream().map(CustomerAddressResponse::from).toList();
    }

    @Transactional
    public CustomerAddressResponse createMyAddress(UUID userId, CustomerAddressRequest request) {
        User user = requireCustomerForUpdate(userId);
        validateCoordinates(request.latitude(), request.longitude());
        CustomerAddress address = new CustomerAddress();
        address.setUser(user);
        if (request.defaultAddress()) clearDefaultAddress(userId);
        applyAddress(address, request);
        return CustomerAddressResponse.from(customerAddressRepository.save(address));
    }

    @Transactional
    public CustomerAddressResponse updateMyAddress(UUID userId, UUID addressId, CustomerAddressRequest request) {
        User user = requireCustomerForUpdate(userId);
        CustomerAddress address = customerAddressRepository.findByIdAndUser_Id(addressId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Address not found"));
        validateCoordinates(request.latitude(), request.longitude());
        if (request.defaultAddress()) clearDefaultAddress(userId);
        applyAddress(address, request);
        return CustomerAddressResponse.from(customerAddressRepository.save(address));
    }

    @Transactional
    public void deleteMyAddress(UUID userId, UUID addressId) {
        CustomerAddress address = customerAddressRepository.findByIdAndUser_Id(addressId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Address not found"));
        customerAddressRepository.delete(address);
    }

    @Transactional
    public PushDeviceTokenResponse registerPushToken(UUID userId, PushDeviceTokenRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
        String tokenValue = request.token().trim();
        PushDeviceToken token = pushDeviceTokenRepository.findByDeviceToken(tokenValue).orElse(null);
        if (token != null && !token.getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.CONFLICT, "This device token is registered to another account");
        }
        if (token == null) {
            token = new PushDeviceToken();
            token.setUser(user);
            token.setDeviceToken(tokenValue);
        }
        token.setPlatform(request.platform() == null ? null : request.platform().trim().toLowerCase(java.util.Locale.ROOT));
        return PushDeviceTokenResponse.from(pushDeviceTokenRepository.save(token));
    }

    @Transactional
    public void unregisterPushToken(UUID userId, String tokenValue) {
        pushDeviceTokenRepository.findByDeviceTokenAndUser_Id(tokenValue.trim(), userId)
                .ifPresent(pushDeviceTokenRepository::delete);
    }

    private User requireCustomer(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
        if (user.getRole() != UserRole.CUSTOMER) throw new ApiException(ErrorCode.ACCESS_DENIED);
        return user;
    }

    private User requireCustomerForUpdate(UUID userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
        if (user.getRole() != UserRole.CUSTOMER) throw new ApiException(ErrorCode.ACCESS_DENIED);
        return user;
    }

    private void clearDefaultAddress(UUID userId) {
        customerAddressRepository.findAllByUser_IdOrderByDefaultAddressDescCreatedAtDesc(userId)
                .forEach(address -> address.setDefaultAddress(false));
    }

    private static void applyAddress(CustomerAddress address, CustomerAddressRequest request) {
        address.setLabel(request.label().trim());
        address.setRecipientName(request.recipientName().trim());
        address.setPhoneNumber(com.example.distrobackend.Util.PhoneNormalizer.normalize(request.phoneNumber()));
        address.setAddressLine(request.addressLine().trim());
        address.setCity(request.city() == null ? null : request.city().trim());
        address.setCounty(request.county() == null ? null : request.county().trim());
        address.setLatitude(request.latitude());
        address.setLongitude(request.longitude());
        address.setDefaultAddress(request.defaultAddress());
    }

    private static void validateCoordinates(Double latitude, Double longitude) {
        if ((latitude == null) != (longitude == null)
                || (latitude != null && (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180))) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Provide valid latitude and longitude together");
        }
    }

    private UUID requireOrganizationAdmin(AuthenticatedUser actor) {
        if (actor == null || actor.organizationId() == null
                || (actor.role() != UserRole.MANUFACTURER_ADMIN
                && actor.role() != UserRole.DISTRIBUTOR_ADMIN)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return actor.organizationId();
    }

    private static void requirePlatformAdmin(AuthenticatedUser actor) {
        if (actor == null || actor.role() != UserRole.SUPER_ADMIN
                || actor.organizationId() != null || actor.organizationType() != null) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }

    private static boolean isOrganizationAdmin(UserRole role) {
        return role == UserRole.MANUFACTURER_ADMIN || role == UserRole.DISTRIBUTOR_ADMIN;
    }

    /**
     * Soft-delete. Orders and payments stay attached to the row, but the
     * person can register again because email and phone are released.
     */
    @Transactional
    public void deleteUserAccount(UUID userId) {
        User user = lockTargetWithAdminSetIfRequired(userId);
        if (user.getRole() == UserRole.SUPER_ADMIN) {
            throw new ApiException(ErrorCode.ROLE_NOT_ALLOWED,
                    "Platform administrator accounts require a controlled operator workflow");
        }
        if (user.getOrganization() != null && isOrganizationAdmin(user.getRole())) {
            List<User> administrators = userRepository.findOrganizationAdministratorsForUpdate(
                    user.getOrganization().getId(), ORGANIZATION_ADMIN_ROLES);
            ensureNotLastActiveAdministrator(user, UserStatus.DEACTIVATED, administrators);
        }

        otpRepository.deleteByUser_Id(userId);
        customerAddressRepository.deleteAllByUser_Id(userId);
        pushDeviceTokenRepository.deleteAllByUser_Id(userId);
        user.setStatus(UserStatus.DEACTIVATED);
        user.setEmail("deleted_" + UUID.randomUUID() + "@anonymized.com");
        user.setPhoneNumber("deleted_" + UUID.randomUUID().toString().substring(0, 8));
        user.setPasswordHash("DELETED");
        user.setFullName("Deleted User");
        userRepository.save(user);
        refreshTokenService.revokeAll(userId);
        log.info("Deactivated and anonymized user {}", userId);
    }

    /**
     * Platform-admin deletion follows the same protections as a status change:
     * a platform admin cannot delete themselves or another platform admin, and
     * the last active organization administrator cannot be removed.
     */
    @Transactional
    public void deleteUserAccount(AuthenticatedUser actor, UUID userId) {
        requirePlatformAdmin(actor);
        User target = lockTargetWithAdminSetIfRequired(userId);
        ensurePlatformMayManage(target, actor, UserStatus.DEACTIVATED);

        otpRepository.deleteByUser_Id(userId);
        customerAddressRepository.deleteAllByUser_Id(userId);
        pushDeviceTokenRepository.deleteAllByUser_Id(userId);
        target.setStatus(UserStatus.DEACTIVATED);
        target.setEmail("deleted_" + UUID.randomUUID() + "@anonymized.com");
        target.setPhoneNumber("deleted_" + UUID.randomUUID().toString().substring(0, 8));
        target.setPasswordHash("DELETED");
        target.setFullName("Deleted User");
        userRepository.save(target);
        refreshTokenService.revokeAll(userId);
        log.info("Platform administrator deactivated and anonymized user {}", userId);
    }
}
