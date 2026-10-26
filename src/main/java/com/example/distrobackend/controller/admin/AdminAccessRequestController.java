package com.example.distrobackend.controller.admin;

import com.example.distrobackend.Domain.entity.AccessRequestDocument;
import com.example.distrobackend.Domain.enums.AccessRequestDocumentType;
import com.example.distrobackend.Domain.enums.AccessRequestStatus;
import com.example.distrobackend.dto.AccessRequestApprovalResponse;
import com.example.distrobackend.dto.AccessRequestResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.AccessRequestService;
import com.example.distrobackend.service.AccessRequestDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/access-requests")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminAccessRequestController {
    private final AccessRequestService accessRequestService;
    private final AccessRequestDocumentService accessRequestDocumentService;

    @GetMapping
    public List<AccessRequestResponse> list(
            @RequestParam(required = false) AccessRequestStatus status) {
        return accessRequestService.list(status);
    }

    @GetMapping("/{requestId}")
    public AccessRequestResponse getById(@PathVariable UUID requestId) {
        return accessRequestService.getById(requestId);
    }

    @PostMapping("/{requestId}/approve")
    public AccessRequestApprovalResponse approve(
            @PathVariable UUID requestId,
            @AuthenticationPrincipal AuthenticatedUser reviewer) {
        return accessRequestService.approve(requestId, reviewer);
    }

    @PostMapping("/{requestId}/activation/resend")
    public AccessRequestResponse resendActivation(
            @PathVariable UUID requestId,
            @AuthenticationPrincipal AuthenticatedUser reviewer) {
        return accessRequestService.resendActivation(requestId, reviewer);
    }

    @PostMapping("/{requestId}/reject")
    public AccessRequestResponse reject(
            @PathVariable UUID requestId,
            @AuthenticationPrincipal AuthenticatedUser reviewer) {
        return accessRequestService.reject(requestId, reviewer);
    }

    @GetMapping("/{requestId}/documents/{documentType}")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable UUID requestId,
                                                    @PathVariable AccessRequestDocumentType documentType) {
        AccessRequestDocument document = accessRequestDocumentService.findForAdmin(requestId, documentType);
        MediaType mediaType = MediaType.parseMediaType(document.getContentType());
        String filename = documentType == AccessRequestDocumentType.INCORPORATION_CERTIFICATE
                ? "incorporation-certificate" : "tax-certificate";
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(document.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(document.getContent());
    }
}
