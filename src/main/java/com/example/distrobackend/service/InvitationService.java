package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Invitation;
import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.InvitationStatus;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Domain.enums.UserStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.Util.PhoneNormalizer;
import com.example.distrobackend.dto.AcceptInvitationRequest;
import com.example.distrobackend.dto.CreateInvitationRequest;
import com.example.distrobackend.dto.InvitationPublicResponse;
import com.example.distrobackend.dto.InvitationResponse;
import com.example.distrobackend.repository.InvitationRepository;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    private static final SecureRandom SECURE_RANDOM =
            new SecureRandom();

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    // ================================================================
    // CREATE INVITATION
    // ================================================================

    @Transactional
    public InvitationResponse createInvitation(
            CreateInvitationRequest request,
            AuthenticatedUser me
    ) {

        UserRole invitedRole = request.role();

        Organization organization;

        // ------------------------------------------------------------
        // PLATFORM ADMIN
        // ------------------------------------------------------------

        if (me.role() == UserRole.PLATFORM_ADMIN) {

            if (request.organizationId() == null) {
                throw new ApiException(
                        ErrorCode.BAD_REQUEST,
                        "Organization is required when the platform admin sends an invitation"
                );
            }

            if (invitedRole != UserRole.MANUFACTURER_ADMIN
                    && invitedRole != UserRole.DISTRIBUTOR_ADMIN) {

                throw new ApiException(
                        ErrorCode.ROLE_NOT_ALLOWED,
                        "Platform administrators can only invite organization administrators"
                );
            }

            organization =
                    organizationRepository
                            .findById(request.organizationId())
                            .orElseThrow(() ->
                                    new ApiException(
                                            ErrorCode.NOT_FOUND,
                                            "Organization not found"
                                    )
                            );
        }

        // ------------------------------------------------------------
        // ORGANIZATION ADMIN
        // ------------------------------------------------------------

        else if (me.role() == UserRole.MANUFACTURER_ADMIN
                || me.role() == UserRole.DISTRIBUTOR_ADMIN) {

            if (me.organizationId() == null) {
                throw new ApiException(
                        ErrorCode.BAD_REQUEST,
                        "Your account is not associated with an organization"
                );
            }

            organization =
                    organizationRepository
                            .findById(me.organizationId())
                            .orElseThrow(() ->
                                    new ApiException(
                                            ErrorCode.NOT_FOUND,
                                            "Organization not found"
                                    )
                            );

            if (invitedRole != UserRole.MANUFACTURER_STAFF
                    && invitedRole != UserRole.DISTRIBUTOR_STAFF) {

                throw new ApiException(
                        ErrorCode.ROLE_NOT_ALLOWED,
                        "Organization administrators can only invite organization staff"
                );
            }
        }

        else {
            throw new ApiException(
                    ErrorCode.ACCESS_DENIED,
                    "You do not have permission to send invitations"
            );
        }

        // ------------------------------------------------------------
        // ROLE / ORGANIZATION VALIDATION
        // ------------------------------------------------------------

        if (invitedRole.organizationtype() == null) {
            throw new ApiException(
                    ErrorCode.ROLE_NOT_ALLOWED,
                    "This role cannot be activated through an organization invitation"
            );
        }

        if (invitedRole.organizationtype() != organization.getType()) {
            throw new ApiException(
                    ErrorCode.ROLE_NOT_ALLOWED,
                    "The invited role does not match the organization type"
            );
        }

        return createInvitationForOrganization(
                request.fullName(),
                request.email(),
                request.phoneNumber(),
                invitedRole,
                organization
        );
    }

    // ================================================================
    // SHARED INVITATION CREATION
    // ================================================================

    private InvitationResponse createInvitationForOrganization(
            String fullName,
            String email,
            String phoneNumber,
            UserRole invitedRole,
            Organization organization
    ) {

        String normalizedEmail =
                email.trim().toLowerCase();

        // ------------------------------------------------------------
        // DUPLICATE INVITATION
        // ------------------------------------------------------------

        boolean pendingInvitation =
                invitationRepository
                        .existsByEmailIgnoreCaseAndOrganizationIdAndStatus(
                                normalizedEmail,
                                organization.getId(),
                                InvitationStatus.PENDING
                        );

        if (pendingInvitation) {
            throw new ApiException(
                    ErrorCode.CONFLICT,
                    "A pending invitation already exists for this email"
            );
        }

        // ------------------------------------------------------------
        // EXISTING ACCOUNT
        // ------------------------------------------------------------

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ApiException(
                    ErrorCode.DUPLICATE_EMAIL
            );
        }

        String phone =
                PhoneNormalizer.normalize(phoneNumber);

        if (userRepository.existsByPhoneNumber(phone)) {
            throw new ApiException(
                    ErrorCode.DUPLICATE_PHONE
            );
        }

        // ------------------------------------------------------------
        // SECURE TOKEN
        // ------------------------------------------------------------

        String token = generateUniqueToken();

        Invitation invitation =
                new Invitation();

        invitation.setOrganization(organization);

        invitation.setFullName(
                fullName.trim()
        );

        invitation.setEmail(
                normalizedEmail
        );

        invitation.setPhoneNumber(
                phone
        );

        invitation.setRole(
                invitedRole
        );

        invitation.setToken(
                token
        );

        invitation.setStatus(
                InvitationStatus.PENDING
        );

        invitation.setExpiresAt(
                OffsetDateTime
                        .now(ZoneOffset.UTC)
                        .plusHours(48)
        );

        Invitation savedInvitation =
                invitationRepository.save(invitation);

        String invitationLink =
                frontendUrl
                        + "/accept-invitation/"
                        + savedInvitation.getToken();

        // ------------------------------------------------------------
        // SEND ACTIVATION EMAIL
        // ------------------------------------------------------------

        emailService.sendActivationEmail(
                savedInvitation.getEmail(),
                savedInvitation.getFullName(),
                organization.getName(),
                invitedRole.name(),
                invitationLink
        );

        return InvitationResponse.from(
                savedInvitation,
                invitationLink
        );
    }

    // ================================================================
    // GET INVITATION
    // ================================================================

    @Transactional
    public InvitationPublicResponse getInvitation(
            String token
    ) {

        Invitation invitation =
                invitationRepository
                        .findByToken(token)
                        .orElseThrow(() ->
                                new ApiException(
                                        ErrorCode.NOT_FOUND,
                                        "Invitation not found"
                                )
                        );

        expireIfNecessary(invitation);

        if (invitation.getStatus()
                != InvitationStatus.PENDING) {

            throw new ApiException(
                    ErrorCode.BAD_REQUEST,
                    "This invitation is no longer available"
            );
        }

        return InvitationPublicResponse.from(
                invitation
        );
    }

    // ================================================================
    // ACCEPT INVITATION
    // ================================================================

    @Transactional
    public User acceptInvitation(
            String token,
            AcceptInvitationRequest request
    ) {

        Invitation invitation =
                invitationRepository
                        .findByToken(token)
                        .orElseThrow(() ->
                                new ApiException(
                                        ErrorCode.BAD_REQUEST,
                                        "Invalid invitation"
                                )
                        );

        expireIfNecessary(invitation);

        if (invitation.getStatus()
                != InvitationStatus.PENDING) {

            throw new ApiException(
                    ErrorCode.BAD_REQUEST,
                    "This invitation is no longer available"
            );
        }

        UserRole role =
                invitation.getRole();

        if (role != UserRole.MANUFACTURER_ADMIN
                && role != UserRole.MANUFACTURER_STAFF
                && role != UserRole.DISTRIBUTOR_ADMIN
                && role != UserRole.DISTRIBUTOR_STAFF) {

            throw new ApiException(
                    ErrorCode.ROLE_NOT_ALLOWED,
                    "This role cannot be activated through an invitation"
            );
        }

        String phone =
                PhoneNormalizer.normalize(
                        request.phoneNumber()
                );

        if (userRepository.existsByEmail(
                invitation.getEmail())) {

            throw new ApiException(
                    ErrorCode.DUPLICATE_EMAIL
            );
        }

        if (userRepository.existsByPhoneNumber(phone)) {
            throw new ApiException(
                    ErrorCode.DUPLICATE_PHONE
            );
        }

        User user =
                new User();

        user.setFullName(
                invitation.getFullName()
        );

        user.setEmail(
                invitation.getEmail()
        );

        user.setPhoneNumber(
                phone
        );

        user.setPasswordHash(
                passwordEncoder.encode(
                        request.password()
                )
        );

        user.setRole(role);

        user.setOrganization(
                invitation.getOrganization()
        );

        user.setStatus(
                UserStatus.ACTIVE
        );

        user.setEmailVerified(true);
        user.setPhoneVerified(false);

        User savedUser =
                userRepository.save(user);

        invitation.setStatus(
                InvitationStatus.ACCEPTED
        );

        invitation.setAcceptedAt(
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        invitationRepository.save(invitation);

        return savedUser;
    }

    // ================================================================
    // HELPERS
    // ================================================================

    private void expireIfNecessary(
            Invitation invitation
    ) {

        if (invitation.getStatus()
                == InvitationStatus.PENDING
                && invitation.getExpiresAt()
                .isBefore(
                        OffsetDateTime.now(ZoneOffset.UTC)
                )) {

            invitation.setStatus(
                    InvitationStatus.EXPIRED
            );

            invitationRepository.save(
                    invitation
            );
        }
    }

    private String generateUniqueToken() {

        String token;

        do {

            byte[] bytes =
                    new byte[32];

            SECURE_RANDOM.nextBytes(bytes);

            token =
                    Base64
                            .getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(bytes);

        } while (
                invitationRepository.existsByToken(token)
        );

        return token;
    }
}
