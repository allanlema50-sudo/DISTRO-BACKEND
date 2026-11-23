package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.LedgerJournal;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record LedgerJournalResponse(
        UUID id,
        String sourceType,
        UUID sourceId,
        String eventType,
        String currency,
        String description,
        boolean simulated,
        OffsetDateTime createdAt,
        List<LedgerEntryResponse> entries
) {
    public static LedgerJournalResponse from(LedgerJournal journal) {
        return new LedgerJournalResponse(journal.getId(), journal.getSourceType(), journal.getSourceId(),
                journal.getEventType(), journal.getCurrency(), journal.getDescription(), journal.isSimulated(),
                journal.getCreatedAt(), journal.getEntries().stream().map(LedgerEntryResponse::from).toList());
    }
}
