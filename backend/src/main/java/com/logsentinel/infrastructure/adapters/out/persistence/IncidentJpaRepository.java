package com.logsentinel.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data JPA repository for IncidentJpaEntity.
 */
public interface IncidentJpaRepository extends JpaRepository<IncidentJpaEntity, UUID> {
}
