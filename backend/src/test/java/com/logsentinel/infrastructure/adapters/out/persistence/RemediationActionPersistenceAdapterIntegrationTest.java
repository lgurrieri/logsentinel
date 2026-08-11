package com.logsentinel.infrastructure.adapters.out.persistence;

import com.logsentinel.config.TestcontainersConfiguration;
import com.logsentinel.domain.model.Incident;
import com.logsentinel.domain.model.RemediationAction;
import com.logsentinel.domain.model.RemediationStatus;
import com.logsentinel.domain.model.Urgency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for RemediationActionPersistenceAdapter against a real Postgres
 * instance (Testcontainers, LOG-US4-BE-02). Verifies the full save-then-update round
 * trip that backs the two-phase transactional design: {@code save} inserts the
 * {@code EXECUTING} row (Transaction A shape), and {@code update} persists its closure
 * (Transaction B shape) while preserving identity (id) and audit trail (createdAt).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RemediationActionPersistenceAdapterIntegrationTest {

    @Autowired
    private IncidentPersistenceAdapter incidentPersistenceAdapter;

    @Autowired
    private RemediationActionPersistenceAdapter remediationActionPersistenceAdapter;

    @Test
    @DisplayName("should populate id and createdAt on the returned action after saving an EXECUTING row")
    void should_populate_id_and_created_at_after_save() {
        Incident incident = incidentPersistenceAdapter.save(
                Incident.createNew("adapter-test-system", Urgency.CRITICAL, "ERROR: pool exhausted"));

        RemediationAction saved = remediationActionPersistenceAdapter.save(
                RemediationAction.startExecuting(incident.getId(), "echo hello-sandbox"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getIncidentId()).isEqualTo(incident.getId());
        assertThat(saved.getGeneratedScript()).isEqualTo("echo hello-sandbox");
        assertThat(saved.getExecutionStatus()).isEqualTo(RemediationStatus.EXECUTING);
        assertThat(saved.getExecutionLog()).isNull();
        assertThat(saved.getExecutedAt()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("should update status/log/executedAt on closure while preserving id and createdAt")
    void should_update_closure_fields_while_preserving_id_and_created_at() {
        Incident incident = incidentPersistenceAdapter.save(
                Incident.createNew("adapter-test-closure-system", Urgency.HIGH, "FATAL: disk full"));
        RemediationAction executing = remediationActionPersistenceAdapter.save(
                RemediationAction.startExecuting(incident.getId(), "echo hello-sandbox"));

        OffsetDateTime executedAt = OffsetDateTime.now();
        RemediationAction closed = remediationActionPersistenceAdapter.update(
                executing.closeWith(RemediationStatus.SUCCESS, "hello-sandbox\n", executedAt));

        assertThat(closed.getId()).isEqualTo(executing.getId());
        assertThat(closed.getCreatedAt()).isEqualTo(executing.getCreatedAt());
        assertThat(closed.getExecutionStatus()).isEqualTo(RemediationStatus.SUCCESS);
        assertThat(closed.getExecutionLog()).isEqualTo("hello-sandbox\n");
        assertThat(closed.getExecutedAt()).isNotNull();
    }

    @Test
    @DisplayName("should allow more than one remediation action for the same incident (one-to-many, retries)")
    void should_allow_multiple_remediation_actions_for_same_incident() {
        Incident incident = incidentPersistenceAdapter.save(
                Incident.createNew("adapter-test-retry-system", Urgency.MEDIUM, "WARN: retrying connection"));

        RemediationAction first = remediationActionPersistenceAdapter.save(
                RemediationAction.startExecuting(incident.getId(), "echo first-attempt"));
        RemediationAction second = remediationActionPersistenceAdapter.save(
                RemediationAction.startExecuting(incident.getId(), "echo second-attempt"));

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(first.getIncidentId()).isEqualTo(second.getIncidentId());
    }
}
