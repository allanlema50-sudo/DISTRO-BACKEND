package com.example.distrobackend.dto;

public record AccessRequestApprovalResponse(
        AccessRequestResponse request,
        String activationLink
) {
}
