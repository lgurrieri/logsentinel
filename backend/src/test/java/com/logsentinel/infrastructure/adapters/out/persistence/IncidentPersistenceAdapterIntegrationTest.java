package com.logsentinel.infrastructure.adapters.out.persistence;

import com.logsentinel.config.TestcontainersConfiguration;
import com.logsentinel.domain.model.Incident;
import com.logsentinel.domain.model.IncidentStatus;
import com.logsentinel.domain.model.Urgency;
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
 * Integration test for IncidentPersistenceAdapter against a real Postgres instance
 * (Testcontainers). Verifies that fields generated at the database level (created_at)
 * are correctly read back into the returned domain object after save().
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class IncidentPersistenceAdapterIntegrationTest {

    @Autowired
    private IncidentPersistenceAdapter incidentPersistenceAdapter;

    @Test
    @DisplayName("should populate createdAt on the returned incident after saving")
    void should_populate_created_at_after_save() {
        // Arrange
        var incident = Incident.createNew("persistence-adapter-test-system", Urgency.CRITICAL,
                "ERROR: pool exhausted at 2024-01-15T10:30:00Z");

        // Act
        Incident saved = incidentPersistenceAdapter.save(incident);

        // Assert
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt())
                .as("created_at must be read back from the DB-generated default after save()")
                .isNotNull();
    }

    @Test
    @DisplayName("should find a previously persisted incident by id (LOG-US3-BE-01)")
    void should_find_incident_by_id_when_it_exists() {
        var incident = Incident.createNew("persistence-adapter-findbyid-test-system", Urgency.HIGH,
                "FATAL: authentication service unreachable");
        Incident saved = incidentPersistenceAdapter.save(incident);

        Optional<Incident> found = incidentPersistenceAdapter.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getSystemName()).isEqualTo("persistence-adapter-findbyid-test-system");
        assertThat(found.get().getStatus()).isEqualTo(IncidentStatus.OPEN);
    }

    @Test
    @DisplayName("should return empty when no incident exists for the given id")
    void should_return_empty_when_incident_does_not_exist() {
        Optional<Incident> found = incidentPersistenceAdapter.findById(UUID.randomUUID());

        assertThat(found).isEmpty();
    }
}
