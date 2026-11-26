package com.example.distrobackend.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreatePayoutBeneficiaryRequest(
        @NotBlank @Size(max = 30) String method,
        @NotBlank @Size(max = 150) String accountName,
        @NotBlank @Pattern(regexp = "KES", flags = Pattern.Flag.CASE_INSENSITIVE) String currency,
        @Size(max = 30) String mobileNumber,
        @Size(max = 40) String accountNumber,
        @Size(max = 30) String bankCode,
        @Size(max = 150) String email,
        @AssertTrue(message = "Confirm the payout destination details before submitting")
        boolean detailsConfirmed
) {}
