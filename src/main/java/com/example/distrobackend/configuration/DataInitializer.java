package com.example.distrobackend.configuration;

import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Domain.enums.UserStatus;
import com.example.distrobackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        String email = "michellemakimei@gmail.com";

        if (!userRepository.existsByEmail(email)) {
            User admin = new User();
            admin.setFullName("Michelle Makimei");
            admin.setEmail(email);
            admin.setPhoneNumber("+254700000000");
            admin.setPasswordHash(passwordEncoder.encode("Admin@12345"));
            admin.setRole(UserRole.SUPER_ADMIN);
            admin.setOrganization(null);
            admin.setStatus(UserStatus.ACTIVE);
            admin.setPhoneVerified(true);
            admin.setEmailVerified(true);

            userRepository.save(admin);
            log.info("Initialized default SUPER_ADMIN account: {}", email);
        } else {
            log.info("SUPER_ADMIN account {} already exists.", email);
        }
    }
}
