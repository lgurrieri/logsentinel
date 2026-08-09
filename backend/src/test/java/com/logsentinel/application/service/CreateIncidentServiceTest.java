package com.logsentinel.application.service;

import com.logsentinel.application.ports.in.CreateIncidentUseCase.CreateIncidentCommand;
import com.logsentinel.application.ports.out.IncidentRepository;
import com.logsentinel.domain.model.Incident;
import com.logsentinel.domain.model.IncidentStatus;
import com.logsentinel.domain.model.Urgency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit test for CreateIncidentService.
 * No Spring context needed - pure Mockito.
 */
@ExtendWith(MockitoExtension.class)
class CreateIncidentServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @InjectMocks
    private CreateIncidentService createIncidentService;

    @Test
    @DisplayName("should create incident with OPEN status when valid command is provided")
    void should_create_incident_with_open_status_when_valid_command() {
        // Arrange
        var command = new CreateIncidentCommand("payment-gw", Urgency.CRITICAL, "ERROR: pool exhausted at 2024-01-15T10:30:00Z");
        var savedIncident = new Incident(
                UUID.randomUUID(),
                "payment-gw",
                Urgency.CRITICAL,
                "ERROR: pool exhausted at 2024-01-15T10:30:00Z",
                IncidentStatus.OPEN,
                OffsetDateTime.now()
        );
        given(incidentRepository.save(any(Incident.class))).willReturn(savedIncident);

        // Act
        Incident result = createIncidentService.execute(command);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(result.getSystemName()).isEqualTo("payment-gw");
        assertThat(result.getUrgency()).isEqualTo(Urgency.CRITICAL);
        verify(incidentRepository, times(1)).save(any(Incident.class));
    }

    @Test
    @DisplayName("should pass incident with OPEN status to repository")
    void should_pass_incident_with_open_status_to_repository() {
        // Arrange
        var command = new CreateIncidentCommand("auth-svc", Urgency.HIGH, "FATAL: authentication service unreachable");
        var savedIncident = new Incident(
                UUID.randomUUID(),
                "auth-svc",
                Urgency.HIGH,
                "FATAL: authentication service unreachable",
                IncidentStatus.OPEN,
                OffsetDateTime.now()
        );
        given(incidentRepository.save(any(Incident.class))).willReturn(savedIncident);

        // Act
        createIncidentService.execute(command);

        // Assert - verify the incident passed to the repository has OPEN status
        ArgumentCaptor<Incident> captor = ArgumentCaptor.forClass(Incident.class);
        verify(incidentRepository).save(captor.capture());
        Incident captured = captor.getValue();
        assertThat(captured.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(captured.getSystemName()).isEqualTo("auth-svc");
        assertThat(captured.getUrgency()).isEqualTo(Urgency.HIGH);
        assertThat(captured.getRawLogs()).isEqualTo("FATAL: authentication service unreachable");
    }

    @Test
    @DisplayName("should preserve raw logs verbatim without modification")
    void should_preserve_raw_logs_verbatim() {
        // Arrange - raw log with special characters (O(n) passthrough, no regex processing)
        String rawLog = "ERROR [2024-01-15T10:30:00Z] pool=primary exhausted connections=100/100 latency_p99=45000ms";
        var command = new CreateIncidentCommand("db-proxy", Urgency.MEDIUM, rawLog);
        var savedIncident = new Incident(
                UUID.randomUUID(),
                "db-proxy",
                Urgency.MEDIUM,
                rawLog,
                IncidentStatus.OPEN,
                OffsetDateTime.now()
        );
        given(incidentRepository.save(any(Incident.class))).willReturn(savedIncident);

        // Act
        Incident result = createIncidentService.execute(command);

        // Assert - raw logs are passed through unchanged (linear O(n) — no parsing)
        assertThat(result.getRawLogs()).isEqualTo(rawLog);
    }
}
