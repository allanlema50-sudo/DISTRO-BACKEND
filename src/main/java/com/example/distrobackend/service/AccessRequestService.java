package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.AccessRequest;
import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.AccessRequestStatus;
import com.example.distrobackend.Domain.enums.NotificationPriority;
import com.example.distrobackend.Domain.enums.NotificationType;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.Exception.OnboardingExpiredException;
import com.example.distrobackend.dto.AccessRequestApprovalResponse;
import com.example.distrobackend.dto.AccessRequestCreateRequest;
import com.example.distrobackend.dto.AccessRequestResponse;
import com.example.distrobackend.dto.ActivateAccountRequest;
import com.example.distrobackend.dto.RegisterResponse;
import com.example.distrobackend.event.OnboardingEmailRequested;
import com.example.distrobackend.repository.AccessRequestRepository;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.Util.SecretTokenService;
import com.example.distrobackend.Util.PhoneNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccessRequestService {
    private static final EnumSet<AccessRequestStatus> ACTIVE_DUPLICATE_STATUSES =
            EnumSet.of(AccessRequestStatus.PENDING, AccessRequestStatus.APPROVED);

    private final AccessRequestRepository accessRequestRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    // Deferred onboarding option: reintroduce an OtpService dependency here only
    // if phone verification is deliberately enabled for organization accounts.
    // private final OtpService otpService;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationService notificationService;

    @Value("${app.frontend.base-url:http://localhost:4200}")
    private String frontendBaseUrl;

    @Value("${app.onboarding.activation-ttl:24h}")
    private Duration activationTtl;

    @Transactional
    public AccessRequestResponse submit(AccessRequestCreateRequest request) {
        String email = normalizeEmail(request.personalEmail());
        String phone = PhoneNormalizer.normalize(request.phoneNumber());

        if (userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByPhoneNumber(phone)) {
            throw new ApiException(ErrorCode.DUPLICATE_PHONE);
        }
        if (ACTIVE_DUPLICATE_STATUSES.stream()
                .anyMatch(status -> accessRequestRepository.existsByPersonalEmailAndStatus(email, status))) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "An access request for this email is already awaiting review or activation");
        }
        if (accessRequestRepository.existsByPhoneNumberAndStatusIn(phone, ACTIVE_DUPLICATE_STATUSES)) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "An access request for this phone number is already awaiting review or activation");
        }

        AccessRequest accessRequest = new AccessRequest();
        accessRequest.setFullName(request.fullName().trim());
        accessRequest.setPersonalEmail(email);
        accessRequest.setOrganizationEmail(normalizeEmail(request.organizationEmail()));
        accessRequest.setPhoneNumber(phone);
        accessRequest.setOrganizationName(request.organizationName().trim());
        accessRequest.setOrganizationType(request.organizationType());
        accessRequest.setStatus(AccessRequestStatus.PENDING);
        List<User> superAdmins = userRepository.findByRole(UserRole.SUPER_ADMIN);
        if (superAdmins.isEmpty()) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "No SUPER_ADMIN account is configured to receive access requests");
        }

        AccessRequest saved = accessRequestRepository.save(accessRequest);
        String notificationMessage = "New organization access request from " + saved.getFullName()
                + " for " + saved.getOrganizationName() + " (" + saved.getOrganizationType()
                + "). Review request " + saved.getId() + ".";
        for (User superAdmin : superAdmins) {
            notificationService.create(
                    superAdmin,
                    "New organization access request",
                    notificationMessage,
                    NotificationType.ACCESS_REQUEST,
                    NotificationPriority.HIGH,
                    saved.getId(),
                    "ACCESS_REQUEST");
            if (superAdmin.getEmail() != null && !superAdmin.getEmail().isBlank()) {
                eventPublisher.publishEvent(new OnboardingEmailRequested(
                        superAdmin.getEmail(),
                        "New LogiFlow organization access request",
                        notificationMessage));
            }
        }
        return AccessRequestResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public AccessRequestResponse getById(UUID requestId) {
        return accessRequestRepository.findById(requestId)
                .map(AccessRequestResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCESS_REQUEST_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<AccessRequestResponse> list(AccessRequestStatus status) {
        List<AccessRequest> requests = status == null
                ? accessRequestRepository.findAllByOrderByCreatedAtDesc()
                : accessRequestRepository.findByStatusOrderByCreatedAtDesc(status);
        return requests.stream().map(AccessRequestResponse::from).toList();
    }

    @Transactional
    public AccessRequestApprovalResponse approve(UUID requestId, AuthenticatedUser reviewer) {
        requireSuperAdmin(reviewer);
        AccessRequest request = accessRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCESS_REQUEST_NOT_FOUND));
        if (request.getStatus() == AccessRequestStatus.APPROVED) {
            return new AccessRequestApprovalResponse(AccessRequestResponse.from(request), null);
        }
        if (request.getStatus() != AccessRequestStatus.PENDING) {
            throw new ApiException(ErrorCode.ACCESS_REQUEST_STATE_CONFLICT,
                    "Only pending access requests can be approved");
        }
        if (userRepository.existsByEmail(request.getPersonalEmail())
                || userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "The applicant already has an account. Reject the request and resolve the duplicate first");
        }
        organizationRepository.findByEmailIgnoreCase(request.getOrganizationEmail())
                .ifPresent(existing -> {
                    throw new ApiException(ErrorCode.CONFLICT,
                            "An organization with this email already exists");
                });

        Organization organization = new Organization();
        organization.setName(request.getOrganizationName().trim());
        organization.setEmail(request.getOrganizationEmail());
        organization.setType(request.getOrganizationType());
        organizationRepository.save(organization);
        request.setOrganization(organization);

        String rawToken = SecretTokenService.generate();
        request.setActivationTokenHash(SecretTokenService.sha256(rawToken));
        request.setActivationExpiresAt(OffsetDateTime.now().plus(activationTtl));
        request.setReviewedAt(OffsetDateTime.now());
        request.setReviewedBy(userRepository.getReferenceById(reviewer.userId()));
        request.setStatus(AccessRequestStatus.APPROVED);
        AccessRequest saved = accessRequestRepository.save(request);

        String link = buildLink("/auth/activate?email="
                + URLEncoder.encode(saved.getPersonalEmail(), StandardCharsets.UTF_8)
                + "&token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8));
        eventPublisher.publishEvent(new OnboardingEmailRequested(
                saved.getPersonalEmail(),
                "Complete your LogiFlow organization account setup",
                "Your organization access request was approved. Complete setup here:\n\n" + link
        ));
        // The raw token is delivered only through the applicant's email.
        // Returning it to an administrator would allow activation without
        // proving control of the applicant's email address.
        return new AccessRequestApprovalResponse(AccessRequestResponse.from(saved), null);
    }

    @Transactional
    public AccessRequestResponse reject(UUID requestId, AuthenticatedUser reviewer) {
        requireSuperAdmin(reviewer);
        AccessRequest request = accessRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCESS_REQUEST_NOT_FOUND));
        if (request.getStatus() == AccessRequestStatus.REJECTED) {
            return AccessRequestResponse.from(request);
        }
        if (request.getStatus() != AccessRequestStatus.PENDING) {
            throw new ApiException(ErrorCode.ACCESS_REQUEST_STATE_CONFLICT,
                    "Only pending access requests can be rejected");
        }
        request.setReviewedAt(OffsetDateTime.now());
        request.setReviewedBy(userRepository.getReferenceById(reviewer.userId()));
        request.setStatus(AccessRequestStatus.REJECTED);
        return AccessRequestResponse.from(accessRequestRepository.save(request));
    }

    @Transactional(noRollbackFor = OnboardingExpiredException.class)
    public RegisterResponse activate(ActivateAccountRequest request) {
        AccessRequest accessRequest = accessRequestRepository
                .findByPersonalEmailAndStatusForUpdate(normalizeEmail(request.email()), AccessRequestStatus.APPROVED)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_ACTIVATION_TOKEN));

        if (accessRequest.getActivationTokenHash() == null
                || !SecretTokenService.matches(request.token(), accessRequest.getActivationTokenHash())
                || accessRequest.getActivationExpiresAt() == null
                || !accessRequest.getActivationExpiresAt().isAfter(OffsetDateTime.now())) {
            if (accessRequest.getActivationExpiresAt() != null
                    && !accessRequest.getActivationExpiresAt().isAfter(OffsetDateTime.now())) {
                accessRequest.setStatus(AccessRequestStatus.EXPIRED);
            }
            throw new OnboardingExpiredException(ErrorCode.INVALID_ACTIVATION_TOKEN);
        }
        if (!request.password().equals(request.confirmPassword())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Password and confirmation do not match");
        }
        if (userRepository.existsByEmail(accessRequest.getPersonalEmail())
                || userRepository.existsByPhoneNumber(accessRequest.getPhoneNumber())) {
            throw new ApiException(ErrorCode.CONFLICT, "An account already exists for this request");
        }

        User user = new User();
        user.setFullName(accessRequest.getFullName());
        user.setEmail(accessRequest.getPersonalEmail());
        user.setPhoneNumber(accessRequest.getPhoneNumber());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(accessRequest.getOrganizationType() == com.example.distrobackend.Domain.enums.Organizationtype.MANUFACTURER
                ? UserRole.MANUFACTURER_ADMIN : UserRole.DISTRIBUTOR_ADMIN);
        Organization organization = accessRequest.getOrganization();
        if (organization == null) {
            throw new ApiException(ErrorCode.CONFLICT, "Approved organization is unavailable");
        }
        user.setOrganization(organization);
        user.setPhoneVerified(false);
        // The emailed activation link is the email-verification step for organization accounts.
        user.setEmailVerified(true);
        userRepository.save(user);

        accessRequest.setStatus(AccessRequestStatus.ACTIVATED);
        accessRequest.setActivatedAt(OffsetDateTime.now());
        accessRequest.setActivationTokenHash(null);
        accessRequest.setActivationExpiresAt(null);
        accessRequestRepository.save(accessRequest);
        // Deferred onboarding option; intentionally inactive for the current
        // email-link verification flow:
        // otpService.issue(user, OtpPurpose.ACCOUNT_VERIFICATION);
        return new RegisterResponse(user.getId(),
                "Account activated. You can now sign in.");
    }

    private String buildLink(String suffix) {
        return frontendBaseUrl.replaceAll("/+$", "") + suffix;
    }

    private static String normalizeEmail(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static void requireSuperAdmin(AuthenticatedUser reviewer) {
        if (reviewer == null || reviewer.role() != UserRole.SUPER_ADMIN
                || reviewer.organizationId() != null) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }

}
