package com.example.distrobackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:${spring.mail.username}}")
    private String fromEmail;


    public void sendActivationEmail(
            String recipientEmail,
            String recipientName,
            String organizationName,
            String role,
            String activationLink
    ) {

        SimpleMailMessage message =
                new SimpleMailMessage();

        message.setFrom(fromEmail);

        message.setTo(recipientEmail);

        message.setSubject(
                "Activate your SupplyChain Admin Portal account"
        );

        message.setText(
                "Hello " + recipientName + ",\n\n"

                + "Your access request for "
                + organizationName
                + " has been approved.\n\n"

                + "You have been registered as: "
                + role
                + "\n\n"

                + "Please activate your account using the link below:\n\n"

                + activationLink
                + "\n\n"

                + "This activation link is valid for 48 hours.\n\n"

                + "After opening the link, you will be asked to "
                + "create your password and complete your account activation.\n\n"

                + "If you did not request access to the SupplyChain Admin Portal, "
                + "you can safely ignore this email.\n\n"

                + "Regards,\n"
                + "SupplyChain Admin Portal"
        );

        mailSender.send(message);
    }
}