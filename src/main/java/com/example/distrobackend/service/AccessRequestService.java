package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.AccessRequest;
import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.AccessRequestStatus;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.Util.PhoneNormalizer;
import com.example.distrobackend.dto.*;
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
    @Value("${app.frontend.base-url:http://localhost:4200}") private String frontendBaseUrl;

    @Transactional
    public MessageResponse submit(AccessRequestCreateRequest req) {
        String personalEmail = normalizeEmail(req.personalEmail());
        String phone = PhoneNormalizer.normalize(req.phoneNumber());
        if (users.existsByEmail(personalEmail) || requests.existsByPersonalEmailAndStatus(personalEmail, AccessRequestStatus.PENDING))
            throw new ApiException(ErrorCode.CONFLICT, "An account or pending request already uses this personal email");
        if (users.existsByPhoneNumber(phone) || requests.existsByPhoneNumber(phone))
            throw new ApiException(ErrorCode.CONFLICT, "An account or request already uses this phone number");
        AccessRequest request = new AccessRequest();
        request.setFullName(req.fullName().trim()); request.setPhoneNumber(phone);
        request.setPersonalEmail(personalEmail); request.setOrganizationEmail(normalizeEmail(req.organizationEmail()));
        request.setOrganizationName(req.organizationName().trim()); request.setOrganizationType(req.organizationType());
        requests.save(request);
        emailSender.notifySuperAdmins("New LogiFlow access request", request.getFullName() + " requested " +
                request.getOrganizationType() + " access for " + request.getOrganizationName() +
                ". Review request " + request.getId() + ".");
        return new MessageResponse("Request submitted. A super administrator will review it.");
    }

    @Transactional(readOnly = true)
    public List<AccessRequestResponse> list() {
        return requests.findAllByOrderByCreatedAtDesc().stream().map(AccessRequestResponse::from).toList();
    }

    @Transactional
    public MessageResponse approve(java.util.UUID id) {
        AccessRequest request = requests.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Access request not found"));
        if (request.getStatus() == AccessRequestStatus.APPROVED) {
            return new MessageResponse("Request was already approved; no additional activation link was sent");
        }
        if (request.getStatus() != AccessRequestStatus.PENDING) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Only pending requests can be approved");
        }
        if (users.existsByEmail(request.getPersonalEmail()) || users.existsByPhoneNumber(request.getPhoneNumber()))
            throw new ApiException(ErrorCode.CONFLICT, "The requested email or phone is already attached to an account");
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes());
        request.setActivationTokenHash(hash(token));
        request.setActivationExpiresAt(OffsetDateTime.now().plusHours(TOKEN_TTL_HOURS));
        request.setStatus(AccessRequestStatus.APPROVED);
        String url = frontendBaseUrl.replaceAll("/$", "") + "/auth/activate?email=" +
                java.net.URLEncoder.encode(request.getPersonalEmail(), StandardCharsets.UTF_8) + "&token=" +
                java.net.URLEncoder.encode(token, StandardCharsets.UTF_8);
        emailSender.sendActivation(request.getPersonalEmail(), url);
        return new MessageResponse("Request approved and activation link sent");
    }

    @Transactional
    public MessageResponse reject(java.util.UUID id) {
        AccessRequest request = requests.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Access request not found"));
        if (request.getStatus() == AccessRequestStatus.REJECTED) {
            return new MessageResponse("Request was already rejected");
        }
        if (request.getStatus() != AccessRequestStatus.PENDING) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Only pending requests can be rejected");
        }
        request.setStatus(AccessRequestStatus.REJECTED);
        request.setActivationTokenHash(null); request.setActivationExpiresAt(null);
        return new MessageResponse("Request rejected");
    }

    @Transactional
    public MessageResponse activate(ActivateAccountRequest req) {
        if (!req.password().equals(req.confirmPassword()))
            throw new ApiException(ErrorCode.BAD_REQUEST, "Password and confirmation do not match");
        AccessRequest request = requests.findByPersonalEmailAndStatusForUpdate(
                        normalizeEmail(req.email()), AccessRequestStatus.APPROVED)
                .filter(r -> r.getActivationExpiresAt() != null && r.getActivationExpiresAt().isAfter(OffsetDateTime.now()))
                .filter(r -> constantTimeEquals(r.getActivationTokenHash(), hash(req.token())))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TOKEN,
                        "Activation link is invalid, expired, or already used"));
        if (users.existsByEmail(request.getPersonalEmail()) || users.existsByPhoneNumber(request.getPhoneNumber()))
            throw new ApiException(ErrorCode.CONFLICT, "The requested email or phone is already attached to an account");

        Organization organization = new Organization();
        organization.setName(request.getOrganizationName()); organization.setType(request.getOrganizationType());
        organization.setEmail(request.getOrganizationEmail());
        organization.setAccessRequest(request);
        organizations.save(organization);
        User user = new User();
        user.setFullName(request.getFullName()); user.setEmail(request.getPersonalEmail());
        user.setPhoneNumber(request.getPhoneNumber()); user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(request.getOrganizationType() == com.example.distrobackend.Domain.enums.Organizationtype.MANUFACTURER
                ? UserRole.MANUFACTURER_ADMIN : UserRole.DISTRIBUTOR_ADMIN);
        user.setOrganization(organization); user.setPhoneVerified(true); user.setEmailVerified(true);
        users.save(user);
        request.setStatus(AccessRequestStatus.ACTIVATED); request.setActivationTokenHash(null); request.setActivationExpiresAt(null);
        return new MessageResponse("Account activated. You can now sign in.");
    }

    private static String normalizeEmail(String email) { return email.trim().toLowerCase(java.util.Locale.ROOT); }
    private static byte[] randomBytes() { byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return bytes; }
    private static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static boolean constantTimeEquals(String a, String b) {
        return a != null && MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }
}
