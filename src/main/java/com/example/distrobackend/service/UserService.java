package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Domain.enums.UserStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.UpdateUserStatusRequest;
import com.example.distrobackend.dto.UserResponse;
import com.example.distrobackend.repository.OtpVerificationRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final List<UserRole> ORGANIZATION_ADMIN_ROLES = List.of(
            UserRole.MANUFACTURER_ADMIN, UserRole.DISTRIBUTOR_ADMIN);

    private final UserRepository userRepository;
    private final OtpVerificationRepository otpRepository;
    private final RefreshTokenService refreshTokenService;

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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
        if (user.getRole() == UserRole.SUPER_ADMIN) {
            throw new ApiException(ErrorCode.ROLE_NOT_ALLOWED,
                    "Platform administrator accounts require a controlled operator workflow");
        }

        otpRepository.deleteByUser_Id(userId);
        user.setStatus(UserStatus.DEACTIVATED);
        user.setEmail("deleted_" + UUID.randomUUID() + "@anonymized.com");
        user.setPhoneNumber("deleted_" + UUID.randomUUID().toString().substring(0, 8));
        user.setPasswordHash("DELETED");
        user.setFullName("Deleted User");
        userRepository.save(user);
        refreshTokenService.revokeAll(userId);
        log.info("Deactivated and anonymized user {}", userId);
    }
}
