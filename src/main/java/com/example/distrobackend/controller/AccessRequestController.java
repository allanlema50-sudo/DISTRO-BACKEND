package com.example.distrobackend.controller;

import com.example.distrobackend.dto.AccessRequestCreateRequest;
import com.example.distrobackend.dto.AccessRequestSubmissionResponse;
import com.example.distrobackend.dto.ActivateAccountRequest;
import com.example.distrobackend.dto.RegisterResponse;
import com.example.distrobackend.service.AccessRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/access-requests")
@RequiredArgsConstructor
public class AccessRequestController {
    private final AccessRequestService accessRequestService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AccessRequestSubmissionResponse submit(
            @Valid @ModelAttribute AccessRequestCreateRequest request,
            @RequestPart("incorporationCertificate") MultipartFile incorporationCertificate,
            @RequestPart("taxCertificate") MultipartFile taxCertificate) {
        return accessRequestService.submit(request, incorporationCertificate, taxCertificate);
    }

    @PostMapping("/activate")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse activate(@Valid @RequestBody ActivateAccountRequest request) {
        return accessRequestService.activate(request);
    }
}
