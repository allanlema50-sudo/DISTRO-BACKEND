package com.example.distrobackend.controller;

import com.example.distrobackend.dto.TripResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
@PreAuthorize("""
        hasAnyRole(
            'PLATFORM_ADMIN',
            'SUPER_ADMIN',
            'MANUFACTURER_ADMIN',
            'MANUFACTURER_STAFF',
            'DISTRIBUTOR_ADMIN',
            'DISTRIBUTOR_STAFF'
        )
        """)
public class TripController {

    private final TripService tripService;

    @GetMapping
    public Page<TripResponse> getDeliveries(
            @AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String search
    ) {
        return tripService.getDeliveries(
                me,
                page,
                size,
                search
        );
    }
}
