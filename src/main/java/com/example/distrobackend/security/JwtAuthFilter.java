package com.example.distrobackend.security;


import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
/**
 * Deliberately NOT a Spring bean: SecurityConfig instantiates it, so Spring Boot does not
 * auto-register it a second time as a plain servlet filter.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String AUTH_ERROR_ATTRIBUTE = "jwt_auth_error";

    private final JwtAuthenticationService authenticationService;

    public JwtAuthFilter(JwtAuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                SecurityContextHolder.getContext().setAuthentication(
                        authenticationService.authenticateBearer("Bearer " + token));

            } catch (ExpiredJwtException e) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, "TOKEN_EXPIRED");
            } catch (RuntimeException e) {
                // JwtException (bad signature/format), or a malformed/missing claim (NPE / IllegalArgumentException)
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, "INVALID_TOKEN");
            }
        }
        chain.doFilter(request, response);
    }
}
