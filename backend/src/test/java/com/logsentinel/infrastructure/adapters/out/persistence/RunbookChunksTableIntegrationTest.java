package com.logsentinel.infrastructure.adapters.out.persistence;

import com.logsentinel.config.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for the runbook_chunks table schema (LOG-US2-DB-01).
 * Uses Testcontainers (pgvector/pgvector:pg16) — NOT an embedded in-memory database —
 * to verify the pgvector extension, the vector(768) column dimension constraint and
 * the HNSW cosine-distance index at the real database level.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RunbookChunksTableIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("should have the pgvector extension enabled")
    void should_have_vector_extension_enabled() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'",
                Integer.class
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("should reject insert with an embedding vector of the wrong dimension")
    void should_reject_embedding_with_wrong_dimension() {
        assertThatThrownBy(() ->
                jdbcTemplate.execute(
                        "INSERT INTO runbook_chunks (content, embedding) " +
                        "VALUES ('runbook chunk with wrong dimension', '[1,2,3]')"
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("should accept insert with a valid 768-dimension embedding and read it back")
    void should_accept_and_read_back_valid_embedding_vector() {
        String vectorLiteral = "[" + "0.1,".repeat(767) + "0.1]";

        jdbcTemplate.execute(
                "INSERT INTO runbook_chunks (content, embedding) VALUES " +
                "('restart the payment-gateway pod when connection pool is exhausted', '"
                        + vectorLiteral + "'::vector)"
        );

        String content = jdbcTemplate.queryForObject(
                "SELECT content FROM runbook_chunks WHERE content = " +
                "'restart the payment-gateway pod when connection pool is exhausted'",
                String.class
        );
        assertThat(content).isEqualTo("restart the payment-gateway pod when connection pool is exhausted");
    }

    @Test
    @DisplayName("should have an HNSW index using cosine distance on the embedding column")
    void should_have_hnsw_cosine_index_on_embedding() {
        String indexDefinition = jdbcTemplate.queryForObject(
                "SELECT indexdef FROM pg_indexes " +
                "WHERE tablename = 'runbook_chunks' AND indexname = 'idx_runbook_chunks_embedding_hnsw'",
                String.class
        );

        assertThat(indexDefinition)
                .as("index must use the hnsw access method with the vector_cosine_ops operator class")
                .containsIgnoringCase("USING hnsw")
                .contains("vector_cosine_ops");
    }
}
