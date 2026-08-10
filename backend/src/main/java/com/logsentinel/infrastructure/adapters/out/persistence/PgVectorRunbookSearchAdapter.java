package com.logsentinel.infrastructure.adapters.out.persistence;

import com.logsentinel.application.ports.out.RunbookSearchPort;
import com.logsentinel.domain.model.RunbookChunk;
import com.pgvector.PGvector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Adapter implementing {@link RunbookSearchPort} with the resilient search strategy
 * required by LOG-US2-BE-02:
 * <ol>
 *   <li>Vectorize the raw incident log with the active {@link EmbeddingModel}
 *       (Ollama by default, OpenAI under the {@code openai} profile) and search
 *       {@code runbook_chunks} by cosine distance (pgvector operator {@code <=>},
 *       backed by the HNSW index).</li>
 *   <li>If — and only if — that embedding call fails (provider timeout/quota), fall
 *       back immediately to {@link FullTextRunbookSearchAdapter} so the search never
 *       comes back empty just because the embedding provider is unavailable.</li>
 * </ol>
 * The {@code try-catch} wraps ONLY the {@link EmbeddingModel} call. A failure in the
 * native SQL query itself is a real database bug and must propagate — it must NOT be
 * silently swallowed by the fallback (documented anti-pattern in
 * {@code .github/skills/rag-pipeline-implementation/SKILL.md}, Paso 3).
 */
@Component
public class PgVectorRunbookSearchAdapter implements RunbookSearchPort {

    private static final Logger log = LoggerFactory.getLogger(PgVectorRunbookSearchAdapter.class);

    private final RunbookChunkJpaRepository repository;
    private final FullTextRunbookSearchAdapter fullTextFallback;
    private final EmbeddingModel embeddingModel;
    private final int topK;

    public PgVectorRunbookSearchAdapter(
            RunbookChunkJpaRepository repository,
            FullTextRunbookSearchAdapter fullTextFallback,
            EmbeddingModel embeddingModel,
            @Value("${logsentinel.rag.top-k:3}") int topK) {
        this.repository = repository;
        this.fullTextFallback = fullTextFallback;
        this.embeddingModel = embeddingModel;
        this.topK = topK;
    }

    @Override
    public List<RunbookChunk> findSimilarRunbooks(String rawLog) {
        float[] embedding;
        try {
            embedding = embeddingModel.embed(rawLog);
        } catch (Exception e) {
            log.error("Embedding model call failed, falling back to full-text search", Map.of(
                    "stage", "embedding",
                    "cause", String.valueOf(e.getMessage())
            ));
            return fullTextFallback.searchByFullText(rawLog, topK);
        }

        String embeddingLiteral = new PGvector(embedding).getValue();
        return repository.findNearestByEmbedding(embeddingLiteral, topK)
                .stream()
                .map(RunbookChunkJpaEntity::toDomain)
                .toList();
    }
}
