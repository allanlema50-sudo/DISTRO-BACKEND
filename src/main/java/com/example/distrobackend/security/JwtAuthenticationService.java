package com.example.distrobackend.security;

import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Converts a validated JWT into the same authenticated principal for both HTTP and STOMP.
 * Organization claims are checked against the role so a signed token cannot claim an
 * inconsistent workspace.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationService {

    private final JwtService jwtService;

    public Authentication authenticateBearer(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Bearer token is required");
        }

        String token = authorizationHeader.substring(7).trim();
        if (token.isBlank()) {
            throw new IllegalArgumentException("Bearer token is required");
        }

        Claims claims = jwtService.parse(token);
        UserRole role = UserRole.valueOf(requiredClaim(claims, "role"));
        UUID userId = UUID.fromString(claims.getSubject());
        UUID organizationId = optionalUuid(claims.get("orgId", String.class));
        Organizationtype organizationType = optionalEnum(
                claims.get("orgType", String.class), Organizationtype.class);
        if (claims.getExpiration() == null) {
            throw new IllegalArgumentException("Required JWT claim is missing: exp");
        }

        validateOrganizationClaims(role, organizationId, organizationType);

        AuthenticatedUser principal = new AuthenticatedUser(
                userId, role, organizationId, organizationType,
                claims.getExpiration().toInstant());

        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private static void validateOrganizationClaims(
            UserRole role, UUID organizationId, Organizationtype organizationType) {
        if ((organizationId == null) != (organizationType == null)) {
            throw new IllegalArgumentException("Organization claims must be supplied together");
        }

        Organizationtype requiredType = role.organizationtype();
        if (requiredType != null && (organizationId == null || organizationType != requiredType)) {
            throw new IllegalArgumentException("Role and organization claims do not match");
        }

        if (role == UserRole.CUSTOMER && organizationId != null) {
            throw new IllegalArgumentException("Customers cannot carry organization claims");
        }
    }

    private static String requiredClaim(Claims claims, String name) {
        String value = claims.get(name, String.class);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required JWT claim is missing: " + name);
        }
        return value;
    }

    private static UUID optionalUuid(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    private static <E extends Enum<E>> E optionalEnum(String value, Class<E> type) {
        return value == null || value.isBlank() ? null : Enum.valueOf(type, value);
    }
}
