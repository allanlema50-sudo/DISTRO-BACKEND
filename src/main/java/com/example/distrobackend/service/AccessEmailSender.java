package com.example.distrobackend.service;

public interface AccessEmailSender {
    void sendActivation(String email, String activationUrl);
    void notifySuperAdmins(String subject, String body);
}
