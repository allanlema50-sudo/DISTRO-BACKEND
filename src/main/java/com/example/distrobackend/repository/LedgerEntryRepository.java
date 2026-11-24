package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
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
}
