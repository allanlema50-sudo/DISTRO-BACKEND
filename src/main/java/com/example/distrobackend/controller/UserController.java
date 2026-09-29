package com.example.distrobackend.controller;




import com.example.distrobackend.dto.UserResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    /** Any authenticated user. */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser me) {
        return authService.getProfile(UUID.randomUUID());
    }
}
