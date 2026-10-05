package com.example.distrobackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
@RequiredArgsConstructor
public class SmtpAccessEmailSender implements AccessEmailSender {
    private final JavaMailSender mailSender;
    @Value("${app.mail.from}") private String from;
    @Value("${app.super-admin.emails:}") private String adminEmails;

    @Override public void sendActivation(String email, String activationUrl) {
        send(email, "Activate your LogiFlow account", "Set your password using this single-use link (expires in 24 hours):\n" + activationUrl);
    }
    @Override public void notifySuperAdmins(String subject, String body) {
        java.util.Arrays.stream(adminEmails.split(","))
                .map(String::trim).filter(email -> !email.isEmpty())
                .forEach(email -> send(email, subject, body));
    }
    private void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(to); message.setSubject(subject); message.setText(body);
        mailSender.send(message);
    }
}
