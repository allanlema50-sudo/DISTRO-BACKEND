package com.example.distrobackend.controller;

import com.example.distrobackend.Domain.enums.SharedLoadStatus;
import com.example.distrobackend.dto.*;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.SharedLoadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/shared-loads") @RequiredArgsConstructor
public class SharedLoadController {
    private final SharedLoadService service;
    @GetMapping public Page<SharedLoadResponse> list(@AuthenticationPrincipal AuthenticatedUser me,@RequestParam(required=false) SharedLoadStatus status,Pageable pageable) { return service.list(me,status,pageable); }
    @GetMapping("/{sharedLoadId}") public SharedLoadResponse get(@AuthenticationPrincipal AuthenticatedUser me,@PathVariable UUID sharedLoadId) { return service.get(me,sharedLoadId); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public SharedLoadResponse create(@AuthenticationPrincipal AuthenticatedUser me,@Valid @RequestBody SharedLoadRequest request) { return service.create(me,request); }
    @PostMapping("/{sharedLoadId}/review") public SharedLoadResponse review(@AuthenticationPrincipal AuthenticatedUser me,@PathVariable UUID sharedLoadId,@Valid @RequestBody SharedLoadReviewRequest request) { return service.review(me,sharedLoadId,request); }
    @PostMapping("/{sharedLoadId}/confirmations") public SharedLoadResponse confirm(@AuthenticationPrincipal AuthenticatedUser me,@PathVariable UUID sharedLoadId,@Valid @RequestBody SharedLoadConfirmationRequest request) { return service.confirm(me,sharedLoadId,request); }
}
