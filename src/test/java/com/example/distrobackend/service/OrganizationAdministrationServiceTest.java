package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.dto.CreateOrganizationRequest;
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
class OrganizationAdministrationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Test
    void superAdminCanCreateOrganization() {
        OrganizationAdministrationService service = new OrganizationAdministrationService(organizationRepository);
        Organization saved = new Organization();
        saved.setId(UUID.randomUUID());
        saved.setName("Acme Manufacturer");
        saved.setEmail("admin@acme.example");
        saved.setType(Organizationtype.MANUFACTURER);
        when(organizationRepository.save(any(Organization.class))).thenReturn(saved);

        var response = service.create(superAdmin(), new CreateOrganizationRequest(
                " Acme Manufacturer ", "Admin@Acme.Example", Organizationtype.MANUFACTURER));

        assertThat(response.id()).isEqualTo(saved.getId());
        assertThat(response.name()).isEqualTo("Acme Manufacturer");
        verify(organizationRepository).save(any(Organization.class));
    }

    @Test
    void organizationUserCannotReadPlatformOrganizations() {
        OrganizationAdministrationService service = new OrganizationAdministrationService(organizationRepository);

        assertThatThrownBy(() -> service.get(
                new AuthenticatedUser(UUID.randomUUID(), UserRole.MANUFACTURER_ADMIN,
                        UUID.randomUUID(), Organizationtype.MANUFACTURER, Instant.now().plusSeconds(300)),
                UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("permission");
    }

    @Test
    void missingOrganizationReturnsNotFound() {
        OrganizationAdministrationService service = new OrganizationAdministrationService(organizationRepository);
        UUID organizationId = UUID.randomUUID();
        when(organizationRepository.findById(organizationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(superAdmin(), organizationId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Organization not found");
    }

    private static AuthenticatedUser superAdmin() {
        return new AuthenticatedUser(UUID.randomUUID(), UserRole.SUPER_ADMIN, null, null,
                Instant.now().plusSeconds(300));
    }
}
