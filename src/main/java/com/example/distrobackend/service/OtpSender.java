package com.example.distrobackend.service;

import com.example.distrobackend.Domain.enums.OtpPurpose;

public interface OtpSender {
    void send(String phoneNumber, OtpPurpose purpose, String code, long ttlMinutes);
}
