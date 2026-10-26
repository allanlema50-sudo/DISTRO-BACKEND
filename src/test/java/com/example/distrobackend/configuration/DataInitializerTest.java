package com.example.distrobackend.configuration;

import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Domain.enums.UserStatus;
import com.example.distrobackend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private DataInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new DataInitializer(userRepository, passwordEncoder);
        ReflectionTestUtils.setField(initializer, "bootstrapEmail", "admin@example.com");
        ReflectionTestUtils.setField(initializer, "bootstrapPassword", "AdminPassword1");
        ReflectionTestUtils.setField(initializer, "bootstrapFullName", "Platform Administrator");
        ReflectionTestUtils.setField(initializer, "bootstrapPhone", "+254700000001");
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
    }

    @Test
    void reconcilesExistingPlatformAdminWhenBootstrapIsEnabled() {
        User existing = new User();
        existing.setEmail("ADMIN@example.com");
        existing.setFullName("Old Name");
        existing.setPhoneNumber("+254700000002");
        existing.setRole(UserRole.SUPER_ADMIN);
        existing.setStatus(UserStatus.SUSPENDED);
        existing.setEmailVerified(false);
        existing.setPhoneVerified(false);

        when(userRepository.findByEmailIgnoreCase("admin@example.com"))
                .thenReturn(Optional.of(existing));
        when(userRepository.existsByPhoneNumber("+254700000001")).thenReturn(false);

        initializer.run();

        assertThat(existing.getFullName()).isEqualTo("Platform Administrator");
        assertThat(existing.getPhoneNumber()).isEqualTo("+254700000001");
        assertThat(existing.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(existing.getRole()).isEqualTo(UserRole.SUPER_ADMIN);
        assertThat(existing.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(existing.isEmailVerified()).isTrue();
        assertThat(existing.isPhoneVerified()).isTrue();
        verify(userRepository).save(existing);
    }

    @Test
    void failsClearlyWithoutChangingTenantAccount() {
        User existing = new User();
        existing.setEmail("admin@example.com");
        existing.setRole(UserRole.MANUFACTURER_ADMIN);

        when(userRepository.findByEmailIgnoreCase("admin@example.com"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(initializer::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("belongs to a tenant or non-platform account")
                .hasMessageContaining("BOOTSTRAP_SUPER_ADMIN_ENABLED=false");

        verify(userRepository, never()).save(existing);
    }

    @Test
    void rejectsPhoneAlreadyUsedByAnotherAccountWithoutChangingAdmin() {
        User existing = new User();
        existing.setEmail("admin@example.com");
        existing.setPhoneNumber("+254700000002");
        existing.setRole(UserRole.SUPER_ADMIN);

        when(userRepository.findByEmailIgnoreCase("admin@example.com"))
                .thenReturn(Optional.of(existing));
        when(userRepository.existsByPhoneNumber("+254700000001")).thenReturn(true);

        assertThatThrownBy(initializer::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PHONE is already assigned");

        verify(userRepository, never()).save(existing);
    }
}
