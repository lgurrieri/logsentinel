package com.logsentinel.application.service;

import com.logsentinel.config.TestcontainersConfiguration;
import com.logsentinel.domain.model.Incident;
import com.logsentinel.domain.model.IncidentStatus;
import com.logsentinel.domain.model.RemediationAction;
import com.logsentinel.domain.model.RemediationStatus;
import com.logsentinel.domain.model.Urgency;
import com.logsentinel.infrastructure.adapters.out.persistence.IncidentPersistenceAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link RemediationStateMachine} against a real Postgres
 * instance and real Spring transactions (Testcontainers, LOG-US4-BE-02). Unlike
 * {@code RemediationStateMachineTest} (Mockito unit test, which cannot exercise real
 * transaction propagation), this test proves the actual guarantee behind the
 * two-phase, {@code Propagation.REQUIRES_NEW} design: each phase commits
 * independently of whatever transaction its caller happens to be running in.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RemediationStateMachineIntegrationTest {

    @Autowired
    private RemediationStateMachine stateMachine;

    @Autowired
    private IncidentPersistenceAdapter incidentPersistenceAdapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("commitExecuting (Transaction A) survives even if the caller's own outer transaction is rolled back")
    void should_persist_executing_row_despite_outer_transaction_rollback() {
        Incident incident = incidentPersistenceAdapter.save(
                Incident.createNew("state-machine-it-system", Urgency.CRITICAL, "ERROR: disk full"));

        TransactionTemplate outerTransaction = new TransactionTemplate(transactionManager);
        RemediationAction[] committed = new RemediationAction[1];
        outerTransaction.execute(status -> {
            committed[0] = stateMachine.commitExecuting(incident.getId(), "echo hello-sandbox");
            status.setRollbackOnly();
            return null;
        });

        // The outer transaction was rolled back, yet Transaction A (REQUIRES_NEW)
        // must have already committed independently, on its own connection.
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM remediation_actions WHERE id = ?",
                Integer.class, committed[0].getId());
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("commitClosure (Transaction B) resolves the parent incident when the script exits with code zero")
    void should_resolve_incident_when_closure_is_success() {
        Incident incident = incidentPersistenceAdapter.save(
                Incident.createNew("state-machine-it-success-system", Urgency.HIGH, "FATAL: pool exhausted"));
        RemediationAction executing = stateMachine.commitExecuting(incident.getId(), "echo hello-sandbox");

        stateMachine.commitClosure(executing, 0, "hello-sandbox\n", OffsetDateTime.now());

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM incidents WHERE id = ?", String.class, incident.getId());
        assertThat(status).isEqualTo(IncidentStatus.RESOLVED.name());
    }

    @Test
    @DisplayName("commitClosure (Transaction B) does NOT resolve the parent incident when the script exits with a non-zero code")
    void should_not_resolve_incident_when_closure_is_failed() {
        Incident incident = incidentPersistenceAdapter.save(
                Incident.createNew("state-machine-it-failed-system", Urgency.MEDIUM, "WARN: retry limit reached"));
        RemediationAction executing = stateMachine.commitExecuting(incident.getId(), "echo boom");

        stateMachine.commitClosure(executing, 1, "boom\n", OffsetDateTime.now());

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM incidents WHERE id = ?", String.class, incident.getId());
        assertThat(status).isEqualTo(IncidentStatus.OPEN.name());
    }

    @Test
    @DisplayName("commitExecuting throws when the incident does not exist, without persisting any remediation action")
    void should_throw_and_persist_nothing_when_incident_does_not_exist() {
        UUID nonExistentIncidentId = UUID.randomUUID();

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                stateMachine.commitExecuting(nonExistentIncidentId, "echo hello-sandbox")
        ).isInstanceOf(com.logsentinel.domain.exception.IncidentNotFoundException.class);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM remediation_actions WHERE incident_id = ?",
                Integer.class, nonExistentIncidentId);
        assertThat(count).isEqualTo(0);
    }
}
