package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.OtpPurpose;

public interface OtpSender {
    void send(User user, OtpPurpose purpose, String code, long ttlMinutes);
}
