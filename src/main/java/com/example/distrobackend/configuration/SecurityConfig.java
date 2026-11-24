package com.example.distrobackend.configuration;


import com.example.distrobackend.controller.DebugIpController;
import com.example.distrobackend.security.JwtAuthFilter;
import com.example.distrobackend.security.JwtAuthenticationService;
import com.example.distrobackend.security.RestAccessDeniedHandler;
import com.example.distrobackend.security.RestAuthenticationEntryPoint;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationService authenticationService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    /** TEMPORARY - remove after proxy hop check. */
    @Value("${DEBUG_IP_ENDPOINT_ENABLED:false}")
    private boolean debugIpEndpointEnabled;

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
                .authorizeHttpRequests(auth -> {
                    auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        // Permit only the deliberately public onboarding operations. Keeping
                        // this method/path-specific prevents a future authenticated onboarding
                        // endpoint from becoming public by accident.
                        .requestMatchers(HttpMethod.POST,
                                "/api/access-requests",
                                "/api/access-requests/activate",
                                "/api/invitations/*/accept").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/invitations/*").permitAll()
                        .requestMatchers("/api/v1/payments/mpesa/callback", "/api/payments/mpesa/callback",
                                "/api/v1/checkouts/paystack/webhook").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // The HTTP upgrade is unauthenticated; STOMP CONNECT is
                        // authenticated and authorized by StompChannelInterceptor.
                        .requestMatchers("/ws", "/ws/**").permitAll()
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // Platform administration is deliberately separate from
                        // organization administration. Organization admins must
                        // never reach platform-wide workflows through this path.
                        .requestMatchers("/api/admin/**", "/api/v1/admin/**", "/api/organizations/**",
                                "/api/manufacturers/**", "/api/distributors/**",
                                "/api/v1/reports/**", "/api/v1/analytics/**", "/api/v1/audit-logs/**",
                                "/api/v1/settings/**")
                        .hasRole("SUPER_ADMIN")

                        // Workspace isolation: each area is reachable only by its own roles.
                        .requestMatchers("/api/manufacturer/**")
                        .hasAnyRole("MANUFACTURER_ADMIN", "MANUFACTURER_STAFF")
                        .requestMatchers("/api/distributor/**")
                        .hasAnyRole("DISTRIBUTOR_ADMIN", "DISTRIBUTOR_STAFF")
                        .requestMatchers("/api/driver/**").hasRole("DRIVER")
                        .requestMatchers("/api/customer/**").hasRole("CUSTOMER");
                    // TEMPORARY - remove after proxy hop check.
                    // Public only while the flag is true. When false, the path
                    // stays authenticated and the endpoint filter returns 404.
                    if (debugIpEndpointEnabled) {
                        auth.requestMatchers(HttpMethod.GET, DebugIpController.PATH).permitAll();
                    }
                    auth.anyRequest().authenticated();
                });
        // TEMPORARY - remove after proxy hop check.
        // Runs before authentication, so a disabled endpoint answers 404 and
        // is not reachable without auth.
        if (!debugIpEndpointEnabled) {
            http.addFilterBefore((request, response, chain) -> {
                if (request instanceof HttpServletRequest httpRequest
                        && response instanceof HttpServletResponse httpResponse
                        && "GET".equalsIgnoreCase(httpRequest.getMethod())
                        && DebugIpController.PATH.equals(httpRequest.getRequestURI())) {
                    httpResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                chain.doFilter(request, response);
            }, UsernamePasswordAuthenticationFilter.class);
        }
        http.addFilterBefore(new JwtAuthFilter(authenticationService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
