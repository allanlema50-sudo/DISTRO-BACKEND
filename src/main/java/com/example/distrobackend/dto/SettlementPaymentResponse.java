package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.SettlementMethod;
import com.example.distrobackend.Domain.enums.SettlementPaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A payment toward a settlement. The proof's bytes are never part of a response. */
public record SettlementPaymentResponse(
        UUID id,
        SettlementMethod method,
        String payerReference,
        BigDecimal amount,
        SettlementPaymentStatus status,
        UUID proofDocumentId,
        OffsetDateTime submittedAt,
        OffsetDateTime confirmedAt
) {}
