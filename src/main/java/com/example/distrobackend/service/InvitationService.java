package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Invitation;
import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.InvitationStatus;
import com.example.distrobackend.Domain.enums.OtpPurpose;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.Exception.OnboardingExpiredException;
import com.example.distrobackend.Util.PhoneNormalizer;
import com.example.distrobackend.dto.AcceptInvitationRequest;
import com.example.distrobackend.dto.CreateDriverInvitationRequest;
import com.example.distrobackend.dto.CreateInvitationRequest;
import com.example.distrobackend.dto.InvitationPublicResponse;
import com.example.distrobackend.dto.InvitationResponse;
import com.example.distrobackend.dto.RegisterResponse;
import com.example.distrobackend.event.OnboardingEmailRequested;
import com.example.distrobackend.repository.InvitationRepository;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.Util.SecretTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvitationService {
    private final InvitationRepository invitationRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.frontend.base-url:http://localhost:4200}")
    private String frontendBaseUrl;

    @Value("${app.onboarding.invitation-ttl:7d}")
    private Duration invitationTtl;

    @Transactional
    public InvitationResponse create(AuthenticatedUser actor, CreateInvitationRequest request) {
        UserRole role = validateInvitedRole(request.role());
        Organization organization = resolveOrganization(actor, request.organizationId());
        if (role.organizationtype() != organization.getType()) {
            throw new ApiException(ErrorCode.ROLE_NOT_ALLOWED,
                    "The invited role does not belong to the selected organization type");
        }
        if (isOrganizationAdminRole(role)
                && !isPlatformAdmin(actor)
                && actor.role() != role) {
            throw new ApiException(ErrorCode.ROLE_NOT_ALLOWED,
                    "An organization administrator may invite only an administrator of the same organization type");
        }

        String email = normalizeEmail(request.email());
        String phone = PhoneNormalizer.normalize(request.phoneNumber());
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByPhoneNumber(phone)) {
            throw new ApiException(ErrorCode.DUPLICATE_PHONE);
        }
        if (invitationRepository.existsByEmailIgnoreCaseAndOrganizationIdAndStatus(
                email, organization.getId(), InvitationStatus.PENDING)) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "A pending invitation already exists for this email in the organization");
        }

        String rawToken = SecretTokenService.generate();
        Invitation invitation = new Invitation();
        invitation.setOrganization(organization);
        invitation.setFullName(request.fullName().trim());
        invitation.setEmail(email);
        invitation.setPhoneNumber(phone);
        invitation.setRole(role);
        invitation.setTokenHash(SecretTokenService.sha256(rawToken));
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setExpiresAt(OffsetDateTime.now().plus(invitationTtl));
        Invitation saved = invitationRepository.save(invitation);

        String link = buildLink("/accept-invitation?token=" + rawToken);
        eventPublisher.publishEvent(new OnboardingEmailRequested(
                saved.getEmail(),
                "You have been invited to LogiFlow",
                "You have been invited to join " + organization.getName()
                        + ". Complete setup here:\n\n" + link
        ));
        // The raw token is delivered only through the invitation email. It must
        // never be returned to the organization administrator who created it.
        return InvitationResponse.from(saved, null);
    }

    /**
     * Creates the driver account in an INVITED state before sending the email.
     * The account cannot authenticate until the invitation is accepted.
     */
    @Transactional
    public InvitationResponse createDriver(AuthenticatedUser actor,
                                           CreateDriverInvitationRequest request) {
        UUID organizationId = requireOrganizationAdmin(actor);
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Organization not found"));
        if (organization.getType() != com.example.distrobackend.Domain.enums.Organizationtype.DISTRIBUTOR) {
            throw new ApiException(ErrorCode.ROLE_NOT_ALLOWED,
                    "Drivers can only be onboarded by distributor organizations");
        }

        String email = normalizeEmail(request.email());
        String phone = PhoneNormalizer.normalize(request.phoneNumber());
        if (userRepository.existsByEmail(email)) throw new ApiException(ErrorCode.DUPLICATE_EMAIL);
        if (userRepository.existsByPhoneNumber(phone)) throw new ApiException(ErrorCode.DUPLICATE_PHONE);
        if (invitationRepository.existsByEmailIgnoreCaseAndOrganizationIdAndStatus(
                email, organizationId, InvitationStatus.PENDING)) {
            throw new ApiException(ErrorCode.CONFLICT, "A pending driver invitation already exists");
        }

        User invitedUser = new User();
        invitedUser.setFullName(request.fullName().trim());
        invitedUser.setEmail(email);
        invitedUser.setPhoneNumber(phone);
        invitedUser.setPasswordHash(passwordEncoder.encode(SecretTokenService.generate()));
        invitedUser.setRole(UserRole.DRIVER);
        invitedUser.setOrganization(organization);
        invitedUser.setStatus(com.example.distrobackend.Domain.enums.UserStatus.INVITED);
        invitedUser.setEmailVerified(false);
        invitedUser.setPhoneVerified(false);
        User savedUser = userRepository.save(invitedUser);

        // RiderProfile is created on first driver-profile read/update so the
        // invitation transaction does not need a second persistence aggregate.
        String rawToken = SecretTokenService.generate();
        Invitation invitation = new Invitation();
        invitation.setOrganization(organization);
        invitation.setInvitedUser(savedUser);
        invitation.setDriverLicenseNumber(request.licenseNumber().trim());
        invitation.setFullName(request.fullName().trim());
        invitation.setEmail(email);
        invitation.setPhoneNumber(phone);
        invitation.setRole(UserRole.DRIVER);
        invitation.setTokenHash(SecretTokenService.sha256(rawToken));
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setExpiresAt(OffsetDateTime.now().plus(invitationTtl));
        Invitation saved = invitationRepository.save(invitation);

        String link = buildLink("/accept-invitation?token=" + rawToken);
        eventPublisher.publishEvent(new OnboardingEmailRequested(
                saved.getEmail(), "You have been invited to drive for LogiFlow",
                "You have been invited to join " + organization.getName()
                        + " as a driver. Complete setup here:\n\n" + link));
        return InvitationResponse.from(saved, null);
    }

    @Transactional
    public InvitationResponse resendDriverInvitation(AuthenticatedUser actor, UUID driverId) {
        UUID organizationId = requireOrganizationAdmin(actor);
        User driver = userRepository.findWithOrganizationById(driverId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Driver not found"));
        if (driver.getRole() != UserRole.DRIVER || driver.getOrganization() == null
                || !organizationId.equals(driver.getOrganization().getId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Driver not found");
        }
        if (driver.getStatus() != com.example.distrobackend.Domain.enums.UserStatus.INVITED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Only an invited driver can receive another invitation");
        }

        Invitation previous = invitationRepository.findTopByInvitedUser_IdOrderByCreatedAtDesc(driverId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVITATION_NOT_FOUND));
        OffsetDateTime now = OffsetDateTime.now();
        if (previous.getStatus() == InvitationStatus.PENDING
                && previous.getExpiresAt().isAfter(now)) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "The driver's current invitation is still valid");
        }
        if (previous.getStatus() == InvitationStatus.PENDING) {
            previous.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(previous);
        }

        String rawToken = SecretTokenService.generate();
        Invitation invitation = new Invitation();
        invitation.setOrganization(driver.getOrganization());
        invitation.setInvitedUser(driver);
        invitation.setDriverLicenseNumber(previous.getDriverLicenseNumber());
        invitation.setFullName(driver.getFullName());
        invitation.setEmail(driver.getEmail());
        invitation.setPhoneNumber(driver.getPhoneNumber());
        invitation.setRole(UserRole.DRIVER);
        invitation.setTokenHash(SecretTokenService.sha256(rawToken));
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setExpiresAt(now.plus(invitationTtl));
        Invitation saved = invitationRepository.save(invitation);
        String link = buildLink("/accept-invitation?token=" + rawToken);
        eventPublisher.publishEvent(new OnboardingEmailRequested(
                saved.getEmail(), "Your LogiFlow driver invitation",
                "Complete your driver account setup here:\n\n" + link));
        return InvitationResponse.from(saved, null);
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> list(AuthenticatedUser actor) {
        List<Invitation> invitations = isPlatformAdmin(actor)
                ? invitationRepository.findAllByOrderByCreatedAtDesc()
                : invitationRepository.findByOrganizationId(requireOrganizationAdmin(actor));
        return invitations.stream()
                .map(invitation -> InvitationResponse.from(invitation, null))
                .toList();
    }

    @Transactional(noRollbackFor = OnboardingExpiredException.class)
    public InvitationPublicResponse getPublic(String rawToken) {
        Invitation invitation = findPending(rawToken);
        return InvitationPublicResponse.from(invitation);
    }

    @Transactional(noRollbackFor = OnboardingExpiredException.class)
    public RegisterResponse accept(String rawToken, AcceptInvitationRequest request) {
        Invitation invitation = invitationRepository.findByTokenHashForUpdate(SecretTokenService.sha256(rawToken))
                .orElseThrow(() -> new ApiException(ErrorCode.INVITATION_NOT_FOUND));
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ApiException(ErrorCode.INVITATION_STATE_CONFLICT,
                    "This invitation has already been used or revoked");
        }
        if (!invitation.getExpiresAt().isAfter(OffsetDateTime.now())) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw new OnboardingExpiredException(ErrorCode.INVITATION_EXPIRED);
        }

        String phone = PhoneNormalizer.normalize(request.phoneNumber());
        if (invitation.getPhoneNumber() != null
                && !invitation.getPhoneNumber().equals(phone)) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "The phone number does not match the invitation");
        }
        User user = invitation.getInvitedUser();
        if (user == null) {
            if (userRepository.existsByEmail(invitation.getEmail())) {
                throw new ApiException(ErrorCode.DUPLICATE_EMAIL);
            }
            if (userRepository.existsByPhoneNumber(phone)) {
                throw new ApiException(ErrorCode.DUPLICATE_PHONE);
            }
            user = new User();
            user.setFullName(invitation.getFullName());
            user.setEmail(invitation.getEmail());
            user.setPhoneNumber(phone);
            user.setRole(invitation.getRole());
            user.setOrganization(invitation.getOrganization());
        } else if (user.getStatus() != com.example.distrobackend.Domain.enums.UserStatus.INVITED) {
            throw new ApiException(ErrorCode.INVITATION_STATE_CONFLICT,
                    "The invited account is no longer available");
        } else {
            user.setPhoneNumber(phone);
        }
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(com.example.distrobackend.Domain.enums.UserStatus.ACTIVE);
        user.setPhoneVerified(false);
        // Accepting the emailed invitation verifies the organization user's email.
        user.setEmailVerified(true);
        userRepository.save(user);

        // Drivers require phone verification before login. The invitation email
        // establishes email ownership; this code completes the remaining step.
        if (user.getRole() == UserRole.DRIVER) {
            otpService.issue(user, OtpPurpose.ACCOUNT_VERIFICATION);
        }

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(OffsetDateTime.now());
        invitationRepository.save(invitation);
        String message = user.getRole() == UserRole.DRIVER
                ? "Invitation accepted. Enter the account-verification code before signing in."
                : "Invitation accepted. You can now sign in.";
        return new RegisterResponse(user.getId(), message);
    }

    @Transactional
    public void revoke(AuthenticatedUser actor, UUID invitationId) {
        Invitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVITATION_NOT_FOUND));
        if (!isPlatformAdmin(actor)
                && !organizationMatches(actor, invitation.getOrganization().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ApiException(ErrorCode.INVITATION_STATE_CONFLICT,
                    "Only pending invitations can be revoked");
        }
        invitation.setStatus(InvitationStatus.REVOKED);
        if (invitation.getInvitedUser() != null) {
            invitation.getInvitedUser().setStatus(
                    com.example.distrobackend.Domain.enums.UserStatus.DEACTIVATED);
        }
    }

    private Invitation findPending(String rawToken) {
        Invitation invitation = invitationRepository.findByTokenHash(SecretTokenService.sha256(rawToken))
                .orElseThrow(() -> new ApiException(ErrorCode.INVITATION_NOT_FOUND));
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ApiException(ErrorCode.INVITATION_STATE_CONFLICT,
                    "This invitation has already been used or revoked");
        }
        if (!invitation.getExpiresAt().isAfter(OffsetDateTime.now())) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw new OnboardingExpiredException(ErrorCode.INVITATION_EXPIRED);
        }
        return invitation;
    }

    private Organization resolveOrganization(AuthenticatedUser actor, UUID requestedOrganizationId) {
        if (isPlatformAdmin(actor)) {
            if (requestedOrganizationId == null) {
                throw new ApiException(ErrorCode.BAD_REQUEST,
                        "organizationId is required for a platform administrator");
            }
            return organizationRepository.findById(requestedOrganizationId)
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Organization not found"));
        }
        UUID organizationId = requireOrganizationAdmin(actor);
        if (requestedOrganizationId != null && !requestedOrganizationId.equals(organizationId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Organization not found"));
    }

    private UUID requireOrganizationAdmin(AuthenticatedUser actor) {
        if (actor == null || actor.organizationId() == null
                || (actor.role() != UserRole.MANUFACTURER_ADMIN
                && actor.role() != UserRole.DISTRIBUTOR_ADMIN)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return actor.organizationId();
    }

    private static UserRole validateInvitedRole(UserRole role) {
        if (role != UserRole.MANUFACTURER_ADMIN
                && role != UserRole.MANUFACTURER_STAFF
                && role != UserRole.DISTRIBUTOR_ADMIN
                && role != UserRole.DISTRIBUTOR_STAFF) {
            throw new ApiException(ErrorCode.ROLE_NOT_ALLOWED,
                    "Only manufacturer or distributor organization roles can be invited");
        }
        return role;
    }

    private static boolean isOrganizationAdminRole(UserRole role) {
        return role == UserRole.MANUFACTURER_ADMIN || role == UserRole.DISTRIBUTOR_ADMIN;
    }

    private static boolean isPlatformAdmin(AuthenticatedUser actor) {
        return actor != null && actor.role() == UserRole.SUPER_ADMIN;
    }

    private static boolean organizationMatches(AuthenticatedUser actor, UUID organizationId) {
        return actor != null && actor.organizationId() != null && actor.organizationId().equals(organizationId)
                && (actor.role() == UserRole.MANUFACTURER_ADMIN || actor.role() == UserRole.DISTRIBUTOR_ADMIN);
    }

    private String buildLink(String suffix) {
        return frontendBaseUrl.replaceAll("/+$", "") + suffix;
    }

    private static String normalizeEmail(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

}
