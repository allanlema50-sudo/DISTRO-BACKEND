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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())               // stateless API, bearer tokens, no cookies
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/access-requests", "/api/access-requests/activate").permitAll()
                        // This legacy stock listing is also used by organization workspaces.
                        .requestMatchers("/api/admin/stock/**").hasAnyRole(
                                "PLATFORM_ADMIN", "SUPER_ADMIN",
                                "MANUFACTURER_ADMIN", "DISTRIBUTOR_ADMIN")
                        // Platform admins manage the shared platform; SUPER_ADMIN remains accepted for older tokens.
                        .requestMatchers("/api/admin/**").hasAnyRole("PLATFORM_ADMIN", "SUPER_ADMIN")

                        // Workspace isolation: each area is reachable only by its own roles.
                        .requestMatchers("/api/manufacturer/**")
                        .hasAnyRole("MANUFACTURER_ADMIN", "MANUFACTURER_STAFF")
                        .requestMatchers("/api/distributor/**")
                        .hasAnyRole("DISTRIBUTOR_ADMIN", "DISTRIBUTOR_STAFF")
                        .requestMatchers("/api/driver/**").hasRole("DRIVER")
                        .requestMatchers("/api/customer/**").hasRole("CUSTOMER")

                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

}
