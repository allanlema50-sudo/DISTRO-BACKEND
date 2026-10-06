package com.example.distrobackend.controller;

import com.example.distrobackend.dto.AccessRequestCreateRequest;
import com.example.distrobackend.dto.ActivateAccountRequest;
import com.example.distrobackend.dto.MessageResponse;
import com.example.distrobackend.service.AccessRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AccessRequestController {
    private final AccessRequestService service;

    @PostMapping("/access-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse submit(@Valid @RequestBody AccessRequestCreateRequest request) {
        return service.submit(request);
    }

    @PostMapping("/access-requests/activate")
    public MessageResponse activate(@Valid @RequestBody ActivateAccountRequest request) {
        return service.activate(request);
    }
}
