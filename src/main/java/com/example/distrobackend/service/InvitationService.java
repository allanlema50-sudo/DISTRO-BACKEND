package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Invitation;
import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.enums.InvitationStatus;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.dto.CreateInvitationRequest;
import com.example.distrobackend.dto.InvitationResponse;
import com.example.distrobackend.repository.InvitationRepository;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final OrganizationRepository organizationRepository;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /*
     * Development frontend URL.
     *
     * Later this should come from application.properties.
     */
    private static final String FRONTEND_URL = "http://localhost:4200";

    @Transactional
    public InvitationResponse createInvitation(
            CreateInvitationRequest request,
            AuthenticatedUser me
    ) {

        // --------------------------------------------------
        // 1. Only organization admins can invite users
        // --------------------------------------------------

        if (me.role() != UserRole.DISTRIBUTOR_ADMIN
                && me.role() != UserRole.MANUFACTURER_ADMIN) {

            throw new RuntimeException(
                    "Only organization administrators can send invitations"
            );
        }

        // --------------------------------------------------
        // 2. Get the administrator's organization
        // --------------------------------------------------

        if (me.organizationId() == null) {
            throw new RuntimeException(
                    "Your account is not associated with an organization"
            );
        }

        Organization organization =
                organizationRepository.findById(me.organizationId())
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Organization not found"
                                )
                        );

        // --------------------------------------------------
        // 3. Validate invited role
        // --------------------------------------------------

        if (request.role() != UserRole.DRIVER
                && request.role() != UserRole.DISTRIBUTOR_STAFF
                && request.role() != UserRole.MANUFACTURER_STAFF) {

            throw new RuntimeException(
                    "This role cannot be invited"
            );
        }

        // --------------------------------------------------
        // 4. Make sure the role matches the organization
        // --------------------------------------------------

        if (request.role() == UserRole.DISTRIBUTOR_STAFF
                && !organization.getType().name().equals("DISTRIBUTOR")) {

            throw new RuntimeException(
                    "Distributor staff can only join distributor organizations"
            );
        }

        if (request.role() == UserRole.MANUFACTURER_STAFF
                && !organization.getType().name().equals("MANUFACTURER")) {

            throw new RuntimeException(
                    "Manufacturer staff can only join manufacturer organizations"
            );
        }

        // --------------------------------------------------
        // 5. Generate secure invitation token
        // --------------------------------------------------

        String token = generateToken();

        // --------------------------------------------------
        // 6. Create invitation
        // --------------------------------------------------

        Invitation invitation = new Invitation();

        invitation.setOrganization(organization);
        invitation.setFullName(request.fullName().trim());
        invitation.setEmail(request.email().trim().toLowerCase());
        invitation.setPhoneNumber(
                request.phoneNumber() == null
                        ? null
                        : request.phoneNumber().trim()
        );
        invitation.setRole(request.role());
        invitation.setToken(token);
        invitation.setStatus(InvitationStatus.PENDING);

        // Invitation valid for 48 hours
        invitation.setExpiresAt(
                OffsetDateTime.now(ZoneOffset.UTC).plusHours(48)
        );

        Invitation savedInvitation =
                invitationRepository.save(invitation);

        // --------------------------------------------------
        // 7. Create development invitation link
        // --------------------------------------------------

        String invitationLink =
                FRONTEND_URL
                        + "/accept-invitation/"
                        + savedInvitation.getToken();

        return InvitationResponse.from(
                savedInvitation,
                invitationLink
        );
    }

    private String generateToken() {

        byte[] bytes = new byte[32];

        SECURE_RANDOM.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }
}