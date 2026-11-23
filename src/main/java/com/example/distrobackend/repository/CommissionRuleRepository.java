package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.CommissionRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;

public interface CommissionRuleRepository extends JpaRepository<CommissionRule, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CommissionRule r where r.currency = :currency and r.active = true")
    List<CommissionRule> findActiveForUpdate(@Param("currency") String currency);

    @Query("select r from CommissionRule r where r.currency = :currency and r.active = true")
    List<CommissionRule> findActive(@Param("currency") String currency);

    List<CommissionRule> findAllByOrderByCurrencyAscEffectiveFromDesc();
}
