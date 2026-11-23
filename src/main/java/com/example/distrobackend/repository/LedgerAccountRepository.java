package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.LedgerAccount;
import com.example.distrobackend.Domain.enums.LedgerAccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {
    @Modifying
    @org.springframework.data.jpa.repository.Query(value = """
            insert into ledger_accounts (id, account_type, organization_id, currency, active, created_at)
            values (gen_random_uuid(), :type, null, :currency, true, current_timestamp)
            on conflict (account_type, currency) where organization_id is null do nothing
            """, nativeQuery = true)
    int insertPlatformAccountIfMissing(@Param("type") String type, @Param("currency") String currency);

    @Modifying
    @org.springframework.data.jpa.repository.Query(value = """
            insert into ledger_accounts (id, account_type, organization_id, currency, active, created_at)
            values (gen_random_uuid(), :type, :organizationId, :currency, true, current_timestamp)
            on conflict (account_type, organization_id, currency) where organization_id is not null do nothing
            """, nativeQuery = true)
    int insertOrganizationAccountIfMissing(@Param("type") String type,
                                           @Param("organizationId") UUID organizationId,
                                           @Param("currency") String currency);

    @Query("select a from LedgerAccount a where a.accountType = :type and a.currency = :currency and a.organization.id = :organizationId")
    Optional<LedgerAccount> findOrganizationAccount(@Param("type") LedgerAccountType type,
                                                     @Param("currency") String currency,
                                                     @Param("organizationId") UUID organizationId);

    @Query("select a from LedgerAccount a where a.accountType = :type and a.currency = :currency and a.organization is null")
    Optional<LedgerAccount> findPlatformAccount(@Param("type") LedgerAccountType type,
                                                @Param("currency") String currency);
}
