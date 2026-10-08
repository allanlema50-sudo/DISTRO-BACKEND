package com.example.distrobackend.configuration;

import com.example.distrobackend.security.JwtAuthFilter;
import com.example.distrobackend.security.JwtService;
import com.example.distrobackend.security.RestAccessDeniedHandler;
import com.example.distrobackend.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpMethod;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtService jwtService;

    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    private final RestAccessDeniedHandler accessDeniedHandler;


    // =========================================================
    // PASSWORD ENCODER
    // =========================================================

    @Bean
    public PasswordEncoder passwordEncoder() {

        return new BCryptPasswordEncoder(12);
    }


    // =========================================================
    // SECURITY FILTER CHAIN
    // =========================================================

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http

                // -------------------------------------------------
                // CSRF
                // -------------------------------------------------

                .csrf(csrf -> csrf.disable())


                // -------------------------------------------------
                // CORS
                // -------------------------------------------------

                .cors(Customizer.withDefaults())


                // -------------------------------------------------
                // SESSION MANAGEMENT
                // -------------------------------------------------

                .sessionManagement(s ->
                        s.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )


                // -------------------------------------------------
                // EXCEPTION HANDLING
                // -------------------------------------------------

                .exceptionHandling(e -> e

                        .authenticationEntryPoint(
                                authenticationEntryPoint
                        )

                        .accessDeniedHandler(
                                accessDeniedHandler
                        )
                )


                // -------------------------------------------------
                // AUTHORIZATION
                // -------------------------------------------------

                .authorizeHttpRequests(auth -> auth

                        // CORS preflight requests
                        .requestMatchers(
                                HttpMethod.OPTIONS,
                                "/**"
                        ).permitAll()


                        // Authentication
                        .requestMatchers(
                                "/api/auth/**"
                        ).permitAll()


                        // Public access requests
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/access-requests",
                                "/api/access-requests/activate"
                        ).permitAll()


                        // Public invitation lookup
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/invitations/*"
                        ).permitAll()


                        // Public invitation acceptance
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/invitations/*/accept"
                        ).permitAll()


                        // Stock administration
                        .requestMatchers(
                                "/api/admin/stock/**"
                        ).hasAnyRole(
                                "PLATFORM_ADMIN",
                                "SUPER_ADMIN",
                                "MANUFACTURER_ADMIN",
                                "DISTRIBUTOR_ADMIN"
                        )


                        // Platform administration
                        .requestMatchers(
                                "/api/admin/**"
                        ).hasAnyRole(
                                "PLATFORM_ADMIN",
                                "SUPER_ADMIN"
                        )


                        // Manufacturer
                        .requestMatchers(
                                "/api/manufacturer/**"
                        ).hasAnyRole(
                                "MANUFACTURER_ADMIN",
                                "MANUFACTURER_STAFF"
                        )


                        // Distributor
                        .requestMatchers(
                                "/api/distributor/**"
                        ).hasAnyRole(
                                "DISTRIBUTOR_ADMIN",
                                "DISTRIBUTOR_STAFF"
                        )


                        // Driver
                        .requestMatchers(
                                "/api/driver/**"
                        ).hasRole("DRIVER")


                        // Customer
                        .requestMatchers(
                                "/api/customer/**"
                        ).hasRole("CUSTOMER")


                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )


                // -------------------------------------------------
                // JWT AUTHENTICATION FILTER
                // -------------------------------------------------

                .addFilterBefore(
                        new JwtAuthFilter(jwtService),
                        UsernamePasswordAuthenticationFilter.class
                );


        return http.build();
    }
}
