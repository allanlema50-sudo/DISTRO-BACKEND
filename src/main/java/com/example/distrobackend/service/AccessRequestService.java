package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.AccessRequest;
import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.AccessRequestStatus;
import com.example.distrobackend.Domain.enums.NotificationPriority;
import com.example.distrobackend.Domain.enums.NotificationType;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.Util.PhoneNormalizer;
import com.example.distrobackend.dto.AccessRequestCreateRequest;
import com.example.distrobackend.dto.AccessRequestResponse;
import com.example.distrobackend.dto.ActivateAccountRequest;
import com.example.distrobackend.dto.MessageResponse;
import com.example.distrobackend.repository.AccessRequestRepository;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccessRequestService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long TOKEN_TTL_HOURS = 24;

    private final AccessRequestRepository requests;
    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final PasswordEncoder passwordEncoder;
    private final AccessEmailSender emailSender;
    private final NotificationService notificationService;

    @Value("${app.frontend.base-url:http://localhost:4200}")
    private String frontendBaseUrl;

    @Transactional
    public MessageResponse submit(AccessRequestCreateRequest req) {
        String personalEmail = normalizeEmail(req.personalEmail());
        String phone = PhoneNormalizer.normalize(req.phoneNumber());
        if (users.existsByEmail(personalEmail) || requests.existsByPersonalEmail(personalEmail)) {
            throw new ApiException(ErrorCode.CONFLICT, "An account or request already uses this personal email");
        }
        if (users.existsByPhoneNumber(phone) || requests.existsByPhoneNumber(phone)) {
            throw new ApiException(ErrorCode.CONFLICT, "An account or request already uses this phone number");
        }

        AccessRequest request = new AccessRequest();
        request.setFullName(req.fullName().trim());
        request.setPhoneNumber(phone);
        request.setPersonalEmail(personalEmail);
        request.setOrganizationEmail(normalizeEmail(req.organizationEmail()));
        request.setOrganizationName(req.organizationName().trim());
        request.setOrganizationType(req.organizationType());
        AccessRequest saved = requests.save(request);

        String body = saved.getFullName() + " requested " + saved.getOrganizationType()
                + " access for " + saved.getOrganizationName() + ". Review request " + saved.getId() + ".";
        emailSender.notifySuperAdmins("New LogiFlow access request", body);
        notifyPlatformAdmins(saved, body);
        return new MessageResponse("Request submitted. A super administrator will review it.");
    }

    @Transactional(readOnly = true)
    public List<AccessRequestResponse> list() {
        return requests.findAllByOrderByCreatedAtDesc().stream()
                .map(AccessRequestResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccessRequestResponse getById(UUID id) {
        return requests.findById(id)
                .map(AccessRequestResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Access request not found"));
    }

    @Transactional
    public AccessRequestResponse approve(UUID id, User reviewer) {
        AccessRequest request = requests.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Access request not found"));
        if (request.getStatus() == AccessRequestStatus.APPROVED) {
            getOrCreateOrganization(request);
            return AccessRequestResponse.from(request);
        }
        if (request.getStatus() != AccessRequestStatus.PENDING) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Only pending requests can be approved");
        }
        if (users.existsByEmail(request.getPersonalEmail()) || users.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new ApiException(ErrorCode.CONFLICT, "The requested email or phone is already attached to an account");
        }

        getOrCreateOrganization(request);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes());
        request.setActivationTokenHash(hash(token));
        request.setActivationExpiresAt(OffsetDateTime.now().plusHours(TOKEN_TTL_HOURS));
        request.setStatus(AccessRequestStatus.APPROVED);
        request.setReviewedAt(OffsetDateTime.now());
        request.setReviewedBy(reviewer);

        String url = frontendBaseUrl.replaceAll("/$", "") + "/auth/activate?email="
                + java.net.URLEncoder.encode(request.getPersonalEmail(), StandardCharsets.UTF_8)
                + "&token=" + java.net.URLEncoder.encode(token, StandardCharsets.UTF_8);
        emailSender.sendActivation(request.getPersonalEmail(), url);
        return AccessRequestResponse.from(request);
    }

    @Transactional
    public AccessRequestResponse reject(UUID id, User reviewer) {
        AccessRequest request = requests.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Access request not found"));
        if (request.getStatus() == AccessRequestStatus.REJECTED) {
            return AccessRequestResponse.from(request);
        }
        if (request.getStatus() != AccessRequestStatus.PENDING) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Only pending requests can be rejected");
        }
        request.setStatus(AccessRequestStatus.REJECTED);
        request.setReviewedAt(OffsetDateTime.now());
        request.setReviewedBy(reviewer);
        request.setActivationTokenHash(null);
        request.setActivationExpiresAt(null);
        return AccessRequestResponse.from(request);
    }

    @Transactional
    public MessageResponse activate(ActivateAccountRequest req) {
        if (!req.password().equals(req.confirmPassword())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Password and confirmation do not match");
        }
        AccessRequest request = requests.findByPersonalEmailAndStatusForUpdate(
                        normalizeEmail(req.email()), AccessRequestStatus.APPROVED)
                .filter(r -> r.getActivationExpiresAt() != null
                        && r.getActivationExpiresAt().isAfter(OffsetDateTime.now()))
                .filter(r -> constantTimeEquals(r.getActivationTokenHash(), hash(req.token())))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TOKEN,
                        "Activation link is invalid, expired, or already used"));
        if (users.existsByEmail(request.getPersonalEmail()) || users.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new ApiException(ErrorCode.CONFLICT, "The requested email or phone is already attached to an account");
        }

        Organization organization = getOrCreateOrganization(request);
        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(request.getPersonalEmail());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(request.getOrganizationType() == Organizationtype.MANUFACTURER
                ? UserRole.MANUFACTURER_ADMIN : UserRole.DISTRIBUTOR_ADMIN);
        user.setOrganization(organization);
        user.setPhoneVerified(true);
        user.setEmailVerified(true);
        users.save(user);

        request.setStatus(AccessRequestStatus.ACTIVATED);
        request.setActivationTokenHash(null);
        request.setActivationExpiresAt(null);
        request.setActivatedAt(OffsetDateTime.now());
        return new MessageResponse("Account activated. You can now sign in.");
    }

    private void notifyPlatformAdmins(AccessRequest request, String body) {
        List<User> admins = users.findByRole(UserRole.PLATFORM_ADMIN);
        admins.addAll(users.findByRole(UserRole.SUPER_ADMIN));
        for (User admin : admins) {
            notificationService.create(admin, "New Organization Access Request", body,
                    NotificationType.ACCESS_REQUEST, NotificationPriority.HIGH,
                    request.getId(), "ACCESS_REQUEST");
        }
    }

    private Organization getOrCreateOrganization(AccessRequest request) {
        return organizations.findByAccessRequest_Id(request.getId()).orElseGet(() -> {
            Organization organization = new Organization();
            organization.setName(request.getOrganizationName());
            organization.setType(request.getOrganizationType());
            organization.setEmail(request.getOrganizationEmail());
            organization.setAccessRequest(request);
            return organizations.save(organization);
        });
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static byte[] randomBytes() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return a != null && MessageDigest.isEqual(
                a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }
}
