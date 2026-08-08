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
 * Integration test for the incidents table schema.
 * Uses Testcontainers (pgvector/pgvector:pg16) to verify
 * CHECK CONSTRAINTS at the database level.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class IncidentsTableIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("should reject insert with invalid urgency value due to CHECK constraint")
    void should_reject_invalid_urgency_value() {
        assertThatThrownBy(() ->
            jdbcTemplate.execute(
                "INSERT INTO incidents (system_name, urgency, raw_logs, status) " +
                "VALUES ('test-system', 'INVALID', 'some log data', 'OPEN')"
            )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("should reject insert with invalid status value due to CHECK constraint")
    void should_reject_invalid_status_value() {
        assertThatThrownBy(() ->
            jdbcTemplate.execute(
                "INSERT INTO incidents (system_name, urgency, raw_logs, status) " +
                "VALUES ('test-system', 'HIGH', 'some log data', 'UNKNOWN')"
            )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("should accept insert with valid urgency and status values")
    void should_accept_valid_urgency_and_status_values() {
        jdbcTemplate.execute(
            "INSERT INTO incidents (system_name, urgency, raw_logs, status) " +
            "VALUES ('payment-gateway', 'CRITICAL', 'ERROR: pool exhausted at 2024-01-15T10:30:00Z', 'OPEN')"
        );

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM incidents WHERE system_name = 'payment-gateway'",
            Integer.class
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("should auto-generate UUID id and created_at timestamp")
    void should_auto_generate_id_and_created_at() {
        jdbcTemplate.execute(
            "INSERT INTO incidents (system_name, urgency, raw_logs, status) " +
            "VALUES ('auth-service', 'LOW', 'WARN: token expiry approaching', 'OPEN')"
        );

        var result = jdbcTemplate.queryForMap(
            "SELECT id, created_at FROM incidents WHERE system_name = 'auth-service'"
        );
        assertThat(result.get("id")).isNotNull();
        assertThat(result.get("created_at")).isNotNull();
    }

    @Test
    @DisplayName("should default status to OPEN when not provided")
    void should_default_status_to_open() {
        jdbcTemplate.execute(
            "INSERT INTO incidents (system_name, urgency, raw_logs) " +
            "VALUES ('monitoring-svc', 'MEDIUM', 'INFO: heartbeat missed for 30s')"
        );

        String status = jdbcTemplate.queryForObject(
            "SELECT status FROM incidents WHERE system_name = 'monitoring-svc'",
            String.class
        );
        assertThat(status).isEqualTo("OPEN");
    }
}
