package com.example.distrobackend.controller;

import com.example.distrobackend.Domain.entity.Notification;
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


    // =========================================================
    // GET MY NOTIFICATIONS
    // =========================================================

    @GetMapping
    public List<NotificationResponse> getNotifications(
            @AuthenticationPrincipal AuthenticatedUser me
    ) {

        return notificationService
                .getForUser(me.userId())
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }


    // =========================================================
    // GET UNREAD COUNT
    // =========================================================

    @GetMapping("/unread-count")
    public long getUnreadCount(
            @AuthenticationPrincipal AuthenticatedUser me
    ) {

        return notificationService
                .getUnreadCount(me.userId());
    }


    // =========================================================
    // MARK ONE AS READ
    // =========================================================

    @PatchMapping("/{id}/read")
    public void markAsRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser me
    ) {

        notificationService.markAsRead(
                id,
                me.userId()
        );
    }


    // =========================================================
    // MARK ALL AS READ
    // =========================================================

    @PatchMapping("/read-all")
    public void markAllAsRead(
            @AuthenticationPrincipal AuthenticatedUser me
    ) {

        notificationService.markAllAsRead(
                me.userId()
        );
    }
}