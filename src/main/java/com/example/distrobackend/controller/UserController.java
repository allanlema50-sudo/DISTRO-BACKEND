package com.example.distrobackend.controller;




import com.example.distrobackend.dto.UserResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.AuthService;
import com.example.distrobackend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;
    private final UserService userService;

    /** Any authenticated user. */
    @GetMapping
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser me) {
        return authService.getProfile(me.userId());
    }

    @DeleteMapping
    public void deleteMyAccount(@AuthenticationPrincipal AuthenticatedUser me) {
        userService.deleteUserAccount(me.userId());
    }
}
