package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.UserStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.dto.VerifyOtpRequest;
import com.example.distrobackend.dto.LoginRequest;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private OtpService otpService;

    @Test
    void suspendedAccountCannotReceiveTokensDuringVerification() {
        AuthService authService = new AuthService(
                userRepository, passwordEncoder, jwtService, refreshTokenService, otpService);
        User user = new User();
        user.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findByEmail("suspended@example.test")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.verifyAccount(
                new VerifyOtpRequest("suspended@example.test", "123456")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("suspended");

        verify(otpService).verify(user,
                com.example.distrobackend.Domain.enums.OtpPurpose.ACCOUNT_VERIFICATION, "123456");
        verify(userRepository, never()).save(any());
        verify(jwtService, never()).generateAccessToken(any());
        verify(refreshTokenService, never()).create(any());
    }

    @Test
    void organizationUserUsesEmailVerificationInsteadOfPhoneOtpAtLogin() {
        AuthService authService = new AuthService(
                userRepository, passwordEncoder, jwtService, refreshTokenService, otpService);
        User user = new User();
        user.setRole(com.example.distrobackend.Domain.enums.UserRole.MANUFACTURER_ADMIN);
        user.setEmail("admin@example.test");
        user.setPhoneVerified(false);
        user.setEmailVerified(true);
        user.setPasswordHash("encoded-password");
        when(userRepository.findByEmail("admin@example.test")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("StrongPassword1!", "encoded-password")).thenReturn(true);
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(refreshTokenService.create(user)).thenReturn("refresh-token");
        when(jwtService.getAccessTtlSeconds()).thenReturn(900L);

        assertThat(authService.login(new LoginRequest("admin@example.test", "StrongPassword1!"))
                .accessToken()).isEqualTo("access-token");
        verify(otpService, never()).issue(any(), any());
    }
}
