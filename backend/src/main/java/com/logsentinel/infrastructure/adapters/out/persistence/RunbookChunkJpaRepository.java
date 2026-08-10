package com.logsentinel.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Minimal Spring Data JPA repository for {@link RunbookChunkJpaEntity} (LOG-US2-DB-01).
 * <p>
 * Deliberately CRUD-only. The native similarity-search query (cosine distance operator
 * {@code <=>}, Top K parametrizable via {@code logsentinel.rag.top-k}, Full-Text
 * fallback) is out of scope for this ticket — see LOG-US2-BE-02.
 */
public interface RunbookChunkJpaRepository extends JpaRepository<RunbookChunkJpaEntity, UUID> {
}
