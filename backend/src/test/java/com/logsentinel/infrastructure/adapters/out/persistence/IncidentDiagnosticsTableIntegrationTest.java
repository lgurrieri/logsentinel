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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for the incident_diagnostics table schema (LOG-US3-DB-02).
 * Uses Testcontainers (pgvector/pgvector:pg16) against a real Postgres instance — NOT
 * an embedded in-memory database — to verify the one-to-one relationship with
 * {@code incidents} (UNIQUE + FOREIGN KEY on incident_id) and the NOT NULL constraint
 * on the consolidated diagnostic text, at the database level.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class IncidentDiagnosticsTableIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID insertIncident() {
        UUID incidentId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO incidents (id, system_name, urgency, raw_logs, status) " +
                "VALUES (?, 'payment-gw', 'CRITICAL', 'ERROR: connection pool exhausted', 'OPEN')",
                incidentId);
        return incidentId;
    }

    @Test
    @DisplayName("should reject insert referencing a non-existent incident due to FOREIGN KEY constraint")
    void should_reject_insert_with_nonexistent_incident_id() {
        UUID nonExistentIncidentId = UUID.randomUUID();

        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO incident_diagnostics (incident_id, diagnostic_text) VALUES (?, ?)",
                        nonExistentIncidentId, "Root cause: connection pool exhaustion.")
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("should reject a second diagnostic for the same incident due to UNIQUE constraint (one-to-one)")
    void should_reject_duplicate_incident_id_due_to_unique_constraint() {
        UUID incidentId = insertIncident();
        jdbcTemplate.update(
                "INSERT INTO incident_diagnostics (incident_id, diagnostic_text) VALUES (?, ?)",
                incidentId, "Root cause: connection pool exhaustion.");

        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO incident_diagnostics (incident_id, diagnostic_text) VALUES (?, ?)",
                        incidentId, "A second, conflicting diagnostic for the same incident.")
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("should reject insert with a null diagnostic_text due to NOT NULL constraint")
    void should_reject_null_diagnostic_text() {
        UUID incidentId = insertIncident();

        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO incident_diagnostics (incident_id, diagnostic_text) VALUES (?, ?)",
                        incidentId, (Object) null)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("should accept a valid insert linked one-to-one to an existing incident, auto-generating id and created_at")
    void should_accept_valid_insert_and_auto_generate_id_and_created_at() {
        UUID incidentId = insertIncident();

        jdbcTemplate.update(
                "INSERT INTO incident_diagnostics (incident_id, diagnostic_text) VALUES (?, ?)",
                incidentId, "Root cause: connection pool exhaustion detected.");

        var result = jdbcTemplate.queryForMap(
                "SELECT id, created_at, diagnostic_text FROM incident_diagnostics WHERE incident_id = ?",
                incidentId);
        assertThat(result.get("id")).isNotNull();
        assertThat(result.get("created_at")).isNotNull();
        assertThat(result.get("diagnostic_text")).isEqualTo("Root cause: connection pool exhaustion detected.");
    }

    @Test
    @DisplayName("should accept and round-trip a non-null suggested_script value (LOG-US3-DB-02B)")
    void should_accept_and_round_trip_suggested_script() {
        UUID incidentId = insertIncident();

        jdbcTemplate.update(
                "INSERT INTO incident_diagnostics (incident_id, diagnostic_text, suggested_script) VALUES (?, ?, ?)",
                incidentId, "Root cause: connection pool exhaustion detected.", "systemctl restart payment-gw");

        var result = jdbcTemplate.queryForMap(
                "SELECT suggested_script FROM incident_diagnostics WHERE incident_id = ?",
                incidentId);
        assertThat(result.get("suggested_script")).isEqualTo("systemctl restart payment-gw");
    }

    @Test
    @DisplayName("should accept a null suggested_script — the column is nullable (LOG-US3-DB-02B)")
    void should_accept_null_suggested_script() {
        UUID incidentId = insertIncident();

        jdbcTemplate.update(
                "INSERT INTO incident_diagnostics (incident_id, diagnostic_text, suggested_script) VALUES (?, ?, ?)",
                incidentId, "Root cause: connection pool exhaustion detected.", (Object) null);

        var result = jdbcTemplate.queryForMap(
                "SELECT suggested_script FROM incident_diagnostics WHERE incident_id = ?",
                incidentId);
        assertThat(result.get("suggested_script")).isNull();
    }
}
