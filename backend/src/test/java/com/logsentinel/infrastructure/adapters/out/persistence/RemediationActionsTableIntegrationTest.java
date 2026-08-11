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
 * Integration test for the remediation_actions table schema (LOG-US4-BE-02).
 * Uses Testcontainers (pgvector/pgvector:pg16) against a real Postgres instance — NOT
 * an embedded in-memory database — to verify, at the database level:
 * <ul>
 *     <li>the FOREIGN KEY on incident_id rejects orphan remediation actions;</li>
 *     <li>the CHECK constraint on execution_status rejects any value outside
 *         {@code EXECUTING, SUCCESS, FAILED, DRY_RUN};</li>
 *     <li>a valid EXECUTING row (no execution_log/executed_at yet) is accepted,
 *         auto-generating id and created_at — proving Transaction A's shape works;</li>
 *     <li>a one-to-many relationship is allowed: a single incident can have more
 *         than one remediation_actions row (retries), unlike incident_diagnostics.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RemediationActionsTableIntegrationTest {

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
                        "INSERT INTO remediation_actions (incident_id, generated_script, execution_status) " +
                        "VALUES (?, 'echo hello', 'EXECUTING')",
                        nonExistentIncidentId)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("should reject an execution_status value outside the allowed enum due to CHECK constraint")
    void should_reject_invalid_execution_status() {
        UUID incidentId = insertIncident();

        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO remediation_actions (incident_id, generated_script, execution_status) " +
                        "VALUES (?, 'echo hello', 'INVALID')",
                        incidentId)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("should accept a valid EXECUTING insert with null execution_log/executed_at, auto-generating id and created_at")
    void should_accept_valid_executing_insert_with_nullable_closure_fields() {
        UUID incidentId = insertIncident();

        jdbcTemplate.update(
                "INSERT INTO remediation_actions (incident_id, generated_script, execution_status) " +
                "VALUES (?, 'echo hello', 'EXECUTING')",
                incidentId);

        var result = jdbcTemplate.queryForMap(
                "SELECT id, created_at, execution_log, executed_at, execution_status " +
                "FROM remediation_actions WHERE incident_id = ?",
                incidentId);
        assertThat(result.get("id")).isNotNull();
        assertThat(result.get("created_at")).isNotNull();
        assertThat(result.get("execution_log")).isNull();
        assertThat(result.get("executed_at")).isNull();
        assertThat(result.get("execution_status")).isEqualTo("EXECUTING");
    }

    @Test
    @DisplayName("should allow more than one remediation action for the same incident (one-to-many, retries)")
    void should_allow_multiple_remediation_actions_for_same_incident() {
        UUID incidentId = insertIncident();

        jdbcTemplate.update(
                "INSERT INTO remediation_actions (incident_id, generated_script, execution_status) " +
                "VALUES (?, 'echo first-attempt', 'FAILED')",
                incidentId);
        jdbcTemplate.update(
                "INSERT INTO remediation_actions (incident_id, generated_script, execution_status) " +
                "VALUES (?, 'echo second-attempt', 'SUCCESS')",
                incidentId);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM remediation_actions WHERE incident_id = ?",
                Integer.class, incidentId);
        assertThat(count).isEqualTo(2);
    }
}
