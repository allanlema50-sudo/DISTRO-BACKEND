package com.example.distrobackend.controller;

import com.example.distrobackend.dto.NotificationResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return notificationService.list(user.userId());
    }

    @GetMapping("/unread-count")
    public long unreadCount(@AuthenticationPrincipal AuthenticatedUser user) {
        return notificationService.unreadCount(user.userId());
    }

    @PatchMapping("/{id}/read")
    public void markAsRead(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        notificationService.markAsRead(id, user.userId());
    }

    @PatchMapping("/read-all")
    public void markAllAsRead(@AuthenticationPrincipal AuthenticatedUser user) {
        notificationService.markAllAsRead(user.userId());
    }
}
