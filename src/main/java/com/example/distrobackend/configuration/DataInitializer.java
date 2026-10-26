package com.example.distrobackend.configuration;

import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Domain.enums.UserStatus;
import com.example.distrobackend.Util.PasswordRules;
import com.example.distrobackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.bootstrap.super-admin.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final Set<String> PASSWORD_PLACEHOLDERS = Set.of(
            "REPLACE_ME_WITH_A_UNIQUE_PASSWORD",
            "use-a-strong-password-at-least-12-characters");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.super-admin.email:}")
    private String bootstrapEmail;

    @Value("${app.bootstrap.super-admin.password:}")
    private String bootstrapPassword;

    @Value("${app.bootstrap.super-admin.full-name:}")
    private String bootstrapFullName;

    @Value("${app.bootstrap.super-admin.phone:}")
    private String bootstrapPhone;

    @Override
    @Transactional
    public void run(String... args) {
        String email = requireValue(bootstrapEmail, "BOOTSTRAP_SUPER_ADMIN_EMAIL").toLowerCase(Locale.ROOT);
        String password = requirePassword(bootstrapPassword);
        String fullName = requireValue(bootstrapFullName, "BOOTSTRAP_SUPER_ADMIN_FULL_NAME");
        String phone = requireValue(bootstrapPhone, "BOOTSTRAP_SUPER_ADMIN_PHONE");

        if (fullName.length() > 150 || phone.length() > 20) {
            throw new IllegalStateException("Bootstrap administrator profile values exceed database limits");
        }

        User existing = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (existing != null) {
            if (existing.getRole() != UserRole.SUPER_ADMIN || existing.getOrganization() != null) {
                throw new IllegalStateException("Bootstrap email belongs to a non-platform account");
            }
            log.info("Bootstrap SUPER_ADMIN account already exists; no changes made");
            return;
        }

        if (userRepository.existsByPhoneNumber(phone)) {
            throw new IllegalStateException("BOOTSTRAP_SUPER_ADMIN_PHONE is already assigned to another account");
        }

        User admin = new User();
        admin.setFullName(fullName);
        admin.setEmail(email);
        admin.setPhoneNumber(phone);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(UserRole.SUPER_ADMIN);
        admin.setOrganization(null);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setPhoneVerified(true);
        admin.setEmailVerified(true);

        userRepository.save(admin);
        log.info("Initialized configured SUPER_ADMIN account");
    }

    private static String requireValue(String value, String environmentVariable) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(environmentVariable + " must be set when bootstrap is enabled");
        }
        return value.trim();
    }

    private static String requirePassword(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "BOOTSTRAP_SUPER_ADMIN_PASSWORD must be set when bootstrap is enabled");
        }
        if (PASSWORD_PLACEHOLDERS.contains(value.trim())) {
            throw new IllegalStateException(
                    "BOOTSTRAP_SUPER_ADMIN_PASSWORD must be replaced with a unique password");
        }
        if (!value.matches(PasswordRules.PATTERN)) {
            throw new IllegalStateException(
                    "BOOTSTRAP_SUPER_ADMIN_PASSWORD is invalid: " + PasswordRules.MESSAGE);
        }
        // Do not trim: whitespace is part of the configured password and must
        // be preserved exactly when BCrypt creates the stored hash.
        return value;
    }
}
