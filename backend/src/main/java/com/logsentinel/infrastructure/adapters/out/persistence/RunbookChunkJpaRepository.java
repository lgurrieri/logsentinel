package com.logsentinel.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link RunbookChunkJpaEntity}.
 * <p>
 * Besides the inherited CRUD operations, this repository hosts the two native SQL
 * strategies required by LOG-US2-BE-02, both scoped to the same table
 * (Opción B — ver {@code .claude/state/orchestration/US2.md}): the primary vector
 * similarity search (pgvector cosine distance operator {@code <=>}, backed by the HNSW
 * index) and the Full-Text fallback (PostgreSQL {@code tsvector}/{@code ts_rank},
 * backed by the GIN index added in {@code V4__add_fulltext_search_to_runbook_chunks.sql}).
 */
public interface RunbookChunkJpaRepository extends JpaRepository<RunbookChunkJpaEntity, UUID> {

    /**
     * Finds the {@code topK} runbook chunks nearest (by cosine distance) to the given
     * embedding vector. {@code embeddingLiteral} must be a pgvector text literal
     * (e.g. {@code "[0.1,0.2,...]"}), cast explicitly to {@code vector} so PostgreSQL
     * can resolve the bound parameter's type.
     */
    @Query(value = """
            SELECT id, content, embedding, created_at
            FROM runbook_chunks
            ORDER BY embedding <=> CAST(:embeddingLiteral AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<RunbookChunkJpaEntity> findNearestByEmbedding(
            @Param("embeddingLiteral") String embeddingLiteral, @Param("topK") int topK);

    /**
     * Full-Text fallback (LOG-US2-BE-02): finds the {@code topK} runbook chunks whose
     * generated {@code content_tsv} column matches {@code searchText}, ranked by
     * {@code ts_rank} (most relevant first). Used ONLY when the embedding model call
     * fails — never as the primary search path.
     */
    @Query(value = """
            SELECT id, content, embedding, created_at
            FROM runbook_chunks
            WHERE content_tsv @@ plainto_tsquery('english', :searchText)
            ORDER BY ts_rank(content_tsv, plainto_tsquery('english', :searchText)) DESC
            LIMIT :topK
            """, nativeQuery = true)
    List<RunbookChunkJpaEntity> findByFullText(@Param("searchText") String searchText, @Param("topK") int topK);
}
