package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Invitation;
import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.dto.CreateInvitationRequest;
import com.example.distrobackend.repository.InvitationRepository;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    @Mock private InvitationRepository invitationRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private OtpService otpService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private InvitationService invitationService;
    private Organization manufacturer;

    @BeforeEach
    void setUp() {
        invitationService = new InvitationService(
                invitationRepository, organizationRepository, userRepository,
                passwordEncoder, otpService, eventPublisher);
        ReflectionTestUtils.setField(invitationService, "frontendBaseUrl", "https://app.example.test");
        ReflectionTestUtils.setField(invitationService, "invitationTtl", Duration.ofDays(7));

        manufacturer = new Organization();
        manufacturer.setId(UUID.randomUUID());
        manufacturer.setName("Example Manufacturer");
        manufacturer.setType(Organizationtype.MANUFACTURER);
        when(organizationRepository.findById(manufacturer.getId())).thenReturn(java.util.Optional.of(manufacturer));
    }

    @Test
    void organizationAdminMayInviteAnotherAdminOfTheSameOrganizationType() {
        stubCreateDependencies();
        AuthenticatedUser actor = principal(UserRole.MANUFACTURER_ADMIN, manufacturer.getId(),
                Organizationtype.MANUFACTURER);
        CreateInvitationRequest request = request(UserRole.MANUFACTURER_ADMIN, manufacturer.getId());

        InvitationServiceResult result = new InvitationServiceResult(invitationService.create(actor, request));

        assertThat(result.response().role()).isEqualTo(UserRole.MANUFACTURER_ADMIN.name());
        verify(invitationRepository).save(any(Invitation.class));
    }

    @Test
    void organizationAdminCannotInviteAStaffOrAdminRoleForAnotherOrganizationType() {
        AuthenticatedUser actor = principal(UserRole.MANUFACTURER_ADMIN, manufacturer.getId(),
                Organizationtype.MANUFACTURER);
        CreateInvitationRequest request = request(UserRole.DISTRIBUTOR_ADMIN, manufacturer.getId());

        assertThatThrownBy(() -> invitationService.create(actor, request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("does not belong to the selected organization type");
    }

    @Test
    void platformAdminMayInviteAnAdministratorForASelectedOrganization() {
        stubCreateDependencies();
        AuthenticatedUser actor = principal(UserRole.SUPER_ADMIN, null, null);
        CreateInvitationRequest request = request(UserRole.MANUFACTURER_ADMIN, manufacturer.getId());

        InvitationServiceResult result = new InvitationServiceResult(invitationService.create(actor, request));

        assertThat(result.response().role()).isEqualTo(UserRole.MANUFACTURER_ADMIN.name());
    }

    private void stubCreateDependencies() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByPhoneNumber(any())).thenReturn(false);
        when(invitationRepository.existsByEmailIgnoreCaseAndOrganizationIdAndStatus(any(), any(), any()))
                .thenReturn(false);
        when(invitationRepository.save(any(Invitation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private CreateInvitationRequest request(UserRole role, UUID organizationId) {
        return new CreateInvitationRequest(
                "New Administrator", "new-admin@example.test", "+254700000001", role, organizationId);
    }

    private AuthenticatedUser principal(UserRole role, UUID organizationId,
                                        Organizationtype organizationType) {
        return new AuthenticatedUser(UUID.randomUUID(), role, organizationId, organizationType,
                Instant.now().plusSeconds(300));
    }

    private record InvitationServiceResult(
            com.example.distrobackend.dto.InvitationResponse response) {
    }
}
