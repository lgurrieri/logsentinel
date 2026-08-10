package com.logsentinel.infrastructure.adapters.out.persistence;

import com.logsentinel.config.TestcontainersConfiguration;
import com.logsentinel.domain.model.RunbookChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * End-to-end integration test for {@link PgVectorRunbookSearchAdapter} (LOG-US2-TEST-03),
 * exercising the FULL resilient search strategy — adapter -&gt; repository -&gt; real
 * PostgreSQL (Testcontainers, {@code pgvector/pgvector:pg16}) — with no mocked
 * persistence. Only the {@link EmbeddingModel} is mocked, so this suite never depends
 * on a real running Ollama/OpenAI instance while still proving, against a real
 * database:
 * <ul>
 *     <li>Top K is genuinely read from Spring configuration, not hardcoded. This suite
 *         forces {@code logsentinel.rag.top-k=2} via {@link TestPropertySource} — a
 *         value distinct from the {@code application.yml} default of 3 — and asserts
 *         exactly that many rows come back even though more rows match.</li>
 *     <li>Results are ordered by REAL cosine distance (pgvector {@code <=>} operator,
 *         HNSW index) against known, hand-picked embedding vectors — never by
 *         insertion order or a mocked repository response.</li>
 *     <li>When the {@link EmbeddingModel} call fails, the adapter falls back to
 *         {@link FullTextRunbookSearchAdapter} — a real (non-mocked) Spring bean
 *         hitting the same PostgreSQL instance via the {@code tsvector}/{@code ts_rank}
 *         full-text query — and the result is never empty when matching content
 *         exists, nor does it include unrelated rows.</li>
 * </ul>
 * <p>
 * <b>Gap this ticket closes:</b> {@code PgVectorRunbookSearchAdapterTest} and
 * {@code FullTextRunbookSearchAdapterTest} (LOG-US2-BE-02) are pure Mockito unit tests
 * with a fully mocked {@link RunbookChunkJpaRepository} — they prove the orchestration
 * logic (which branch is taken, Top K forwarded) but never execute the native SQL
 * against a real database through the adapter. {@code RunbookChunkJpaRepositoryIntegrationTest}
 * (LOG-US2-BE-02) proves the native queries work in isolation against Testcontainers,
 * but never through {@link PgVectorRunbookSearchAdapter} itself, and never exercises
 * Top K parametrization end-to-end. This class is deliberately the only place in the
 * suite that wires the real adapter to a real database with only the AI boundary
 * mocked, per the Gherkin of {@code docs/user-stories/us2-busqueda-semantica-automatizada-de-runbooks.md}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "logsentinel.rag.top-k=2")
class PgVectorRunbookSearchAdapterIntegrationTest {

    @Autowired
    private PgVectorRunbookSearchAdapter adapter;

    @Autowired
    private RunbookChunkJpaRepository repository;

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @BeforeEach
    void cleanUp() {
        // Isolation between test methods: this class forces its own Spring context
        // (distinct property set), so the Testcontainers instance is not shared with
        // other integration test classes, but IS shared across the @Test methods below.
        repository.deleteAll();
        repository.flush();
    }

    @Test
    @DisplayName("should return at most the configured Top K chunks, ordered by real cosine distance, never a fixed value")
    void should_return_top_k_ordered_by_real_cosine_distance() {
        given(embeddingModel.embed(anyString())).willReturn(queryVector());

        repository.save(new RunbookChunkJpaEntity(
                "LOG-US2-TEST-03 marker: nearest chunk - restart the auth-service pod", nearestVector()));
        repository.save(new RunbookChunkJpaEntity(
                "LOG-US2-TEST-03 marker: medium chunk - check connection pool metrics", mediumVector()));
        repository.save(new RunbookChunkJpaEntity(
                "LOG-US2-TEST-03 marker: farthest chunk - rotate TLS certificates", farthestVector()));
        repository.flush();

        List<RunbookChunk> results = adapter.findSimilarRunbooks("ERROR auth-service connection refused");

        assertThat(results)
                .as("Top K must come from configuration (forced to 2 here via @TestPropertySource, "
                        + "distinct from the application.yml default of 3) even though 3 rows match")
                .hasSize(2);
        assertThat(results.get(0).content())
                .as("nearest chunk (smallest real cosine distance to the query vector) must rank first")
                .contains("nearest chunk");
        assertThat(results.get(1).content())
                .as("medium chunk must rank second, ahead of the farthest chunk excluded by Top K")
                .contains("medium chunk");
    }

    @Test
    @DisplayName("should fall back to the real full-text search when the embedding model fails, never returning empty or unrelated content")
    void should_fallback_to_real_full_text_search_when_embedding_model_fails() {
        given(embeddingModel.embed(anyString())).willThrow(new RuntimeException("Ollama timeout"));

        String uniqueMarker = "logustest03fulltextfallbackprobe";
        repository.save(new RunbookChunkJpaEntity(
                "restart the notification-service pod " + uniqueMarker + " after connection pool exhaustion",
                nearestVector()));
        repository.save(new RunbookChunkJpaEntity(
                "rotate TLS certificates before they expire", farthestVector()));
        repository.flush();

        List<RunbookChunk> results = adapter.findSimilarRunbooks(uniqueMarker);

        assertThat(results)
                .as("the Full-Text fallback is a real query against Postgres, not a mock — it must "
                        + "never come back empty when matching content exists")
                .hasSize(1);
        assertThat(results.get(0).content())
                .contains(uniqueMarker)
                .as("must not include unrelated rows that do not match the full-text query")
                .doesNotContain("rotate TLS certificates");
    }

    // ---- Known embedding vectors (dimension 768) ----
    // e1 = firstHalfOnes() and e2 = secondHalfOnes() below are orthogonal unit-direction
    // basis vectors (e1 . e2 = 0). For any vector v = a*e1 + b*e2 (a, b >= 0), the cosine
    // similarity to e1 is exactly a / sqrt(a^2 + b^2) - independent of magnitude - which
    // makes the expected cosine distance of each seeded vector fully deterministic and
    // hand-verifiable instead of relying on approximate/opaque math:
    //   queryVector()   = e1              (a=1, b=0)
    //   nearestVector()  = a=1,   b=0.1  -> similarity ~0.995 -> distance ~0.005 (nearest)
    //   mediumVector()   = a=1,   b=1    -> similarity ~0.707 -> distance ~0.293 (medium)
    //   farthestVector() = e2              (a=0, b=1)  -> similarity 0      -> distance 1 (farthest)

    private float[] queryVector() {
        return firstHalfOnes();
    }

    private float[] nearestVector() {
        float[] v = firstHalfOnes();
        for (int i = 384; i < 768; i++) {
            v[i] = 0.1f;
        }
        return v;
    }

    private float[] mediumVector() {
        float[] v = firstHalfOnes();
        for (int i = 384; i < 768; i++) {
            v[i] = 1f;
        }
        return v;
    }

    private float[] farthestVector() {
        return secondHalfOnes();
    }

    private float[] firstHalfOnes() {
        float[] v = new float[768];
        for (int i = 0; i < 384; i++) {
            v[i] = 1f;
        }
        return v;
    }

    private float[] secondHalfOnes() {
        float[] v = new float[768];
        for (int i = 384; i < 768; i++) {
            v[i] = 1f;
        }
        return v;
    }
}
