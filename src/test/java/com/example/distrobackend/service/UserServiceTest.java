package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Domain.enums.UserStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.dto.UpdateUserStatusRequest;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.repository.OtpVerificationRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private OtpVerificationRepository otpRepository;
    @Mock private RefreshTokenService refreshTokenService;

    private UserService userService;
    private UUID organizationId;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, otpRepository, refreshTokenService);
        organizationId = UUID.randomUUID();
    }

    @Test
    void organizationAdminCannotSuspendTheirOwnAccount() {
        UUID adminId = UUID.randomUUID();
        AuthenticatedUser actor = principal(adminId, UserRole.MANUFACTURER_ADMIN);

        assertThatThrownBy(() -> userService.updateStatusForOrganization(
                actor, adminId, new UpdateUserStatusRequest(UserStatus.SUSPENDED)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("own account");

        verify(userRepository, never()).findOrganizationAdministratorsForUpdate(any(), any());
    }

    @Test
    void organizationAdminCannotDisableTheLastActiveOrganizationAdministrator() {
        UUID targetId = UUID.randomUUID();
        User target = organizationUser(targetId, UserRole.MANUFACTURER_ADMIN, UserStatus.ACTIVE);
        when(userRepository.findOrganizationAdministratorsForUpdate(any(), any()))
                .thenReturn(List.of(target));
        when(userRepository.findByIdForUpdate(targetId)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> userService.updateStatusForOrganization(
                principal(UUID.randomUUID(), UserRole.MANUFACTURER_ADMIN),
                targetId, new UpdateUserStatusRequest(UserStatus.DEACTIVATED)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("at least one active administrator");

        verify(refreshTokenService, never()).revokeAll(targetId);
    }

    @Test
    void organizationAdminCannotManageAMemberFromAnotherOrganization() {
        UUID targetId = UUID.randomUUID();
        Organization otherOrganization = organization(UUID.randomUUID(), Organizationtype.MANUFACTURER);
        User target = organizationUser(targetId, UserRole.MANUFACTURER_STAFF, UserStatus.ACTIVE);
        target.setOrganization(otherOrganization);
        when(userRepository.findOrganizationAdministratorsForUpdate(any(), any())).thenReturn(List.of());
        when(userRepository.findByIdForUpdate(targetId)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> userService.updateStatusForOrganization(
                principal(UUID.randomUUID(), UserRole.MANUFACTURER_ADMIN),
                targetId, new UpdateUserStatusRequest(UserStatus.SUSPENDED)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("permission");

        verify(refreshTokenService, never()).revokeAll(targetId);
    }

    private AuthenticatedUser principal(UUID userId, UserRole role) {
        return new AuthenticatedUser(userId, role, organizationId,
                Organizationtype.MANUFACTURER, Instant.now().plusSeconds(300));
    }

    private User organizationUser(UUID id, UserRole role, UserStatus status) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setStatus(status);
        user.setOrganization(organization(organizationId, Organizationtype.MANUFACTURER));
        return user;
    }

    private Organization organization(UUID id, Organizationtype type) {
        Organization organization = new Organization();
        organization.setId(id);
        organization.setType(type);
        return organization;
    }
}
