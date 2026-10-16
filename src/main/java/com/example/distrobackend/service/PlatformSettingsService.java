
package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.PlatformSettings;
import com.example.distrobackend.repository.PlatformSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

@Service
@RequiredArgsConstructor
public class PlatformSettingsService {

    private final PlatformSettingsRepository repository;

    private static final Set<String> ALLOWED_CURRENCIES =
            Set.of("KES", "USD", "EUR", "GBP");

    @Transactional(readOnly = true)
    public PlatformSettings getSettings() {
        return repository.findById(1L)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Platform settings have not been initialized."
                        )
                );
    }

    @Transactional
    public PlatformSettings updateSettings(PlatformSettings request) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Settings request must not be empty."
            );
        }

        if (request.getPlatformName() == null
                || request.getPlatformName().isBlank()
                || request.getPlatformName().trim().length() > 100) {
            throw new IllegalArgumentException(
                    "Platform name is required and must not exceed 100 characters."
            );
        }

        if (request.getCurrency() == null
                || !ALLOWED_CURRENCIES.contains(
                        request.getCurrency().trim().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "Currency must be KES, USD, EUR, or GBP."
            );
        }

        if (request.getTimezone() == null
                || !isValidTimezone(request.getTimezone().trim())) {
            throw new IllegalArgumentException(
                    "Please provide a valid timezone."
            );
        }

        if (request.getLowStockThreshold() == null
                || request.getLowStockThreshold() < 0) {
            throw new IllegalArgumentException(
                    "Low-stock threshold must be zero or greater."
            );
        }

        PlatformSettings settings = getSettings();

        settings.setPlatformName(request.getPlatformName().trim());
        settings.setCurrency(
                request.getCurrency().trim().toUpperCase(Locale.ROOT)
        );
        settings.setTimezone(request.getTimezone().trim());
        settings.setLowStockThreshold(request.getLowStockThreshold());
        settings.setEmailNotifications(
                Boolean.TRUE.equals(request.getEmailNotifications())
        );
        settings.setSystemNotifications(
                Boolean.TRUE.equals(request.getSystemNotifications())
        );

        return repository.save(settings);
    }

    private boolean isValidTimezone(String timezone) {
        return TimeZone.getAvailableIDs().length > 0
                && Set.of(TimeZone.getAvailableIDs()).contains(timezone);
    }
}
