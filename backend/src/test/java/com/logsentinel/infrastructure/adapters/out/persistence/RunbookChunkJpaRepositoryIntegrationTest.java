package com.logsentinel.infrastructure.adapters.out.persistence;

import com.logsentinel.config.TestcontainersConfiguration;
import com.pgvector.PGvector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link RunbookChunkJpaRepository} against a real Postgres
 * instance (Testcontainers). Verifies that the minimal JPA mapping for the
 * runbook_chunks table (LOG-US2-DB-01) is usable end-to-end: a float[] embedding can
 * be persisted and read back through Hibernate via the pgvector column, with no
 * precision loss. Also verifies the two native queries required by LOG-US2-BE-02:
 * cosine-distance nearest-neighbor search ({@code <=>}) and the Full-Text fallback
 * ({@code tsvector}/{@code ts_rank}) — these queries touch hand-written native SQL,
 * so they are proven against a real PostgreSQL/pgvector instance, never mocked.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RunbookChunkJpaRepositoryIntegrationTest {

    @Autowired
    private RunbookChunkJpaRepository repository;

    @Test
    @DisplayName("should persist and read back a runbook chunk with its embedding vector")
    void should_persist_and_read_back_embedding_vector() {
        float[] embedding = new float[768];
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = i % 2 == 0 ? 0.5f : -0.25f;
        }
        var entity = new RunbookChunkJpaEntity(
                "check disk usage and clear /var/log if above 90%", embedding);

        RunbookChunkJpaEntity saved = repository.save(entity);
        repository.flush();

        Optional<RunbookChunkJpaEntity> found = repository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getContent())
                .isEqualTo("check disk usage and clear /var/log if above 90%");
        assertThat(found.get().getEmbedding()).hasSize(768);
        assertThat(found.get().getEmbedding()).containsExactly(embedding);
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("should auto-generate a UUID id on save")
    void should_auto_generate_id() {
        float[] embedding = new float[768];
        var entity = new RunbookChunkJpaEntity("restart the auth-service on token expiry", embedding);

        RunbookChunkJpaEntity saved = repository.save(entity);

        assertThat(saved.getId()).isNotNull().isInstanceOf(UUID.class);
    }

    @Test
    @DisplayName("should find the nearest runbook chunk first when ordering by cosine distance")
    void should_find_nearest_chunk_by_cosine_distance() {
        // Orthogonal vectors (disjoint dimensions "on") so cosine distance between them
        // is exactly 1 (maximum) - the query vector matches "target" exactly (distance 0),
        // so it must always win the LIMIT 1 spot regardless of any other rows/tests
        // sharing this Testcontainers instance.
        float[] targetEmbedding = firstHalfOnes();
        float[] unrelatedEmbedding = secondHalfOnes();
        repository.save(new RunbookChunkJpaEntity(
                "LOG-US2-BE-02 marker: restart the auth-service pod on token expiry", targetEmbedding));
        repository.save(new RunbookChunkJpaEntity(
                "LOG-US2-BE-02 marker: rotate TLS certificates before they expire", unrelatedEmbedding));
        repository.flush();

        String queryVectorLiteral = new PGvector(targetEmbedding).getValue();
        List<RunbookChunkJpaEntity> results = repository.findNearestByEmbedding(queryVectorLiteral, 1);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getContent())
                .isEqualTo("LOG-US2-BE-02 marker: restart the auth-service pod on token expiry");
    }

    @Test
    @DisplayName("should find runbook chunks by full-text search on content")
    void should_find_chunks_by_full_text_search() {
        String uniqueMarker = "logusbetwobe02fulltextprobe";
        repository.save(new RunbookChunkJpaEntity(
                "restart the payment-gateway pod " + uniqueMarker + " when connection pool is exhausted",
                new float[768]));
        repository.flush();

        List<RunbookChunkJpaEntity> results = repository.findByFullText(uniqueMarker, 1);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getContent()).contains("payment-gateway");
    }

    @Test
    @DisplayName("should map a persisted entity to the domain RunbookChunk")
    void should_map_entity_to_domain() {
        var entity = new RunbookChunkJpaEntity("clear the cache on deploy", new float[768]);

        RunbookChunkJpaEntity saved = repository.save(entity);

        var domain = saved.toDomain();
        assertThat(domain.id()).isEqualTo(saved.getId());
        assertThat(domain.content()).isEqualTo("clear the cache on deploy");
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
