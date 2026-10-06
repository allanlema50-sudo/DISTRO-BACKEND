package com.example.distrobackend.service;


import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.OtpPurpose;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Domain.enums.UserStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.Util.PhoneNormalizer;
import com.example.distrobackend.dto.*;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.JwtService;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * NOTE on @Transactional(noRollbackFor = ApiException.class): methods that verify an OTP or consume a
 * refresh token must not roll back when they throw, otherwise the failed-attempt counter / the
 * "revoke all sessions" safeguard would be undone together with the exception.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Set<UserRole> SELF_REGISTRATION_ROLES =
            EnumSet.of(UserRole.CUSTOMER);

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final OtpService otpService;

    // Compared against when the account does not exist, so response time does not reveal which accounts exist.
    private String dummyHash;

    @PostConstruct
    void init() {
        dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    // ------------------------------------------------------------------ register

    @Transactional
    public RegisterResponse register(RegisterRequest req) {
        UserRole role = req.role();
        if (!SELF_REGISTRATION_ROLES.contains(role)) {
            throw new ApiException(ErrorCode.ROLE_NOT_ALLOWED,
                    "This role cannot self-register. Ask your organization administrator for an invitation");
        }

        boolean createsOrganization = role != UserRole.CUSTOMER;
        if (createsOrganization) {
            if (isBlank(req.organizationName())) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Organization name is required");
            }
            if (isBlank(req.email())) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Email is required for organization accounts");
            }
        }

        String phone = PhoneNormalizer.normalize(req.phoneNumber());
        String email = isBlank(req.email()) ? null : req.email().trim().toLowerCase();

        if (userRepository.existsByPhoneNumber(phone)) {
            throw new ApiException(ErrorCode.DUPLICATE_PHONE);
        }
        if (email != null && userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.DUPLICATE_EMAIL);
        }

        Organization organization = null;
        if (createsOrganization) {
            organization = new Organization();
            organization.setName(req.organizationName().trim());
            organization.setType(role.organizationtype());
            organizationRepository.save(organization);
        }

        User user = new User();
        user.setFullName(req.fullName().trim());
        user.setEmail(email);
        user.setPhoneNumber(phone);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(role);
        user.setOrganization(organization);
        userRepository.save(user);

        otpService.issue(user, OtpPurpose.ACCOUNT_VERIFICATION);

        return new RegisterResponse(user.getId(),
                "Account created. Enter the verification code sent to " + mask(phone));
    }

    // ------------------------------------------------------------------ verify account

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse verifyAccount(VerifyOtpRequest req) {
        // Same error whether the account exists or not, so this endpoint cannot be used to probe accounts.
        User user = findByIdentifier(req.identifier())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_OTP));

        otpService.verify(user, OtpPurpose.ACCOUNT_VERIFICATION, req.otp());

        user.setPhoneVerified(true);
        userRepository.save(user);
        return issueTokens(user); // verified users are signed in straight away
    }

    @Transactional
    public MessageResponse resendVerification(IdentifierRequest req) {
        findByIdentifier(req.identifier())
                .filter(u -> !u.isPhoneVerified())
                .ifPresent(u -> otpService.issue(u, OtpPurpose.ACCOUNT_VERIFICATION));
        return new MessageResponse("If the account exists and is not yet verified, a new code has been sent");
    }

    // ------------------------------------------------------------------ login

    @Transactional
    public AuthResponse login(LoginRequest req) {
        Optional<User> found = findByIdentifier(req.identifier());

        String hash = found.map(User::getPasswordHash).orElse(dummyHash);
        boolean passwordOk = passwordEncoder.matches(req.password(), hash);
        if (found.isEmpty() || !passwordOk) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }

        User user = found.get();

        // Status is only revealed after the password is proven correct.
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED);
        }
        if (user.getStatus() == UserStatus.DEACTIVATED) {
            throw new ApiException(ErrorCode.ACCOUNT_DEACTIVATED);
        }
        if (!user.isPhoneVerified()) {
            throw new ApiException(ErrorCode.ACCOUNT_NOT_VERIFIED);
        }

        return issueTokens(user);
    }

    // ------------------------------------------------------------------ refresh / logout

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(RefreshTokenRequest req) {
        User user = refreshTokenService.consume(req.refreshToken());

        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED);
        }
        if (user.getStatus() == UserStatus.DEACTIVATED) {
            throw new ApiException(ErrorCode.ACCOUNT_DEACTIVATED);
        }
        return issueTokens(user);
    }

    @Transactional
    public MessageResponse logout(RefreshTokenRequest req) {
        refreshTokenService.revoke(req.refreshToken());
        return new MessageResponse("Logged out");
    }

    // ------------------------------------------------------------------ forgot / reset password

    @Transactional
    public MessageResponse forgotPassword(IdentifierRequest req) {
        findByIdentifier(req.identifier())
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .ifPresent(u -> {
                    try {
                        otpService.issue(u, OtpPurpose.PASSWORD_RESET);
                    } catch (ApiException e) {
                        // Swallow the cooldown error: surfacing it would reveal that the account exists.
                        if (e.getCode() != ErrorCode.OTP_COOLDOWN) throw e;
                    }
                });
        return new MessageResponse("If an account exists, a reset code has been sent");
    }

    @Transactional(noRollbackFor = ApiException.class)
    public MessageResponse resetPassword(ResetPasswordRequest req) {
        User user = findByIdentifier(req.identifier())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_OTP));

        otpService.verify(user, OtpPurpose.PASSWORD_RESET, req.otp());

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);

        // A password reset must end every existing session.
        refreshTokenService.revokeAll(user.getId());

        return new MessageResponse("Password updated. Please log in with your new password");
    }

    // ------------------------------------------------------------------ profile

    @Transactional(readOnly = true)
    public UserResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
        return UserResponse.from(user);
    }

    // ------------------------------------------------------------------ helpers

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.create(user);
        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtService.getAccessTtlSeconds(),
                user.getRole().webHomePath(),
                UserResponse.from(user));
    }

    /** identifier is an email (contains "@") or a phone number in any supported local/international format. */
    private Optional<User> findByIdentifier(String identifier) {
        String id = identifier.trim();
        if (id.contains("@")) {
            return userRepository.findByEmail(id.toLowerCase());
        }
        try {
            return userRepository.findByPhoneNumber(PhoneNormalizer.normalize(id));
        } catch (ApiException e) {
            return Optional.empty();
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String mask(String phone) {
        return phone.substring(0, Math.min(4, phone.length())) + "****" + phone.substring(phone.length() - 3);
    }
}
