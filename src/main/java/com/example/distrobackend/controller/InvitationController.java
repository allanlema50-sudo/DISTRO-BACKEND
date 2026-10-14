package com.example.distrobackend.controller;

import com.example.distrobackend.dto.AcceptInvitationRequest;
import com.example.distrobackend.dto.InvitationPublicResponse;
import com.example.distrobackend.dto.RegisterResponse;
import com.example.distrobackend.service.InvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/invitations/{token}")
@RequiredArgsConstructor
public class InvitationController {
    private final InvitationService invitationService;

    @GetMapping
    public InvitationPublicResponse get(@PathVariable String token) {
        return invitationService.getPublic(token);
    }

    @PostMapping("/accept")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse accept(
            @PathVariable String token,
            @Valid @RequestBody AcceptInvitationRequest request) {
        return invitationService.accept(token, request);
    }
}
