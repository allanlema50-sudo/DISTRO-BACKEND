package com.example.distrobackend.controller.admin;

import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Domain.enums.UserStatus;
import com.example.distrobackend.dto.UserResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("""
        hasAnyRole(
            'PLATFORM_ADMIN',
            'MANUFACTURER_ADMIN',
            'DISTRIBUTOR_ADMIN'
        )
        """)
public class AdminUserController {

    private final UserService userService;

    // ================================================================
    // GET ALL USERS
    // ================================================================

    @GetMapping
    public List<UserResponse> getUsers(
            @AuthenticationPrincipal AuthenticatedUser me
    ) {

        return userService.getUsers(me);
    }

    // ================================================================
    // GET USER
    // ================================================================

    @GetMapping("/{id}")
    public UserResponse getUser(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser me
    ) {

        return userService.getUserById(
                id,
                me
        );
    }

    // ================================================================
    // GET BY STATUS
    // ================================================================

    @GetMapping("/status/{status}")
    public List<UserResponse> getUsersByStatus(
            @PathVariable UserStatus status,
            @AuthenticationPrincipal AuthenticatedUser me
    ) {

        return userService.getUsersByStatus(
                status,
                me
        );
    }

    // ================================================================
    // GET BY ROLE
    // ================================================================

    @GetMapping("/role/{role}")
    public List<UserResponse> getUsersByRole(
            @PathVariable UserRole role,
            @AuthenticationPrincipal AuthenticatedUser me
    ) {

        return userService.getUsersByRole(
                role,
                me
        );
    }

    // ================================================================
    // UPDATE STATUS
    // ================================================================

    @PatchMapping("/{id}/status")
    public UserResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody StatusUpdateRequest request,
            @AuthenticationPrincipal AuthenticatedUser me
    ) {

        return userService.updateUserStatus(
                id,
                request.status(),
                me
        );
    }

    // ================================================================
    // REQUEST DTO
    // ================================================================

    public record StatusUpdateRequest(
            UserStatus status
    ) {
    }
}