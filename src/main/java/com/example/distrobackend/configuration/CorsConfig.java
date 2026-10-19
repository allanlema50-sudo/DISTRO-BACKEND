package com.example.distrobackend.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();

        // Browser-based clients (Angular, React, etc.) send an Origin header —
        // only the origins listed here are accepted.
        config.setAllowedOrigins(allowedOrigins);

        // Mobile apps (Android / iOS) never send an Origin header, so CORS
        // does not apply to them; Spring skips the check entirely for
        // requests with no Origin. No extra configuration is needed.

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // Keep the request-header allowlist explicit. CORS is not an
        // authorization boundary, but an explicit list prevents accidental
        // expansion when clients add headers over time.
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Requested-With",
                "X-Platform",
                "ngrok-skip-browser-warning"
        ));
        config.setExposedHeaders(List.of("Location", "X-Total-Count"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);  // cache preflight for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
