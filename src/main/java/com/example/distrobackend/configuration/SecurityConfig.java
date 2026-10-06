package com.example.distrobackend.configuration;

import com.example.distrobackend.security.JwtAuthFilter;
import com.example.distrobackend.security.JwtService;
import com.example.distrobackend.security.RestAccessDeniedHandler;
import com.example.distrobackend.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtService jwtService;

    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    private final RestAccessDeniedHandler accessDeniedHandler;


    @Bean
    public PasswordEncoder passwordEncoder() {

        return new BCryptPasswordEncoder(12);

    }


    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http

                .csrf(csrf ->
                        csrf.disable()
                )

                .cors(Customizer.withDefaults())

                .sessionManagement(s ->
                        s.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .exceptionHandling(e -> e

                        .authenticationEntryPoint(
                                authenticationEntryPoint
                        )

                        .accessDeniedHandler(
                                accessDeniedHandler
                        )

                )

                .authorizeHttpRequests(auth -> auth


                        // =================================================
                        // CORS PREFLIGHT
                        // =================================================

                        .requestMatchers(
                                HttpMethod.OPTIONS,
                                "/**"
                        )
                        .permitAll()


                        // =================================================
                        // AUTHENTICATION
                        // =================================================

                        .requestMatchers(
                                "/api/auth/**"
                        )
                        .permitAll()


                        // =================================================
                        // PUBLIC ACCESS REQUEST
                        // =================================================
                        //
                        // A new organization does not have an account yet,
                        // therefore it must be able to submit an access
                        // request without authentication.
                        //

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/access-requests"
                        )
                        .permitAll()


                        // =================================================
                        // PUBLIC INVITATION ACCEPTANCE
                        // =================================================

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/invitations/*"
                        )
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/invitations/*/accept"
                        )
                        .permitAll()


                        // =================================================
                        // MANUFACTURER
                        // =================================================

                        .requestMatchers(
                                "/api/manufacturer/**"
                        )
                        .hasAnyRole(
                                "MANUFACTURER_ADMIN",
                                "MANUFACTURER_STAFF"
                        )


                        // =================================================
                        // DISTRIBUTOR
                        // =================================================

                        .requestMatchers(
                                "/api/distributor/**"
                        )
                        .hasAnyRole(
                                "DISTRIBUTOR_ADMIN",
                                "DISTRIBUTOR_STAFF"
                        )


                        // =================================================
                        // DRIVER
                        // =================================================

                        .requestMatchers(
                                "/api/driver/**"
                        )
                        .hasRole("DRIVER")


                        // =================================================
                        // CUSTOMER
                        // =================================================

                        .requestMatchers(
                                "/api/customer/**"
                        )
                        .hasRole("CUSTOMER")


                        // =================================================
                        // EVERYTHING ELSE REQUIRES AUTHENTICATION
                        // =================================================

                        .anyRequest()
                        .authenticated()

                )

                .addFilterBefore(
                        new JwtAuthFilter(jwtService),
                        UsernamePasswordAuthenticationFilter.class
                );


        return http.build();

    }


    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}")
            List<String> allowedOrigins
    ) {

        CorsConfiguration config =
                new CorsConfiguration();


        config.setAllowedOrigins(
                allowedOrigins
        );


        config.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "PATCH",
                        "DELETE",
                        "OPTIONS"
                )
        );


        config.setAllowedHeaders(
                List.of(
                        "Authorization",
                        "Content-Type"
                )
        );


        config.setMaxAge(
                3600L
        );


        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();


        source.registerCorsConfiguration(
                "/**",
                config
        );


        return source;

    }

}