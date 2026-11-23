package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.LedgerAccount;
import com.example.distrobackend.Domain.enums.LedgerAccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {
    @Query("select a from LedgerAccount a where a.accountType = :type and a.currency = :currency and a.organization.id = :organizationId")
    Optional<LedgerAccount> findOrganizationAccount(@Param("type") LedgerAccountType type,
                                                     @Param("currency") String currency,
                                                     @Param("organizationId") UUID organizationId);

    @Query("select a from LedgerAccount a where a.accountType = :type and a.currency = :currency and a.organization is null")
    Optional<LedgerAccount> findPlatformAccount(@Param("type") LedgerAccountType type,
                                                @Param("currency") String currency);
}
