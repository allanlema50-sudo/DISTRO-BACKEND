package com.example.distrobackend.security;


import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Deliberately NOT a Spring bean: SecurityConfig instantiates it, so Spring Boot does not
 * auto-register it a second time as a plain servlet filter.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String AUTH_ERROR_ATTRIBUTE = "jwt_auth_error";

    private final JwtService jwtService;
    private final OrganizationRepository organizationRepository;

    public JwtAuthFilter(
            JwtService jwtService,
            OrganizationRepository organizationRepository
    ) {
        this.jwtService = jwtService;
        this.organizationRepository = organizationRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            AuthenticatedUser principal;
            try {
                Claims claims = jwtService.parse(token);

                UserRole role = UserRole.valueOf(claims.get("role", String.class));
                String orgId = claims.get("orgId", String.class);
                String orgType = claims.get("orgType", String.class);

                principal = new AuthenticatedUser(
                        UUID.fromString(claims.getSubject()),
                        role,
                        orgId == null ? null : UUID.fromString(orgId),
                        orgType == null ? null : Organizationtype.valueOf(orgType));
            } catch (ExpiredJwtException e) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, "TOKEN_EXPIRED");
                chain.doFilter(request, response);
                return;
            } catch (RuntimeException e) {
                // JwtException (bad signature/format), or a malformed/missing claim (NPE / IllegalArgumentException)
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, "INVALID_TOKEN");
                chain.doFilter(request, response);
                return;
            }

            if (principal.organizationId() != null
                    && !organizationRepository.existsByIdAndActiveTrue(principal.organizationId())) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, "ORGANIZATION_DEACTIVATED");
                chain.doFilter(request, response);
                return;
            }

            var authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        chain.doFilter(request, response);
    }
}
