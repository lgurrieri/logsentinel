package com.logsentinel.infrastructure.adapters.out.persistence;

import com.logsentinel.application.ports.out.IncidentRepository;
import com.logsentinel.domain.model.Incident;
import com.logsentinel.domain.model.IncidentStatus;
import com.logsentinel.domain.model.Urgency;
import org.springframework.stereotype.Component;

/**
 * Persistence adapter implementing the IncidentRepository port.
 * Maps between domain model and JPA entity.
 */
@Component
public class IncidentPersistenceAdapter implements IncidentRepository {

    private final IncidentJpaRepository jpaRepository;

    public IncidentPersistenceAdapter(IncidentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Incident save(Incident incident) {
        IncidentJpaEntity entity = toJpaEntity(incident);
        IncidentJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    private IncidentJpaEntity toJpaEntity(Incident incident) {
        return new IncidentJpaEntity(
                incident.getSystemName(),
                incident.getUrgency().name(),
                incident.getRawLogs(),
                incident.getStatus().name()
        );
    }

    private Incident toDomain(IncidentJpaEntity entity) {
        return new Incident(
                entity.getId(),
                entity.getSystemName(),
                Urgency.valueOf(entity.getUrgency()),
                entity.getRawLogs(),
                IncidentStatus.valueOf(entity.getStatus()),
                entity.getCreatedAt()
        );
    }
}
