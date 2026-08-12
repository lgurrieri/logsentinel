package com.logsentinel.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data JPA repository for RemediationActionJpaEntity (LOG-US4-BE-02).
 */
public interface RemediationActionJpaRepository extends JpaRepository<RemediationActionJpaEntity, UUID> {
}
