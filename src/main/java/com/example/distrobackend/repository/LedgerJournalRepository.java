package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.LedgerJournal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface LedgerJournalRepository extends JpaRepository<LedgerJournal, UUID> {
    Optional<LedgerJournal> findBySourceTypeAndSourceIdAndEventType(String sourceType, UUID sourceId,
                                                                      String eventType);

    @EntityGraph(attributePaths = "entries")
    java.util.List<LedgerJournal> findBySourceTypeAndSourceIdOrderByCreatedAtAsc(String sourceType, UUID sourceId);

    @EntityGraph(attributePaths = "entries")
    java.util.List<LedgerJournal> findBySourceTypeAndSourceIdInAndEventTypeIn(
            String sourceType, java.util.Collection<UUID> sourceIds, java.util.Collection<String> eventTypes);

    @EntityGraph(attributePaths = "entries")
    java.util.List<LedgerJournal> findBySourceTypeAndSourceIdIn(
            String sourceType, java.util.Collection<UUID> sourceIds);

    @EntityGraph(attributePaths = {"entries", "entries.account", "entries.account.organization"})
    @Query("select j from LedgerJournal j order by j.createdAt desc, j.id desc")
    Page<LedgerJournal> findAllDetailed(Pageable pageable);

    @EntityGraph(attributePaths = {"entries", "entries.account", "entries.account.organization"})
    @Query("select j from LedgerJournal j where j.id = :id")
    Optional<LedgerJournal> findDetailedById(@Param("id") UUID id);
}
