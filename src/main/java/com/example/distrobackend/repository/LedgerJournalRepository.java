package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.LedgerJournal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface LedgerJournalRepository extends JpaRepository<LedgerJournal, UUID> {
    @EntityGraph(attributePaths = {"entries", "entries.account"})
    Optional<LedgerJournal> findBySourceTypeAndSourceIdAndEventType(String sourceType, UUID sourceId,
                                                                     String eventType);

    @Query("select j.id from LedgerJournal j order by j.createdAt desc, j.id desc")
    Page<UUID> findPageIds(Pageable pageable);

    @EntityGraph(attributePaths = "entries")
    java.util.List<LedgerJournal> findBySourceTypeAndSourceIdOrderByCreatedAtAsc(String sourceType, UUID sourceId);

    @EntityGraph(attributePaths = "entries")
    java.util.List<LedgerJournal> findBySourceTypeAndSourceIdInAndEventTypeIn(
            String sourceType, java.util.Collection<UUID> sourceIds, java.util.Collection<String> eventTypes);

    @EntityGraph(attributePaths = "entries")
    java.util.List<LedgerJournal> findBySourceTypeAndSourceIdIn(
            String sourceType, java.util.Collection<UUID> sourceIds);

    @EntityGraph(attributePaths = {"entries", "entries.account", "entries.account.organization"})
    @Query("select distinct j from LedgerJournal j where j.id in :ids")
    List<LedgerJournal> findDetailedByIdIn(@Param("ids") Collection<UUID> ids);

    default Page<LedgerJournal> findAllDetailed(Pageable pageable) {
        int page = pageable.isPaged() ? Math.max(0, pageable.getPageNumber()) : 0;
        int requestedSize = pageable.isPaged() ? pageable.getPageSize() : 20;
        Pageable bounded = PageRequest.of(page, Math.max(1, Math.min(requestedSize, 100)));
        Page<UUID> ids = findPageIds(bounded);
        if (ids.isEmpty()) return new PageImpl<>(List.of(), bounded, ids.getTotalElements());
        Map<UUID, LedgerJournal> journals = findDetailedByIdIn(ids.getContent()).stream()
                .collect(Collectors.toMap(LedgerJournal::getId, Function.identity()));
        List<LedgerJournal> ordered = ids.getContent().stream().map(journals::get)
                .filter(java.util.Objects::nonNull).toList();
        return new PageImpl<>(ordered, bounded, ids.getTotalElements());
    }

    @EntityGraph(attributePaths = {"entries", "entries.account", "entries.account.organization"})
    @Query("select j from LedgerJournal j where j.id = :id")
    Optional<LedgerJournal> findDetailedById(@Param("id") UUID id);
}
