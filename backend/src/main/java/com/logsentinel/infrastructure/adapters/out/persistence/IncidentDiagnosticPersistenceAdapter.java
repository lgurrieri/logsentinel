package com.logsentinel.infrastructure.adapters.out.persistence;

import com.logsentinel.application.ports.out.IncidentDiagnosticRepository;
import com.logsentinel.domain.model.IncidentDiagnostic;
import org.springframework.stereotype.Component;

/**
 * Persistence adapter implementing the IncidentDiagnosticRepository port (LOG-US3-DB-02).
 * Maps between the domain model and the JPA entity backing the 'incident_diagnostics'
 * table.
 */
@Component
public class IncidentDiagnosticPersistenceAdapter implements IncidentDiagnosticRepository {

    private final IncidentDiagnosticJpaRepository jpaRepository;

    public IncidentDiagnosticPersistenceAdapter(IncidentDiagnosticJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public IncidentDiagnostic save(IncidentDiagnostic diagnostic) {
        IncidentDiagnosticJpaEntity entity = new IncidentDiagnosticJpaEntity(
                diagnostic.getIncidentId(), diagnostic.getDiagnosticText());
        IncidentDiagnosticJpaEntity saved = jpaRepository.save(entity);
        return saved.toDomain();
    }
}
