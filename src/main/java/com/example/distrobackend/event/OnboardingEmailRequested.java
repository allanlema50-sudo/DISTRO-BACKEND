package com.example.distrobackend.event;

public record OnboardingEmailRequested(String recipient, String subject, String body) {
}
