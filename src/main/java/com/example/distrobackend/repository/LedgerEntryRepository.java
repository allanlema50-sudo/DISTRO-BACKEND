package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    @Query(value = """
            select coalesce(sum(case when e.direction = 'CREDIT' then e.amount else -e.amount end), 0)
            from ledger_entries e
            join ledger_accounts a on a.id = e.account_id
            where a.account_type = 'ORGANIZATION_PAYABLE'
              and a.organization_id = :organizationId
              and a.currency = :currency
            """, nativeQuery = true)
    BigDecimal organizationPayableBalance(@Param("organizationId") UUID organizationId,
                                          @Param("currency") String currency);

    /** Accrued platform fees per organization, read from the posted journals. */
    @Query(value = """
            select a.organization_id as organizationId,
                   o.name as organizationName,
                   e.currency as currency,
                   count(distinct j.id) as feeCount,
                   sum(e.amount) as totalFees
            from ledger_entries e
            join ledger_accounts a on a.id = e.account_id
            join ledger_journals j on j.id = e.journal_id
            join organizations o on o.id = a.organization_id
            where a.account_type = 'PLATFORM_FEE_RECEIVABLE'
              and e.direction = 'DEBIT'
              and j.event_type = 'PLATFORM_FEE_ACCRUED'
              and j.created_at >= :fromInclusive
              and j.created_at < :toExclusive
              and (cast(:currency as varchar) is null or e.currency = cast(:currency as varchar))
            group by a.organization_id, o.name, e.currency
            order by sum(e.amount) desc, o.name asc
            """, nativeQuery = true)
    List<PlatformFeeTotalRow> platformFeeTotals(@Param("fromInclusive") OffsetDateTime fromInclusive,
                                                @Param("toExclusive") OffsetDateTime toExclusive,
                                                @Param("currency") String currency);

    interface PlatformFeeTotalRow {
        UUID getOrganizationId();
        String getOrganizationName();
        String getCurrency();
        Long getFeeCount();
        BigDecimal getTotalFees();
    }
}
