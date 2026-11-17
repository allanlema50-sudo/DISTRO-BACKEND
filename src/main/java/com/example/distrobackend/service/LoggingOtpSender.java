package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.OtpPurpose;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!prod")
@ConditionalOnExpression("not ('mailgun'.equalsIgnoreCase('${otp.provider:http}') or 'brevo'.equalsIgnoreCase('${otp.provider:http}'))")
public class LoggingOtpSender implements OtpSender {

    @Override
    public void send(User user, OtpPurpose purpose, String code, long ttlMinutes) {
        log.info("[DEV OTP] purpose={} email={} phone={} code={} valid {} min",
                purpose, user.getEmail(), user.getPhoneNumber(), code, ttlMinutes);
    }
}