package com.example.distrobackend.controller.admin;

import com.example.distrobackend.dto.AccessRequestResponse;
import com.example.distrobackend.dto.MessageResponse;
import com.example.distrobackend.service.AccessRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/access-requests")
@RequiredArgsConstructor
public class AdminAccessRequestController {
    private final AccessRequestService service;

    @GetMapping public List<AccessRequestResponse> list() { return service.list(); }
    @PostMapping("/{id}/approve") public MessageResponse approve(@PathVariable UUID id) { return service.approve(id); }
    @PostMapping("/{id}/reject") public MessageResponse reject(@PathVariable UUID id) { return service.reject(id); }
}
