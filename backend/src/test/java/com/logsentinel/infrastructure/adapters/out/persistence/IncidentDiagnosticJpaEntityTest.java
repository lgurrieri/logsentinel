package com.logsentinel.infrastructure.adapters.out.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link IncidentDiagnosticJpaEntity#equals(Object)} / {@code hashCode()}
 * (LOG-US3-DB-02). Verifies the project convention (see
 * `.github/skills/verify-clean-arch/SKILL.md`, Check 4): entity equality is based
 * SOLELY on the {@code id} field, for compatibility with Hibernate proxies.
 * Pure JUnit — no Spring context needed.
 */
class IncidentDiagnosticJpaEntityTest {

    @Test
    @DisplayName("should be equal to itself")
    void should_be_equal_to_itself() {
        var entity = new IncidentDiagnosticJpaEntity(UUID.randomUUID(), "Root cause: pool exhaustion.", null);

        assertThat(entity).isEqualTo(entity);
    }

    @Test
    @DisplayName("should not be equal to null")
    void should_not_be_equal_to_null() {
        var entity = new IncidentDiagnosticJpaEntity(UUID.randomUUID(), "Root cause: pool exhaustion.", null);

        assertThat(entity).isNotEqualTo(null);
    }

    @Test
    @DisplayName("should not be equal to an instance of a different type")
    void should_not_be_equal_to_different_type() {
        var entity = new IncidentDiagnosticJpaEntity(UUID.randomUUID(), "Root cause: pool exhaustion.", null);

        assertThat(entity).isNotEqualTo("not an IncidentDiagnosticJpaEntity");
    }

    @Test
    @DisplayName("should not be equal to another transient entity (id not yet assigned)")
    void should_not_be_equal_when_both_ids_are_null() {
        UUID incidentId = UUID.randomUUID();
        var first = new IncidentDiagnosticJpaEntity(incidentId, "Root cause: pool exhaustion.", null);
        var second = new IncidentDiagnosticJpaEntity(incidentId, "Root cause: pool exhaustion.", null);

        // Same content, but neither has a persisted id yet -> never equal (id-based identity)
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("hashCode should be consistent across instances of the same class")
    void hash_code_should_be_consistent_across_instances() {
        var first = new IncidentDiagnosticJpaEntity(UUID.randomUUID(), "Root cause: pool exhaustion.", null);
        var second = new IncidentDiagnosticJpaEntity(UUID.randomUUID(), "A different diagnostic text.", null);

        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    @Test
    @DisplayName("should map to a domain IncidentDiagnostic carrying incidentId and diagnosticText (LOG-US3-DB-02)")
    void should_map_to_domain_incident_diagnostic() {
        UUID incidentId = UUID.randomUUID();
        var entity = new IncidentDiagnosticJpaEntity(incidentId, "Root cause: connection pool exhaustion detected.", null);

        var domain = entity.toDomain();

        assertThat(domain.getIncidentId()).isEqualTo(incidentId);
        assertThat(domain.getDiagnosticText()).isEqualTo("Root cause: connection pool exhaustion detected.");
        assertThat(domain.getId()).isNull(); // transient entity, id not yet assigned by JPA
    }

    @Test
    @DisplayName("should map suggestedScript to the domain IncidentDiagnostic when present (LOG-US3-DB-02B)")
    void should_map_suggested_script_to_domain_incident_diagnostic() {
        UUID incidentId = UUID.randomUUID();
        var entity = new IncidentDiagnosticJpaEntity(
                incidentId, "Root cause: connection pool exhaustion detected.", "systemctl restart payment-gw");

        var domain = entity.toDomain();

        assertThat(domain.getSuggestedScript()).isEqualTo("systemctl restart payment-gw");
    }

    @Test
    @DisplayName("should expose a null suggestedScript when none was derived (LOG-US3-DB-02B)")
    void should_expose_null_suggested_script_when_absent() {
        var entity = new IncidentDiagnosticJpaEntity(UUID.randomUUID(), "Root cause: pool exhaustion.", null);

        assertThat(entity.getSuggestedScript()).isNull();
        assertThat(entity.toDomain().getSuggestedScript()).isNull();
    }
}
