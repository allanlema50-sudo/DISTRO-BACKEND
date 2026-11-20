package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.OtpVerification;
import com.example.distrobackend.Domain.entity.TripStop;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.OtpPurpose;
import com.example.distrobackend.Domain.enums.OtpStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.repository.OtpVerificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryOtpServiceTest {
    @Mock private OtpVerificationRepository repository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private OtpSender otpSender;

    private OtpService service;

    @BeforeEach
    void setUp() {
        service = new OtpService(repository, passwordEncoder, otpSender);
        ReflectionTestUtils.setField(service, "ttlMinutes", 10L);
        ReflectionTestUtils.setField(service, "maxAttempts", 3);
        ReflectionTestUtils.setField(service, "cooldownSeconds", 60L);
    }

    @Test
    void issuedCodeIsBoundToStopAndStoredHashed() {
        User recipient = new User();
        recipient.setId(UUID.randomUUID());
        recipient.setEmail("recipient@example.com");
        recipient.setPhoneNumber("254700000000");
        TripStop stop = new TripStop();
        stop.setId(UUID.randomUUID());
        when(repository.save(any(OtpVerification.class))).thenAnswer(call -> call.getArgument(0));
        when(passwordEncoder.encode(any())).thenReturn("hashed-code");

        service.issueDelivery(recipient, stop);

        var captor = org.mockito.ArgumentCaptor.forClass(OtpVerification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDeliveryStop()).isSameAs(stop);
        assertThat(captor.getValue().getCodeHash()).isEqualTo("hashed-code");
        assertThat(captor.getValue().getPurpose()).isEqualTo(OtpPurpose.DELIVERY_CONFIRMATION);
        verify(otpSender).send(eq(recipient), eq(OtpPurpose.DELIVERY_CONFIRMATION), anyString(), eq(10L));
    }

    @Test
    void invalidCodeIncrementsAttemptsOnOnlyThatStopsCode() {
        TripStop stop = new TripStop();
        stop.setId(UUID.randomUUID());
        OtpVerification otp = pendingOtp();
        when(repository.findFirstByDeliveryStop_IdOrderByCreatedAtDesc(stop.getId()))
                .thenReturn(Optional.of(otp));
        when(passwordEncoder.matches("000000", "hashed-code")).thenReturn(false);

        assertThatThrownBy(() -> service.verifyDelivery(stop, "000000"))
                .isInstanceOf(ApiException.class);
        assertThat(otp.getAttemptCount()).isEqualTo((short) 1);
        assertThat(otp.getStatus()).isEqualTo(OtpStatus.PENDING);
        verify(repository).findFirstByDeliveryStop_IdOrderByCreatedAtDesc(stop.getId());
    }

    @Test
    void expiredCodeCannotBeUsed() {
        TripStop stop = new TripStop();
        stop.setId(UUID.randomUUID());
        OtpVerification otp = pendingOtp();
        otp.setExpiresAt(OffsetDateTime.now().minusSeconds(1));
        when(repository.findFirstByDeliveryStop_IdOrderByCreatedAtDesc(stop.getId()))
                .thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> service.verifyDelivery(stop, "123456"))
                .isInstanceOf(ApiException.class);
        assertThat(otp.getStatus()).isEqualTo(OtpStatus.EXPIRED);
        verifyNoInteractions(passwordEncoder);
    }

    private OtpVerification pendingOtp() {
        OtpVerification otp = new OtpVerification();
        otp.setCodeHash("hashed-code");
        otp.setStatus(OtpStatus.PENDING);
        otp.setAttemptCount((short) 0);
        otp.setMaxAttempts((short) 3);
        otp.setExpiresAt(OffsetDateTime.now().plusMinutes(5));
        return otp;
    }
}
