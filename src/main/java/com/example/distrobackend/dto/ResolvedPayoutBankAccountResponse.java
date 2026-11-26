package com.example.distrobackend.dto;

public record ResolvedPayoutBankAccountResponse(
        String accountName,
        String bankCode,
        String bankName,
        String currency,
        String maskedAccountNumber
) {}
