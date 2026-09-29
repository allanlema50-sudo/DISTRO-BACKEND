package com.example.distrobackend.service;



import com.example.distrobackend.Domain.entity.OtpVerification;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.OtpPurpose;
import com.example.distrobackend.Domain.enums.OtpStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.repository.OtpVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpVerificationRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final OtpSender otpSender;

    @Value("${app.otp.ttl-minutes:5}")
    private long ttlMinutes;

    @Value("${app.otp.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.otp.resend-cooldown-seconds:60}")
    private long cooldownSeconds;

    /**
     * Generates a 6-digit code, stores only its hash, invalidates older pending codes, and sends it.
     * noRollbackFor: the cooldown check throws before any write, and forgotPassword deliberately catches it;
     * without this the caught exception would still mark the outer transaction rollback-only.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void issue(User user, OtpPurpose purpose) {
        OffsetDateTime now = OffsetDateTime.now();

        repository.findFirstByUser_IdAndPurposeOrderByCreatedAtDesc(user.getId(), purpose)
                .filter(last -> last.getCreatedAt().isAfter(now.minusSeconds(cooldownSeconds)))
                .ifPresent(last -> { throw new ApiException(ErrorCode.OTP_COOLDOWN); });

        repository.findByUser_IdAndPurposeAndStatus(user.getId(), purpose, OtpStatus.PENDING)
                .forEach(old -> old.setStatus(OtpStatus.EXPIRED));

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));

        OtpVerification otp = new OtpVerification();
        otp.setUser(user);
        otp.setDestination(user.getPhoneNumber());
        otp.setCodeHash(passwordEncoder.encode(code));
        otp.setPurpose(purpose);
        otp.setStatus(OtpStatus.PENDING);
        otp.setMaxAttempts((short) maxAttempts);
        otp.setExpiresAt(now.plusMinutes(ttlMinutes));
        repository.save(otp);

        otpSender.send(user.getPhoneNumber(), purpose, code, ttlMinutes);
    }

    /**
     * Verifies and consumes the latest pending code. Wrong-attempt counters must survive the
     * exception, hence noRollbackFor here AND on every caller (the outermost transaction decides).
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void verify(User user, OtpPurpose purpose, String code) {
        OffsetDateTime now = OffsetDateTime.now();

        OtpVerification otp = repository
                .findFirstByUser_IdAndPurposeOrderByCreatedAtDesc(user.getId(), purpose)
                .filter(o -> o.getStatus() == OtpStatus.PENDING)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_OTP));

        if (otp.getExpiresAt().isBefore(now)) {
            otp.setStatus(OtpStatus.EXPIRED);
            throw new ApiException(ErrorCode.OTP_EXPIRED);
        }

        if (otp.getAttemptCount() >= otp.getMaxAttempts()) {
            otp.setStatus(OtpStatus.FAILED);
            throw new ApiException(ErrorCode.OTP_ATTEMPTS_EXCEEDED);
        }

        if (!passwordEncoder.matches(code, otp.getCodeHash())) {
            otp.setAttemptCount((short) (otp.getAttemptCount() + 1));
            if (otp.getAttemptCount() >= otp.getMaxAttempts()) {
                otp.setStatus(OtpStatus.FAILED);
                throw new ApiException(ErrorCode.OTP_ATTEMPTS_EXCEEDED);
            }
            throw new ApiException(ErrorCode.INVALID_OTP);
        }

        otp.setStatus(OtpStatus.VERIFIED);
        otp.setVerifiedAt(now);
    }
}
