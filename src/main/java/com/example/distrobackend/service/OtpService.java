package com.example.distrobackend.service;



import com.example.distrobackend.Domain.entity.OtpVerification;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.entity.TripStop;
import com.example.distrobackend.Domain.enums.OtpPurpose;
import com.example.distrobackend.Domain.enums.OtpStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.DeliveryOtpResponse;
import com.example.distrobackend.repository.OtpVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    @Value("${otp.provider:http}")
    private String otpProvider;

    /** Delivery codes outlive a login OTP so a presentation can reach the handover. */
    @Value("${app.otp.delivery-ttl-minutes:30}")
    private long deliveryTtlMinutes;

    /**
     * When set, a missing SMS provider does not roll back dispatch, and the
     * customer app can show the same code the driver must type.
     */
    @Value("${app.demo.customer-fulfillment.enabled:false}")
    private boolean demoCustomerFulfillment;

    private final ConcurrentHashMap<UUID, RememberedDeliveryCode> deliveryCodes = new ConcurrentHashMap<>();

    private record RememberedDeliveryCode(String code, OffsetDateTime expiresAt) {}

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
        boolean emailDelivery = "resend".equalsIgnoreCase(otpProvider)
                || "mailgun".equalsIgnoreCase(otpProvider)
                || "brevo".equalsIgnoreCase(otpProvider);
        otp.setDestination(emailDelivery ? user.getEmail() : user.getPhoneNumber());
        otp.setCodeHash(passwordEncoder.encode(code));
        otp.setPurpose(purpose);
        otp.setStatus(OtpStatus.PENDING);
        otp.setMaxAttempts((short) maxAttempts);
        otp.setExpiresAt(now.plusMinutes(ttlMinutes));
        repository.save(otp);

        try {
            otpSender.send(user, purpose, code, ttlMinutes);
        } catch (ApiException ex) {
            // ApiException is generally excluded from rollback here so cooldown and failed-attempt
            // state can be returned to the caller. Delivery failures are different: a pending OTP
            // must not survive when the provider did not accept it.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            throw ex;
        }
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

    @Transactional(noRollbackFor = ApiException.class)
    public DeliveryOtpResponse issueDelivery(User recipient, TripStop stop) {
        OffsetDateTime now = OffsetDateTime.now();
        long minutes = deliveryTtlMinutes > 0 ? deliveryTtlMinutes : ttlMinutes;
        RememberedDeliveryCode remembered = deliveryCodes.get(stop.getId());
        boolean coolingDown = repository.findFirstByDeliveryStop_IdOrderByCreatedAtDesc(stop.getId())
                .filter(last -> last.getStatus() == OtpStatus.PENDING)
                .filter(last -> last.getExpiresAt() == null || last.getExpiresAt().isAfter(now))
                .filter(last -> last.getCreatedAt().isAfter(now.minusSeconds(cooldownSeconds)))
                .isPresent();
        if (coolingDown && remembered != null && remembered.expiresAt().isAfter(now)) {
            return new DeliveryOtpResponse(remembered.code(), remainingMinutes(remembered.expiresAt(), now));
        }
        if (coolingDown) {
            throw new ApiException(ErrorCode.OTP_COOLDOWN);
        }
        repository.findByDeliveryStop_IdAndStatus(stop.getId(), OtpStatus.PENDING)
                .forEach(old -> old.setStatus(OtpStatus.EXPIRED));
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        OtpVerification otp = new OtpVerification();
        otp.setUser(recipient);
        otp.setDeliveryStop(stop);
        boolean emailDelivery = "resend".equalsIgnoreCase(otpProvider)
                || "mailgun".equalsIgnoreCase(otpProvider)
                || "brevo".equalsIgnoreCase(otpProvider);
        otp.setDestination(emailDelivery ? recipient.getEmail() : recipient.getPhoneNumber());
        otp.setCodeHash(passwordEncoder.encode(code));
        otp.setPurpose(OtpPurpose.DELIVERY_CONFIRMATION);
        otp.setStatus(OtpStatus.PENDING);
        otp.setMaxAttempts((short) maxAttempts);
        otp.setExpiresAt(now.plusMinutes(minutes));
        repository.save(otp);
        try {
            otpSender.send(recipient, OtpPurpose.DELIVERY_CONFIRMATION, code, minutes);
        } catch (ApiException ex) {
            if (!demoCustomerFulfillment) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                throw ex;
            }
            // The customer app shows the code. A missing SMS provider must not cancel dispatch.
        }
        deliveryCodes.put(stop.getId(), new RememberedDeliveryCode(code, now.plusMinutes(minutes)));
        return new DeliveryOtpResponse(code, minutes);
    }

    /** The code issued for this stop during the current server run, if it has not expired. */
    public DeliveryOtpResponse currentDeliveryCode(UUID stopId) {
        RememberedDeliveryCode remembered = deliveryCodes.get(stopId);
        OffsetDateTime now = OffsetDateTime.now();
        if (remembered == null || !remembered.expiresAt().isAfter(now)) {
            throw new ApiException(ErrorCode.INVALID_OTP,
                    "Delivery code is not on this server. Open the order on the customer phone to issue a fresh one.");
        }
        return new DeliveryOtpResponse(remembered.code(), remainingMinutes(remembered.expiresAt(), now));
    }

    private static long remainingMinutes(OffsetDateTime expiresAt, OffsetDateTime now) {
        long minutes = Duration.between(now, expiresAt).toMinutes();
        return Math.max(1, minutes);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void verifyDelivery(TripStop stop, String code) {
        OffsetDateTime now = OffsetDateTime.now();
        OtpVerification otp = repository.findFirstByDeliveryStop_IdOrderByCreatedAtDesc(stop.getId())
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
