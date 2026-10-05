package com.example.distrobackend.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Shared authorization predicates for organization-scoped endpoints.
 * Business repositories must still apply the same organization predicate to every query.
 */
@Component("tenantAccess")
public class TenantAccessGuard {

    public boolean hasOrganization(Authentication authentication) {
        return principal(authentication) != null
                && principal(authentication).organizationId() != null;
    }

    public boolean isSameOrganization(Authentication authentication, UUID organizationId) {
        return principal(authentication) != null
                && organizationId != null
                && organizationId.equals(principal(authentication).organizationId());
    }

    private AuthenticatedUser principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return null;
        }
        return user;
    }
}
