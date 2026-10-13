package com.example.distrobackend.controller.admin;

import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Domain.enums.UserStatus;
import com.example.distrobackend.dto.UpdateUserStatusRequest;
import com.example.distrobackend.dto.UserResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    public Page<UserResponse> list(
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) UserRole role,
            Pageable pageable,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return userService.listForPlatform(actor, organizationId, status, role, pageable);
    }

    @PatchMapping("/{userId}/status")
    public UserResponse updateStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserStatusRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return userService.updateStatusForPlatform(actor, userId, request);
    }
}
