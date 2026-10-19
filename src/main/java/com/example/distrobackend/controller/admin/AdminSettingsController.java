package com.example.distrobackend.controller.admin;

import com.example.distrobackend.dto.PlatformSettingsResponse;
import com.example.distrobackend.dto.UpdatePlatformSettingsRequest;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.PlatformSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final PlatformSettingsService settingsService;

    @GetMapping
    public PlatformSettingsResponse get(@AuthenticationPrincipal AuthenticatedUser actor) {
        return settingsService.get(actor);
    }

    @PutMapping
    public PlatformSettingsResponse update(@AuthenticationPrincipal AuthenticatedUser actor,
                                           @Valid @RequestBody UpdatePlatformSettingsRequest request) {
        return settingsService.update(actor, request);
    }
}
