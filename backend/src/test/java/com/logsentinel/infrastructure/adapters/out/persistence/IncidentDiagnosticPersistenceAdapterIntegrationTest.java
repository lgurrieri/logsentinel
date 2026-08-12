package com.logsentinel.infrastructure.adapters.out.persistence;

import com.logsentinel.config.TestcontainersConfiguration;
import com.logsentinel.domain.model.Incident;
import com.logsentinel.domain.model.IncidentDiagnostic;
import com.logsentinel.domain.model.Urgency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for IncidentDiagnosticPersistenceAdapter against a real Postgres
 * instance (Testcontainers, LOG-US3-DB-02). Verifies that fields generated at the
 * database level (id, created_at) are correctly read back into the returned domain
 * object after save(), and that the one-to-one constraint with incidents is honored
 * end-to-end through the adapter (not just via raw JDBC, see
 * {@code IncidentDiagnosticsTableIntegrationTest}).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class IncidentDiagnosticPersistenceAdapterIntegrationTest {

    @Autowired
    private IncidentPersistenceAdapter incidentPersistenceAdapter;

    @Autowired
    private IncidentDiagnosticPersistenceAdapter incidentDiagnosticPersistenceAdapter;

    @Test
    @DisplayName("should populate id and createdAt on the returned diagnostic after saving")
    void should_populate_id_and_created_at_after_save() {
        Incident incident = incidentPersistenceAdapter.save(
                Incident.createNew("adapter-test-system", Urgency.CRITICAL, "ERROR: pool exhausted"));

        IncidentDiagnostic saved = incidentDiagnosticPersistenceAdapter.save(
                IncidentDiagnostic.createNew(incident.getId(), "Root cause: connection pool exhaustion.", null));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getIncidentId()).isEqualTo(incident.getId());
        assertThat(saved.getDiagnosticText()).isEqualTo("Root cause: connection pool exhaustion.");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("should reject persisting a second diagnostic for the same incident (one-to-one)")
    void should_reject_second_diagnostic_for_same_incident() {
        Incident incident = incidentPersistenceAdapter.save(
                Incident.createNew("adapter-test-duplicate-system", Urgency.HIGH, "FATAL: auth unreachable"));
        incidentDiagnosticPersistenceAdapter.save(
                IncidentDiagnostic.createNew(incident.getId(), "First diagnostic for this incident.", null));

        assertThatThrownBy(() ->
                incidentDiagnosticPersistenceAdapter.save(
                        IncidentDiagnostic.createNew(incident.getId(), "A conflicting second diagnostic.", null))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("should round-trip a non-null suggestedScript through the adapter (LOG-US3-DB-02B)")
    void should_round_trip_suggested_script() {
        Incident incident = incidentPersistenceAdapter.save(
                Incident.createNew("adapter-test-suggested-script", Urgency.CRITICAL, "ERROR: pool exhausted"));

        IncidentDiagnostic saved = incidentDiagnosticPersistenceAdapter.save(
                IncidentDiagnostic.createNew(incident.getId(),
                        "Root cause: connection pool exhaustion.\n```bash\nsystemctl restart payment-gw\n```",
                        "systemctl restart payment-gw"));

        assertThat(saved.getSuggestedScript()).isEqualTo("systemctl restart payment-gw");
    }

    @Test
    @DisplayName("should round-trip a null suggestedScript through the adapter (LOG-US3-DB-02B)")
    void should_round_trip_null_suggested_script() {
        Incident incident = incidentPersistenceAdapter.save(
                Incident.createNew("adapter-test-null-suggested-script", Urgency.LOW, "WARN: minor blip"));

        IncidentDiagnostic saved = incidentDiagnosticPersistenceAdapter.save(
                IncidentDiagnostic.createNew(incident.getId(), "Root cause: minor blip, no remediation needed.", null));

        assertThat(saved.getSuggestedScript()).isNull();
    }

    @Test
    @DisplayName("should find the persisted diagnostic by incidentId (LOG-US4-BE-02 — resolving the remediation script)")
    void should_find_diagnostic_by_incident_id() {
        Incident incident = incidentPersistenceAdapter.save(
                Incident.createNew("adapter-test-find-by-incident-id", Urgency.CRITICAL, "ERROR: pool exhausted"));
        incidentDiagnosticPersistenceAdapter.save(
                IncidentDiagnostic.createNew(incident.getId(),
                        "Root cause: connection pool exhaustion.\n```bash\nsystemctl restart payment-gw\n```",
                        "systemctl restart payment-gw"));

        Optional<IncidentDiagnostic> found = incidentDiagnosticPersistenceAdapter.findByIncidentId(incident.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getIncidentId()).isEqualTo(incident.getId());
        assertThat(found.get().getSuggestedScript()).isEqualTo("systemctl restart payment-gw");
    }

    @Test
    @DisplayName("should return empty when no diagnostic was ever persisted for the incident (LOG-US4-BE-02)")
    void should_return_empty_when_no_diagnostic_persisted() {
        Optional<IncidentDiagnostic> found = incidentDiagnosticPersistenceAdapter.findByIncidentId(UUID.randomUUID());

        assertThat(found).isEmpty();
    }
}
