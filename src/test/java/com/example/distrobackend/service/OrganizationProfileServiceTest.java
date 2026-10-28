package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.dto.UpdateOrganizationProfileRequest;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationProfileServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Test
    void organizationStaffCanReadOnlyTheirOwnProfile() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = organization(organizationId, Organizationtype.MANUFACTURER);
        when(organizationRepository.findById(organizationId)).thenReturn(Optional.of(organization));

        var response = new OrganizationProfileService(organizationRepository).get(
                actor(UserRole.MANUFACTURER_STAFF, organizationId, Organizationtype.MANUFACTURER));

        assertThat(response.id()).isEqualTo(organizationId);
        verify(organizationRepository).findById(organizationId);
    }

    @Test
    void organizationAdminUpdateRequiresTheCurrentVersion() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = organization(organizationId, Organizationtype.DISTRIBUTOR);
        organization.setVersion(3);
        when(organizationRepository.findByIdForUpdate(organizationId)).thenReturn(Optional.of(organization));

        assertThatThrownBy(() -> new OrganizationProfileService(organizationRepository).update(
                actor(UserRole.DISTRIBUTOR_ADMIN, organizationId, Organizationtype.DISTRIBUTOR),
                new UpdateOrganizationProfileRequest("New Name", null, 2L)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("changed by another request");
    }

    @Test
    void organizationAdminCanUpdateNormalizedProfileFields() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = organization(organizationId, Organizationtype.MANUFACTURER);
        when(organizationRepository.findByIdForUpdate(organizationId)).thenReturn(Optional.of(organization));
        when(organizationRepository.findByEmailIgnoreCaseAndIdNot("new@example.com", organizationId))
                .thenReturn(Optional.empty());
        when(organizationRepository.saveAndFlush(organization)).thenReturn(organization);

        new OrganizationProfileService(organizationRepository).update(
                actor(UserRole.MANUFACTURER_ADMIN, organizationId, Organizationtype.MANUFACTURER),
                new UpdateOrganizationProfileRequest("  New Name ", " New@Example.com ", 0L));

        assertThat(organization.getName()).isEqualTo("New Name");
        assertThat(organization.getEmail()).isEqualTo("new@example.com");
        verify(organizationRepository).saveAndFlush(organization);
    }

    private static Organization organization(UUID id, Organizationtype type) {
        Organization organization = new Organization();
        organization.setId(id);
        organization.setName("Original");
        organization.setType(type);
        return organization;
    }

    private static AuthenticatedUser actor(UserRole role, UUID organizationId, Organizationtype type) {
        return new AuthenticatedUser(UUID.randomUUID(), role, organizationId, type,
                Instant.now().plusSeconds(300));
    }
}
