package com.example.distrobackend.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TEMPORARY - remove after proxy hop check.
 * Reports {@code request.getRemoteAddr()} and the raw {@code X-Forwarded-For}
 * header so the trusted proxy count can be confirmed on Render.
 * Active only when {@code DEBUG_IP_ENDPOINT_ENABLED=true}; otherwise 404.
 * Returns no other headers, cookies, or secrets.
 */
@RestController
public class DebugIpController {

    public static final String PATH = "/debug/ip";

    @Value("${DEBUG_IP_ENDPOINT_ENABLED:false}")
    private boolean enabled;

    @GetMapping(PATH)
    public ResponseEntity<DebugIpResponse> ip(HttpServletRequest request) {
        if (!enabled) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new DebugIpResponse(
                request.getRemoteAddr(),
                request.getHeader("X-Forwarded-For")));
    }

    public record DebugIpResponse(String remoteAddr, String xForwardedFor) {}
}
