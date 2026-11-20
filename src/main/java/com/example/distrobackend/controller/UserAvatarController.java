package com.example.distrobackend.controller;

import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserAvatarController {

    private final UserService userService;

    @GetMapping(value = "/{userId}/avatar", produces = {
            MediaType.IMAGE_PNG_VALUE, MediaType.IMAGE_JPEG_VALUE, "image/webp"})
    public ResponseEntity<byte[]> avatar(@AuthenticationPrincipal AuthenticatedUser actor,
                                         @PathVariable UUID userId) {
        UserService.AvatarPayload avatar = userService.avatar(actor, userId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(avatar.contentType()))
                .body(avatar.content());
    }
}
