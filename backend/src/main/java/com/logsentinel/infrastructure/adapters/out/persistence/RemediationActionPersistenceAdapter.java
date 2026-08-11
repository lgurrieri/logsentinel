package com.logsentinel.infrastructure.adapters.out.persistence;

import com.logsentinel.application.ports.out.RemediationActionRepository;
import com.logsentinel.domain.model.RemediationAction;
import com.logsentinel.domain.model.RemediationStatus;
import org.springframework.stereotype.Component;

/**
 * Persistence adapter implementing the RemediationActionRepository port
 * (LOG-US4-BE-02). Maps between domain model and JPA entity.
 */
@Component
public class RemediationActionPersistenceAdapter implements RemediationActionRepository {

    private final RemediationActionJpaRepository jpaRepository;

    public RemediationActionPersistenceAdapter(RemediationActionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public RemediationAction save(RemediationAction action) {
        return toDomain(jpaRepository.save(toJpaEntity(action)));
    }

    @Override
    public RemediationAction update(RemediationAction action) {
        return toDomain(jpaRepository.save(toJpaEntity(action)));
    }

    private RemediationActionJpaEntity toJpaEntity(RemediationAction action) {
        return new RemediationActionJpaEntity(
                action.getId(),
                action.getIncidentId(),
                action.getGeneratedScript(),
                action.getExecutionStatus().name(),
                action.getExecutionLog(),
                action.getExecutedAt(),
                action.getCreatedAt()
        );
    }

    private RemediationAction toDomain(RemediationActionJpaEntity entity) {
        return new RemediationAction(
                entity.getId(),
                entity.getIncidentId(),
                entity.getGeneratedScript(),
                RemediationStatus.valueOf(entity.getExecutionStatus()),
                entity.getExecutionLog(),
                entity.getExecutedAt(),
                entity.getCreatedAt()
        );
    }
}
