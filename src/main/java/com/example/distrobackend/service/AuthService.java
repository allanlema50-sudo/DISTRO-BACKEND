package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.OtpPurpose;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Domain.enums.UserStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.Util.PhoneNormalizer;
import com.example.distrobackend.dto.*;
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

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Set<UserRole> SELF_REGISTRATION_ROLES =
            EnumSet.of(UserRole.CUSTOMER);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final OtpService otpService;
    private final InvitationService invitationService;

    private String dummyHash;

    @PostConstruct
    void init() {
        dummyHash =
                passwordEncoder.encode(
                        "not-a-real-password"
                );
    }

    // ================================================================
    // SELF REGISTRATION
    // ================================================================

    @Transactional
    public RegisterResponse register(
            RegisterRequest req
    ) {

        /*
         * Only CUSTOMER accounts self-register.
         *
         * Organization administrators and staff must be invited.
         */
        if (req.role() != UserRole.CUSTOMER) {

            throw new ApiException(
                    ErrorCode.ROLE_NOT_ALLOWED,
                    "Organization accounts must be created through an invitation"
            );
        }

        String phone =
                PhoneNormalizer.normalize(
                        req.phoneNumber()
                );

        String email =
                isBlank(req.email())
                        ? null
                        : req.email()
                                .trim()
                                .toLowerCase();

        if (userRepository.existsByPhoneNumber(phone)) {

            throw new ApiException(
                    ErrorCode.DUPLICATE_PHONE
            );
        }

        if (email != null
                && userRepository.existsByEmail(email)) {

            throw new ApiException(
                    ErrorCode.DUPLICATE_EMAIL
            );
        }

        User user =
                new User();

        user.setFullName(
                req.fullName().trim()
        );

        user.setEmail(email);

        user.setPhoneNumber(phone);

        user.setPasswordHash(
                passwordEncoder.encode(
                        req.password()
                )
        );

        user.setRole(
                UserRole.CUSTOMER
        );

        user.setStatus(
                UserStatus.ACTIVE
        );

        user.setOrganization(null);

        user.setEmailVerified(
                email == null
        );

        user.setPhoneVerified(false);

        userRepository.save(user);

        otpService.issue(
                user,
                OtpPurpose.ACCOUNT_VERIFICATION
        );

        return new RegisterResponse(
                user.getId(),
                "Account created. Enter the verification code sent to "
                        + mask(phone)
        );
    }

    // ================================================================
    // ACCEPT INVITATION
    // ================================================================

    @Transactional
    public AuthResponse acceptInvitation(
            String token,
            AcceptInvitationRequest request
    ) {

        User user =
                invitationService.acceptInvitation(
                        token,
                        request
                );

        /*
         * Organization users are activated through their
         * verified invitation email, so they do not need
         * the customer OTP flow.
         */
        return issueTokens(user);
    }

    // ================================================================
    // VERIFY CUSTOMER ACCOUNT
    // ================================================================

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse verifyAccount(
            VerifyOtpRequest req
    ) {

        User user =
                findByIdentifier(
                        req.identifier()
                )
                .orElseThrow(() ->
                        new ApiException(
                                ErrorCode.INVALID_OTP
                        )
                );

        otpService.verify(
                user,
                OtpPurpose.ACCOUNT_VERIFICATION,
                req.otp()
        );

        user.setPhoneVerified(true);

        userRepository.save(user);

        return issueTokens(user);
    }

    // ================================================================
    // RESEND CUSTOMER OTP
    // ================================================================

    @Transactional
    public MessageResponse resendVerification(
            IdentifierRequest req
    ) {

        findByIdentifier(req.identifier())
                .filter(this::requiresPhoneVerification)
                .filter(u -> !u.isPhoneVerified())
                .ifPresent(u ->
                        otpService.issue(
                                u,
                                OtpPurpose.ACCOUNT_VERIFICATION
                        )
                );

        return new MessageResponse(
                "If the account exists and requires phone verification, "
                        + "a new code has been sent"
        );
    }

    // ================================================================
    // LOGIN
    // ================================================================

    @Transactional
    public AuthResponse login(
            LoginRequest req
    ) {

        Optional<User> found =
                findByIdentifier(
                        req.identifier()
                );

        String hash =
                found
                        .map(User::getPasswordHash)
                        .orElse(dummyHash);

        boolean passwordOk =
                passwordEncoder.matches(
                        req.password(),
                        hash
                );

        if (found.isEmpty()
                || !passwordOk) {

            throw new ApiException(
                    ErrorCode.INVALID_CREDENTIALS
            );
        }

        User user =
                found.get();

        if (user.getStatus()
                == UserStatus.SUSPENDED) {

            throw new ApiException(
                    ErrorCode.ACCOUNT_SUSPENDED
            );
        }

        if (user.getStatus()
                == UserStatus.DEACTIVATED) {

            throw new ApiException(
                    ErrorCode.ACCOUNT_DEACTIVATED
            );
        }

        /*
         * Customers and drivers use phone OTP.
         *
         * Organization web users use invitation/email verification.
         */
        if (requiresPhoneVerification(user)
                && !user.isPhoneVerified()) {

            throw new ApiException(
                    ErrorCode.ACCOUNT_NOT_VERIFIED
            );
        }

        if (requiresEmailVerification(user)
                && !user.isEmailVerified()) {

            throw new ApiException(
                    ErrorCode.ACCOUNT_NOT_VERIFIED
            );
        }

        return issueTokens(user);
    }

    // ================================================================
    // REFRESH
    // ================================================================

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(
            RefreshTokenRequest req
    ) {

        User user =
                refreshTokenService.consume(
                        req.refreshToken()
                );

        if (user.getStatus()
                == UserStatus.SUSPENDED) {

            throw new ApiException(
                    ErrorCode.ACCOUNT_SUSPENDED
            );
        }

        if (user.getStatus()
                == UserStatus.DEACTIVATED) {

            throw new ApiException(
                    ErrorCode.ACCOUNT_DEACTIVATED
            );
        }

        return issueTokens(user);
    }

    // ================================================================
    // LOGOUT
    // ================================================================

    @Transactional
    public MessageResponse logout(
            RefreshTokenRequest req
    ) {

        refreshTokenService.revoke(
                req.refreshToken()
        );

        return new MessageResponse(
                "Logged out"
        );
    }

    // ================================================================
    // FORGOT PASSWORD
    // ================================================================

    @Transactional
    public MessageResponse forgotPassword(
            IdentifierRequest req
    ) {

        findByIdentifier(req.identifier())
                .filter(u ->
                        u.getStatus()
                                == UserStatus.ACTIVE
                )
                .ifPresent(u -> {

                    try {

                        otpService.issue(
                                u,
                                OtpPurpose.PASSWORD_RESET
                        );

                    } catch (ApiException e) {

                        if (e.getCode()
                                != ErrorCode.OTP_COOLDOWN) {

                            throw e;
                        }
                    }
                });

        return new MessageResponse(
                "If an account exists, a reset code has been sent"
        );
    }

    // ================================================================
    // RESET PASSWORD
    // ================================================================

    @Transactional(noRollbackFor = ApiException.class)
    public MessageResponse resetPassword(
            ResetPasswordRequest req
    ) {

        User user =
                findByIdentifier(
                        req.identifier()
                )
                .orElseThrow(() ->
                        new ApiException(
                                ErrorCode.INVALID_OTP
                        )
                );

        otpService.verify(
                user,
                OtpPurpose.PASSWORD_RESET,
                req.otp()
        );

        user.setPasswordHash(
                passwordEncoder.encode(
                        req.newPassword()
                )
        );

        userRepository.save(user);

        refreshTokenService.revokeAll(
                user.getId()
        );

        return new MessageResponse(
                "Password updated. Please log in with your new password"
        );
    }

    // ================================================================
    // PROFILE
    // ================================================================

    @Transactional(readOnly = true)
    public UserResponse getProfile(
            UUID userId
    ) {

        User user =
                userRepository.findById(userId)
                        .orElseThrow(() ->
                                new ApiException(
                                        ErrorCode.NOT_FOUND,
                                        "User not found"
                                )
                        );

        return UserResponse.from(user);
    }

    // ================================================================
    // TOKEN CREATION
    // ================================================================

    private AuthResponse issueTokens(
            User user
    ) {

        String accessToken =
                jwtService.generateAccessToken(
                        user
                );

        String refreshToken =
                refreshTokenService.create(
                        user
                );

        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtService.getAccessTtlSeconds(),
                user.getRole().webHomePath(),
                UserResponse.from(user)
        );
    }

    // ================================================================
    // VERIFICATION RULES
    // ================================================================

    private boolean requiresPhoneVerification(
            User user
    ) {

        return requiresPhoneVerification(
                user.getRole()
        );
    }

    private boolean requiresPhoneVerification(
            UserRole role
    ) {

        return role == UserRole.CUSTOMER
                || role == UserRole.DRIVER;
    }

    private boolean requiresEmailVerification(
            User user
    ) {

        return user.getRole()
                == UserRole.MANUFACTURER_ADMIN

                || user.getRole()
                == UserRole.MANUFACTURER_STAFF

                || user.getRole()
                == UserRole.DISTRIBUTOR_ADMIN

                || user.getRole()
                == UserRole.DISTRIBUTOR_STAFF;
    }

    // ================================================================
    // HELPERS
    // ================================================================

    private Optional<User> findByIdentifier(
            String identifier
    ) {

        String id =
                identifier.trim();

        if (id.contains("@")) {

            return userRepository.findByEmail(
                    id.toLowerCase()
            );
        }

        try {

            return userRepository.findByPhoneNumber(
                    PhoneNormalizer.normalize(id)
            );

        } catch (ApiException e) {

            return Optional.empty();
        }
    }

    private static boolean isBlank(
            String s
    ) {

        return s == null
                || s.isBlank();
    }

    private static String mask(
            String phone
    ) {

        return phone.substring(
                0,
                Math.min(4, phone.length())
        )
                + "****"
                + phone.substring(
                        phone.length() - 3
                );
    }
}
