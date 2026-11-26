package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResolvePayoutBankAccountRequest(
        @NotBlank @Pattern(regexp = "[0-9]{1,40}") String accountNumber,
        @NotBlank @Size(max = 30) String bankCode,
        @NotBlank @Pattern(regexp = "KES", flags = Pattern.Flag.CASE_INSENSITIVE) String currency
) {}
