package com.example.distrobackend.controller.admin;




import com.example.distrobackend.security.AuthenticatedUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Example of a role-restricted area. Two layers protect it:
 *   1. SecurityConfig: /api/manufacturer/** requires a MANUFACTURER_* role.
 *   2. @PreAuthorize: finer rules per endpoint (e.g. admin-only).
 * Replace the placeholder bodies with your real services and ALWAYS filter data by me.organizationId().
 */
@RestController
@RequestMapping("/api/manufacturer")
@PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','MANUFACTURER_STAFF') and @tenantAccess.hasOrganization(authentication)")
public class ManufacturerController {

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@AuthenticationPrincipal AuthenticatedUser me) {
        return Map.of(
                "workspace", "MANUFACTURER",
                "role", me.role(),
                "organizationId", me.organizationId());
    }

    /** Only the manufacturer's administrator can manage staff. */
    @GetMapping("/staff")
    @PreAuthorize("hasRole('MANUFACTURER_ADMIN')")
    public Map<String, Object> staff(@AuthenticationPrincipal AuthenticatedUser me) {
        return Map.of("message", "Admin-only: staff list for organization " + me.organizationId());
    }
}
