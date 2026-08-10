package com.logsentinel.infrastructure.adapters.out.persistence;

import com.logsentinel.config.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the Full-Text search fallback schema added by
 * {@code V4__add_fulltext_search_to_runbook_chunks.sql} (LOG-US2-BE-02). Uses
 * Testcontainers (pgvector/pgvector:pg16) — NOT an embedded in-memory database — to
 * verify the generated {@code tsvector} column and its GIN index at the real database
 * level, exactly like {@link RunbookChunksTableIntegrationTest} does for the vector
 * schema of LOG-US2-DB-01.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RunbookChunksFullTextMigrationIntegrationTest {

    private static final String VALID_VECTOR_LITERAL = "[" + "0.1,".repeat(767) + "0.1]";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("should have a generated tsvector column populated from content")
    void should_have_generated_tsvector_column_populated_from_content() {
        String uniqueContent = "logusbetwobe02migrationprobealpha restart payment-gateway pod";
        jdbcTemplate.update(
                "INSERT INTO runbook_chunks (content, embedding) VALUES (?, ?::vector)",
                uniqueContent, VALID_VECTOR_LITERAL
        );

        Integer matches = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM runbook_chunks " +
                "WHERE content = ? AND content_tsv @@ plainto_tsquery('english', 'logusbetwobe02migrationprobealpha')",
                Integer.class, uniqueContent
        );

        assertThat(matches).isEqualTo(1);
    }

    @Test
    @DisplayName("should not match full-text search for unrelated terms")
    void should_not_match_full_text_search_for_unrelated_terms() {
        String uniqueContent = "logusbetwobe02migrationprobebeta rotate TLS certificates before they expire";
        jdbcTemplate.update(
                "INSERT INTO runbook_chunks (content, embedding) VALUES (?, ?::vector)",
                uniqueContent, VALID_VECTOR_LITERAL
        );

        Integer matches = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM runbook_chunks " +
                "WHERE content = ? AND content_tsv @@ plainto_tsquery('english', 'logusbetwobe02migrationprobealpha')",
                Integer.class, uniqueContent
        );

        assertThat(matches).isEqualTo(0);
    }

    @Test
    @DisplayName("should have a GIN index using content_tsv")
    void should_have_gin_index_on_tsvector_column() {
        String indexDefinition = jdbcTemplate.queryForObject(
                "SELECT indexdef FROM pg_indexes " +
                "WHERE tablename = 'runbook_chunks' AND indexname = 'idx_runbook_chunks_content_tsv_gin'",
                String.class
        );

        assertThat(indexDefinition)
                .as("index must use the GIN access method on content_tsv")
                .containsIgnoringCase("USING gin")
                .contains("content_tsv");
    }
}
