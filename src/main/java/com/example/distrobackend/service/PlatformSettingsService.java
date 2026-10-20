package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.PlatformSetting;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.PlatformSettingsResponse;
import com.example.distrobackend.dto.UpdatePlatformSettingsRequest;
import com.example.distrobackend.repository.PlatformSettingRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PlatformSettingsService {

    private static final String MAINTENANCE_MODE = "maintenanceMode";
    private static final String SUPPORT_EMAIL = "supportEmail";
    private static final String DEFAULT_DELIVERY_FEE = "defaultDeliveryFee";

    private final PlatformSettingRepository repository;

    @Value("${app.orders.delivery-fee:0.00}")
    private BigDecimal configuredDeliveryFee;

    @Transactional(readOnly = true)
    public PlatformSettingsResponse get(AuthenticatedUser actor) {
        requirePlatformAdmin(actor);
        return new PlatformSettingsResponse(readBoolean(MAINTENANCE_MODE, false),
                read(SUPPORT_EMAIL, ""), resolveDeliveryFee());
    }

    @Transactional
    public PlatformSettingsResponse update(AuthenticatedUser actor, UpdatePlatformSettingsRequest request) {
        requirePlatformAdmin(actor);
        if (request.maintenanceMode() != null) write(MAINTENANCE_MODE, request.maintenanceMode().toString());
        if (request.supportEmail() != null) write(SUPPORT_EMAIL, request.supportEmail().trim().toLowerCase(Locale.ROOT));
        if (request.defaultDeliveryFee() != null) write(DEFAULT_DELIVERY_FEE, request.defaultDeliveryFee().toPlainString());
        return get(actor);
    }

    /**
     * Resolves the effective fee used by order creation. Database settings
     * override the environment default, while the environment value remains
     * the fallback for a fresh deployment.
     */
    @Transactional(readOnly = true)
    public BigDecimal resolveDeliveryFee() {
        BigDecimal fallback = configuredDeliveryFee == null
                ? BigDecimal.ZERO : configuredDeliveryFee;
        return readDecimal(DEFAULT_DELIVERY_FEE, fallback);
    }

    private String read(String key, String fallback) {
        return repository.findById(key).map(PlatformSetting::getValue).orElse(fallback);
    }

    private boolean readBoolean(String key, boolean fallback) {
        String value = read(key, Boolean.toString(fallback));
        return Boolean.parseBoolean(value);
    }

    private BigDecimal readDecimal(String key, BigDecimal fallback) {
        try {
            return new BigDecimal(read(key, fallback.toPlainString()));
        } catch (NumberFormatException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Stored platform settings are invalid");
        }
    }

    private void write(String key, String value) {
        PlatformSetting setting = repository.findById(key).orElseGet(() -> {
            PlatformSetting created = new PlatformSetting();
            created.setKey(key);
            return created;
        });
        setting.setValue(value);
        repository.save(setting);
    }

    private static void requirePlatformAdmin(AuthenticatedUser actor) {
        if (actor == null || actor.role() != UserRole.SUPER_ADMIN) throw new ApiException(ErrorCode.ACCESS_DENIED);
    }
}
