package com.example.distrobackend.security;

import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationServiceTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Claims claims;

    private JwtAuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        authenticationService = new JwtAuthenticationService(jwtService, userRepository);
    }

    @Test
    void acceptsDriverTokenOnlyWhenItBelongsToDistributorOrganization() {
        UUID driverId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        stubClaims(driverId, UserRole.DRIVER, organizationId, Organizationtype.DISTRIBUTOR);
        stubActiveUser(driverId, UserRole.DRIVER, organizationId, Organizationtype.DISTRIBUTOR);

        AuthenticationResult result = new AuthenticationResult(
                authenticationService.authenticateBearer("Bearer token"));

        AuthenticatedUser principal = (AuthenticatedUser) result.authentication().getPrincipal();
        assertThat(principal.role()).isEqualTo(UserRole.DRIVER);
        assertThat(principal.organizationId()).isEqualTo(organizationId);
        assertThat(principal.organizationType()).isEqualTo(Organizationtype.DISTRIBUTOR);
    }

    @Test
    void rejectsDriverTokenWithoutOrganizationClaims() {
        stubClaims(UUID.randomUUID(), UserRole.DRIVER, null, null);

        assertThatThrownBy(() -> authenticationService.authenticateBearer("Bearer token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Drivers must belong to an organization");
    }

    @Test
    void rejectsPlatformAdminTokenWithOrganizationClaims() {
        stubClaims(UUID.randomUUID(), UserRole.SUPER_ADMIN, UUID.randomUUID(), null);

        assertThatThrownBy(() -> authenticationService.authenticateBearer("Bearer token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Organization claims must be supplied together");
    }

    private void stubClaims(UUID userId, UserRole role,
                            UUID organizationId, Organizationtype organizationType) {
        when(jwtService.parse("token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("role", String.class)).thenReturn(role.name());
        when(claims.get("orgId", String.class))
                .thenReturn(organizationId == null ? null : organizationId.toString());
        when(claims.get("orgType", String.class))
                .thenReturn(organizationType == null ? null : organizationType.name());
        when(claims.getExpiration()).thenReturn(Date.from(Instant.now().plusSeconds(60)));
    }

    private void stubActiveUser(UUID userId, UserRole role,
                                UUID organizationId, Organizationtype organizationType) {
        User user = new User();
        user.setId(userId);
        user.setRole(role);
        if (organizationId != null) {
            Organization organization = new Organization();
            organization.setId(organizationId);
            organization.setType(organizationType);
            user.setOrganization(organization);
        }
        when(userRepository.findWithOrganizationById(userId)).thenReturn(java.util.Optional.of(user));
    }

    private record AuthenticationResult(
            org.springframework.security.core.Authentication authentication) {
    }
}
