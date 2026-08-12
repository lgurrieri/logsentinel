package com.logsentinel.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * Spring Data JPA repository for IncidentJpaEntity.
 */
public interface IncidentJpaRepository extends JpaRepository<IncidentJpaEntity, UUID> {

    /**
     * Bulk-updates only the {@code status} column (LOG-US4-BE-02 — Transaction B of
     * {@code RemediationStateMachine}, resolving the incident after a successful
     * remediation). Deliberately a JPQL update instead of load+save, so it can run
     * as a single atomic statement inside the caller's {@code REQUIRES_NEW}
     * transaction.
     */
    @Modifying
    @Query("UPDATE IncidentJpaEntity i SET i.status = :status WHERE i.id = :id")
    void updateStatus(@Param("id") UUID id, @Param("status") String status);
}
