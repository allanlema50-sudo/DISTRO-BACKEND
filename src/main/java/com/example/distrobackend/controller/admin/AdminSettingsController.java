
package com.example.distrobackend.controller.admin;

import com.example.distrobackend.Domain.entity.PlatformSettings;
import com.example.distrobackend.service.PlatformSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'SUPER_ADMIN')")
public class AdminSettingsController {

    private final PlatformSettingsService settingsService;

    @GetMapping
    public PlatformSettings getSettings() {
        return settingsService.getSettings();
    }

    @PutMapping
    public PlatformSettings updateSettings(
            @RequestBody PlatformSettings request
    ) {
        return settingsService.updateSettings(request);
    }
}
