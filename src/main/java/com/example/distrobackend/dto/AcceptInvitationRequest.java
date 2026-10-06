package com.example.distrobackend.dto;

import com.example.distrobackend.Util.PasswordRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AcceptInvitationRequest(

        @NotBlank
        String phoneNumber,

        @NotBlank
        @Pattern(
                regexp = PasswordRules.PATTERN,
                message = PasswordRules.MESSAGE
        )
        String password

) {
}