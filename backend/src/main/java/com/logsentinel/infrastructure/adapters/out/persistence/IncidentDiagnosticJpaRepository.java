package com.logsentinel.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for IncidentDiagnosticJpaEntity (LOG-US3-DB-02).
 */
public interface IncidentDiagnosticJpaRepository extends JpaRepository<IncidentDiagnosticJpaEntity, UUID> {

    /**
     * Derived query honoring the one-to-one relationship with {@code incidents}
     * (LOG-US4-BE-02 — resolving {@code suggestedScript} at remediation time).
     */
    Optional<IncidentDiagnosticJpaEntity> findByIncidentId(UUID incidentId);
}
