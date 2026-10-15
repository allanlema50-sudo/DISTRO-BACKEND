package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.OtpPurpose;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!prod")
public class LoggingOtpSender implements OtpSender {

    @Override
    public void send(User user, OtpPurpose purpose, String code, long ttlMinutes) {
        log.info("[DEV OTP] {} code for {} is {} (valid {} min)", purpose, user.getPhoneNumber(), code, ttlMinutes);
    }
}