package com.logsentinel.domain.model;

import java.util.UUID;

/**
 * Domain value object representing a runbook fragment (chunk) retrieved by the
 * semantic search (LOG-US2-BE-02). Pure domain object — no framework, JPA or
 * Spring AI dependency.
 * <p>
 * Relevance ordering (cosine distance for the vector search, {@code ts_rank} for the
 * Full-Text fallback) is a query-time concern resolved by the persistence adapters —
 * callers receive this list already ordered by relevance.
 */
public record RunbookChunk(UUID id, String content) {
}
