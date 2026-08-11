package com.logsentinel.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data JPA repository for IncidentDiagnosticJpaEntity (LOG-US3-DB-02).
 */
public interface IncidentDiagnosticJpaRepository extends JpaRepository<IncidentDiagnosticJpaEntity, UUID> {
}
