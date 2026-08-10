package com.logsentinel.infrastructure.adapters.out.persistence;

import com.logsentinel.domain.model.RunbookChunk;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Full-Text search fallback over {@code runbook_chunks} (LOG-US2-BE-02), backed by the
 * generated {@code tsvector} column and GIN index added in
 * {@code V4__add_fulltext_search_to_runbook_chunks.sql}.
 * <p>
 * Invoked by {@link PgVectorRunbookSearchAdapter} ONLY when the {@code EmbeddingModel}
 * call fails (timeout/quota) — this class has no knowledge of Spring AI, it only ever
 * runs a traditional relational query.
 */
@Component
public class FullTextRunbookSearchAdapter {

    private final RunbookChunkJpaRepository repository;

    public FullTextRunbookSearchAdapter(RunbookChunkJpaRepository repository) {
        this.repository = repository;
    }

    /**
     * Searches {@code runbook_chunks} by Full-Text match against {@code rawLog},
     * returning at most {@code topK} chunks ranked by {@code ts_rank}.
     */
    public List<RunbookChunk> searchByFullText(String rawLog, int topK) {
        return repository.findByFullText(rawLog, topK)
                .stream()
                .map(RunbookChunkJpaEntity::toDomain)
                .toList();
    }
}
