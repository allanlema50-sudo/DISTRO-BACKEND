package com.example.distrobackend.controller;

import com.example.distrobackend.dto.AccessRequestCreateRequest;
import com.example.distrobackend.dto.AccessRequestResponse;
import com.example.distrobackend.dto.ActivateAccountRequest;
import com.example.distrobackend.dto.RegisterResponse;
import com.example.distrobackend.service.AccessRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/access-requests")
@RequiredArgsConstructor
public class AccessRequestController {
    private final AccessRequestService accessRequestService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccessRequestResponse submit(@Valid @RequestBody AccessRequestCreateRequest request) {
        return accessRequestService.submit(request);
    }

    @PostMapping("/activate")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse activate(@Valid @RequestBody ActivateAccountRequest request) {
        return accessRequestService.activate(request);
    }
}
