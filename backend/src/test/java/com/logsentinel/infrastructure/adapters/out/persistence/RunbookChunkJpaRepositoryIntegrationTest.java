package com.logsentinel.infrastructure.adapters.out.persistence;

import com.logsentinel.config.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link RunbookChunkJpaRepository} against a real Postgres
 * instance (Testcontainers). Verifies that the minimal JPA mapping for the
 * runbook_chunks table (LOG-US2-DB-01) is usable end-to-end: a float[] embedding can
 * be persisted and read back through Hibernate via the pgvector column, with no
 * precision loss.
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
}
