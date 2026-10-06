package com.example.distrobackend.controller.admin;




import com.example.distrobackend.security.AuthenticatedUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/distributor")
@PreAuthorize("hasAnyRole('DISTRIBUTOR_ADMIN','DISTRIBUTOR_STAFF') and @tenantAccess.hasOrganization(authentication)")
public class DistributorController {

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@AuthenticationPrincipal AuthenticatedUser me) {
        return Map.of(
                "workspace", "DISTRIBUTOR",
                "role", me.role(),
                "organizationId", me.organizationId());
    }

    @GetMapping("/staff")
    @PreAuthorize("hasRole('DISTRIBUTOR_ADMIN')")
    public Map<String, Object> staff(@AuthenticationPrincipal AuthenticatedUser me) {
        return Map.of("message", "Admin-only: staff list for organization " + me.organizationId());
    }
}
