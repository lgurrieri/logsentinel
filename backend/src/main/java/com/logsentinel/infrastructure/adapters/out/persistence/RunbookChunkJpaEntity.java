package com.logsentinel.infrastructure.adapters.out.persistence;

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
 * Minimal by design: this ticket only covers the schema (pgvector extension, table,
 * HNSW index) and the smallest JPA mapping needed to prove the schema is usable
 * (insert a row with a vector and read it back). The similarity-search repository
 * method (native query with the `<=>` operator, Top K, Full-Text fallback) is scope
 * of LOG-US2-BE-02.
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
