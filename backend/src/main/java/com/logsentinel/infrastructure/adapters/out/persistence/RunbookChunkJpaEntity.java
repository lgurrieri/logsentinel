package com.logsentinel.infrastructure.adapters.out.persistence;

import com.logsentinel.domain.model.RunbookChunk;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity mapping to the 'runbook_chunks' table (LOG-US2-DB-01).
 * NOT exposed outside the persistence layer - domain model is used instead.
 * <p>
 * The similarity-search and Full-Text fallback native queries (operator `<=>`, Top K,
 * `tsvector`) are implemented on {@link RunbookChunkJpaRepository} and consumed by
 * {@link PgVectorRunbookSearchAdapter} / {@link FullTextRunbookSearchAdapter}
 * (LOG-US2-BE-02).
 */
@Entity
@Table(name = "runbook_chunks")
public class RunbookChunkJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Type(VectorType.class)
    @Column(name = "embedding", nullable = false, columnDefinition = "vector(768)")
    private float[] embedding;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected RunbookChunkJpaEntity() {
        // Required by JPA
    }

    public RunbookChunkJpaEntity(String content, float[] embedding) {
        this.content = content;
        this.embedding = embedding;
    }

    public UUID getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Maps this JPA entity to the pure domain model exposed through
     * {@link com.logsentinel.application.ports.out.RunbookSearchPort} (LOG-US2-BE-02).
     */
    public RunbookChunk toDomain() {
        return new RunbookChunk(id, content);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RunbookChunkJpaEntity e)) return false;
        return id != null && id.equals(e.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
