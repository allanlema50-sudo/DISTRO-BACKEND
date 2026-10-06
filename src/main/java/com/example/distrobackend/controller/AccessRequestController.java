package com.example.distrobackend.controller;

import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.dto.AccessRequestResponse;
import com.example.distrobackend.dto.CreateAccessRequest;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.AccessRequestService;
import com.example.distrobackend.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AccessRequestController {

    private final AccessRequestService accessRequestService;

    private final UserRepository userRepository;


    // =========================================================
    // PUBLIC - SUBMIT REQUEST
    // =========================================================

    @PostMapping("/access-requests")
    public AccessRequestResponse create(
            @Valid @RequestBody CreateAccessRequest request
    ) {

        return accessRequestService.create(
                request
        );
    }


    // =========================================================
    // ADMIN - LIST REQUESTS
    // =========================================================

    @GetMapping("/admin/access-requests")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public List<AccessRequestResponse> getAll() {

        return accessRequestService.getAll();
    }


    // =========================================================
    // ADMIN - GET REQUEST
    // =========================================================

    @GetMapping("/admin/access-requests/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public AccessRequestResponse getById(
            @PathVariable UUID id
    ) {

        return accessRequestService.getById(
                id
        );
    }


    // =========================================================
    // ADMIN - APPROVE
    // =========================================================

    @PostMapping("/admin/access-requests/{id}/approve")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public AccessRequestResponse approve(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser me
    ) {

        User reviewer =
                userRepository.findById(
                        me.userId()
                ).orElseThrow(() ->
                        new IllegalStateException(
                                "Authenticated user not found"
                        )
                );

        return accessRequestService.approve(
                id,
                reviewer
        );
    }


    // =========================================================
    // ADMIN - REJECT
    // =========================================================

    @PostMapping("/admin/access-requests/{id}/reject")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public AccessRequestResponse reject(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser me
    ) {

        User reviewer =
                userRepository.findById(
                        me.userId()
                ).orElseThrow(() ->
                        new IllegalStateException(
                                "Authenticated user not found"
                        )
                );

        return accessRequestService.reject(
                id,
                reviewer
        );
    }
}