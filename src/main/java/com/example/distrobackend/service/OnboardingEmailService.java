package com.example.distrobackend.service;

import com.example.distrobackend.event.OnboardingEmailRequested;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Delivers onboarding email only after the transaction that created the token
 * commits. Tokens are never written to application logs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OnboardingEmailService {
    private final ObjectProvider<JavaMailSender> mailSender;

    @Value("${app.mail.enabled:false}")
    private boolean enabled;

    @Value("${app.mail.from:no-reply@logiflow.local}")
    private String from;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliver(OnboardingEmailRequested event) {
        if (!enabled) {
            log.debug("Onboarding email delivery is disabled");
            return;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.error("Onboarding email is enabled but no JavaMailSender is configured");
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(event.recipient());
            message.setSubject(event.subject());
            message.setText(event.body());
            sender.send(message);
        } catch (RuntimeException ex) {
            // The onboarding record and token remain the source of truth. A
            // mail outage must not turn a committed access decision into a 500.
            log.error("Onboarding email delivery failed for recipient domain", ex);
        }
    }
}
