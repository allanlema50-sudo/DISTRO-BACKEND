package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Domain.enums.UserStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.UserResponse;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    // ================================================================
    // GET USERS
    // ================================================================

    @Transactional(readOnly = true)
    public List<UserResponse> getUsers(
            AuthenticatedUser me
    ) {

        if (me.role() == UserRole.PLATFORM_ADMIN) {

            return userRepository.findAll()
                    .stream()
                    .map(UserResponse::from)
                    .toList();
        }

        if (isOrganizationAdmin(me.role())) {

            if (me.organizationId() == null) {

                throw new ApiException(
                        ErrorCode.BAD_REQUEST,
                        "Your account is not associated with an organization"
                );
            }

            return userRepository
                    .findByOrganizationId(
                            me.organizationId()
                    )
                    .stream()
                    .map(UserResponse::from)
                    .toList();
        }

        throw new ApiException(
                ErrorCode.ACCESS_DENIED,
                "You do not have permission to view users"
        );
    }

    // ================================================================
    // GET SINGLE USER
    // ================================================================

    @Transactional(readOnly = true)
    public UserResponse getUserById(
            UUID id,
            AuthenticatedUser me
    ) {

        User user = findUserForViewer(
                id,
                me
        );

        return UserResponse.from(user);
    }

    // ================================================================
    // GET USERS BY STATUS
    // ================================================================

    @Transactional(readOnly = true)
    public List<UserResponse> getUsersByStatus(
            UserStatus status,
            AuthenticatedUser me
    ) {

        if (me.role() == UserRole.PLATFORM_ADMIN) {

            return userRepository
                    .findByStatus(status)
                    .stream()
                    .map(UserResponse::from)
                    .toList();
        }

        requireOrganizationAdmin(me);

        return userRepository
                .findByOrganizationIdAndStatus(
                        me.organizationId(),
                        status
                )
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    // ================================================================
    // GET USERS BY ROLE
    // ================================================================

    @Transactional(readOnly = true)
    public List<UserResponse> getUsersByRole(
            UserRole role,
            AuthenticatedUser me
    ) {

        if (me.role() == UserRole.PLATFORM_ADMIN) {

            return userRepository
                    .findByRole(role)
                    .stream()
                    .map(UserResponse::from)
                    .toList();
        }

        requireOrganizationAdmin(me);

        /*
         * Organization admins may only query roles belonging
         * to their own organization type.
         */
        if (role.organizationtype() == null
                || me.organizationType() == null
                || role.organizationtype()
                != me.organizationType()) {

            throw new ApiException(
                    ErrorCode.ROLE_NOT_ALLOWED,
                    "You cannot view users with this role"
            );
        }

        return userRepository
                .findByOrganizationIdAndRole(
                        me.organizationId(),
                        role
                )
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    // ================================================================
    // UPDATE USER STATUS
    // ================================================================

    @Transactional
    public UserResponse updateUserStatus(
            UUID id,
            UserStatus newStatus,
            AuthenticatedUser me
    ) {

        User user =
                findUserForViewer(
                        id,
                        me
                );

        validateStatusChange(
                user,
                newStatus,
                me
        );

        user.setStatus(newStatus);

        User savedUser =
                userRepository.save(user);

        return UserResponse.from(savedUser);
    }

    // ================================================================
    // FIND USER WITH ORGANIZATION SCOPE
    // ================================================================

    private User findUserForViewer(
            UUID id,
            AuthenticatedUser me
    ) {

        if (me.role() == UserRole.PLATFORM_ADMIN) {

            return userRepository
                    .findById(id)
                    .orElseThrow(() ->
                            new ApiException(
                                    ErrorCode.NOT_FOUND,
                                    "User not found"
                            )
                    );
        }

        requireOrganizationAdmin(me);

        return userRepository
                .findByIdAndOrganizationId(
                        id,
                        me.organizationId()
                )
                .orElseThrow(() ->
                        new ApiException(
                                ErrorCode.NOT_FOUND,
                                "User not found"
                        )
                );
    }

    // ================================================================
    // STATUS CHANGE RULES
    // ================================================================

    private void validateStatusChange(
            User target,
            UserStatus newStatus,
            AuthenticatedUser me
    ) {

        /*
         * Nobody should be able to deactivate or suspend
         * the platform administrator through this endpoint.
         */
        if (target.getRole()
                == UserRole.PLATFORM_ADMIN) {

            throw new ApiException(
                    ErrorCode.ROLE_NOT_ALLOWED,
                    "Platform administrator accounts cannot be changed here"
            );
        }

        /*
         * Organization administrators can only manage staff.
         * They cannot activate/deactivate another organization admin.
         */
        if (isOrganizationAdmin(me.role())) {

            if (target.getRole()
                    != UserRole.MANUFACTURER_STAFF
                    && target.getRole()
                    != UserRole.DISTRIBUTOR_STAFF) {

                throw new ApiException(
                        ErrorCode.ROLE_NOT_ALLOWED,
                        "Organization administrators can only manage staff accounts"
                );
            }

            /*
             * Staff must belong to the same organization.
             *
             * The repository lookup already enforces this, but
             * we keep this check here as a second safety boundary.
             */
            if (target.getOrganization() == null
                    || !me.organizationId()
                    .equals(
                            target.getOrganization().getId()
                    )) {

                throw new ApiException(
                        ErrorCode.ACCESS_DENIED,
                        "You cannot manage users outside your organization"
                );
            }
        }

        if (newStatus == null) {

            throw new ApiException(
                    ErrorCode.BAD_REQUEST,
                    "User status is required"
            );
        }
    }

    // ================================================================
    // ACCESS HELPERS
    // ================================================================

    private void requireOrganizationAdmin(
            AuthenticatedUser me
    ) {

        if (!isOrganizationAdmin(me.role())) {

            throw new ApiException(
                    ErrorCode.ACCESS_DENIED,
                    "You do not have permission to manage users"
            );
        }

        if (me.organizationId() == null) {

            throw new ApiException(
                    ErrorCode.BAD_REQUEST,
                    "Your account is not associated with an organization"
            );
        }
    }

    private boolean isOrganizationAdmin(
            UserRole role
    ) {

        return role == UserRole.MANUFACTURER_ADMIN
                || role == UserRole.DISTRIBUTOR_ADMIN;
    }
}