package com.example.distrobackend.controller.admin;

import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.dto.AccessRequestResponse;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.AccessRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/access-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','SUPER_ADMIN')")
public class AdminAccessRequestController {
    private final AccessRequestService service;
    private final UserRepository userRepository;

    @GetMapping
    public List<AccessRequestResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public AccessRequestResponse getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    @PostMapping("/{id}/approve")
    public AccessRequestResponse approve(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser me
    ) {
        return service.approve(id, getReviewer(me));
    }

    @PostMapping("/{id}/reject")
    public AccessRequestResponse reject(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser me
    ) {
        return service.reject(id, getReviewer(me));
    }

    private User getReviewer(AuthenticatedUser me) {
        return userRepository.findById(me.userId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }
}
